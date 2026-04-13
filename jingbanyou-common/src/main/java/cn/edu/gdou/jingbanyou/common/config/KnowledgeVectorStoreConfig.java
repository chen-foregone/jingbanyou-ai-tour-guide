package cn.edu.gdou.jingbanyou.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.json.Path;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 景区知识库 Redis 向量检索配置
 * 与 FAQ 向量库分离，使用独立索引 doc-index
 * 供 manage（写入）和 tourist（读取）共用
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class KnowledgeVectorStoreConfig {

    @Value("${redis.vectorstore.host:localhost}")
    private String redisHost;

    @Value("${redis.vectorstore.port:6379}")
    private int redisPort;

    private final EmbeddingModel embeddingModel;

    @Bean
    public VectorStore knowledgeVectorStore() {
        JedisPooled jedis = new JedisPooled(redisHost, redisPort);
        createIndexIfNotExists(jedis);

        VectorStore store = RedisVectorStore.builder(jedis, embeddingModel)
                .indexName("doc-index")
                .prefix("doc:")
                .initializeSchema(false)
                .build();

        // 索引重建后，自动重新添加已有文档
        reindexExistingDocuments(jedis, store);

        return store;
    }

    private void createIndexIfNotExists(JedisPooled jedis) {
        try {
            jedis.ftInfo("doc-index");
            log.info("doc-index 索引已存在，检测是否包含向量字段...");
            boolean hasVectorField = checkVectorFieldExists(jedis);
            if (!hasVectorField) {
                log.warn("doc-index 缺少向量字段 embedding，重新创建索引...");
                jedis.ftDropIndex("doc-index");
                log.info("已删除旧的 doc-index 索引");
                createIndex(jedis);
            } else {
                log.info("doc-index 索引正常");
            }
        } catch (Exception e) {
            log.info("doc-index 索引不存在，创建中...");
            createIndex(jedis);
        }
    }

    private void createIndex(JedisPooled jedis) {
        try {
            SchemaField vectorField = VectorField.builder()
                    .fieldName("embedding")
                    .algorithm(VectorField.VectorAlgorithm.HNSW)
                    .attributes(Map.of(
                            "DIM", 1536,
                            "TYPE", "FLOAT32",
                            "DISTANCE_METRIC", "COSINE",
                            "M", 16,
                            "EF_CONSTRUCTION", 200,
                            "EF_RUNTIME", 10
                    ))
                    .build();

            jedis.ftCreate("doc-index", FTCreateParams.createParams()
                            .on(IndexDataType.JSON)
                            .addPrefix("doc:"),
                    TextField.of("content"),
                    NumericField.of("scenicId"),
                    NumericField.of("docId"),
                    TextField.of("docTitle"),
                    NumericField.of("chunkIndex"),
                    NumericField.of("chunk_index"),
                    TextField.of("parent_document_id"),
                    NumericField.of("total_chunks"),
                    vectorField);
            log.info("doc-index 索引创建成功（包含向量字段）");
        } catch (Exception createEx) {
            if (createEx.getMessage() != null && createEx.getMessage().contains("ALREADY")) {
                log.info("doc-index 索引已存在");
            } else {
                log.warn("创建 doc-index 索引失败: {}", createEx.getMessage());
            }
        }
    }

    private boolean checkVectorFieldExists(JedisPooled jedis) {
        try {
            Map<String, Object> info = jedis.ftInfo("doc-index");
            Object vectorSize = info.get("vector_index_sz_mb");
            if (vectorSize instanceof String) {
                return Double.parseDouble((String) vectorSize) > 0;
            } else if (vectorSize instanceof Number) {
                return ((Number) vectorSize).doubleValue() > 0;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 索引重建后，将 Redis 中已有的 JSON 文档重新添加到向量库
     * 使其被新索引识别并完成向量索引
     */
    private void reindexExistingDocuments(JedisPooled jedis, VectorStore store) {
        try {
            Set<String> keys = jedis.keys("doc:*");
            if (keys == null || keys.isEmpty()) {
                log.info("Redis 中无现有文档，跳过重索引");
                return;
            }
            log.info("发现 {} 个现有文档，开始重索引...", keys.size());
            List<Document> docs = new ArrayList<>();
            for (String key : keys) {
                try {
                    String json = jedis.jsonGetAsPlainString(key, redis.clients.jedis.json.Path.ROOT_PATH);
                    if (json != null && !json.isEmpty()) {
                        Map<String, Object> data = parseJsonToMap(json);
                        if (data != null) {
                            String content = data.get("content") != null ? data.get("content").toString() : "";
                            Document doc = new Document(content, data);
                            docs.add(doc);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("读取文档失败 key={}: {}", key, ex.getMessage());
                }
            }
            if (!docs.isEmpty()) {
                store.add(docs);
                log.info("重索引完成，共处理 {} 个文档", docs.size());
            }
        } catch (Exception e) {
            log.warn("重索引过程出现异常: {}", e.getMessage());
        }
    }

    private Map<String, Object> parseJsonToMap(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
