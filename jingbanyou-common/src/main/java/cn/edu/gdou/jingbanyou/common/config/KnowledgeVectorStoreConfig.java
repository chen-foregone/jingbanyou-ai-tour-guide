package cn.edu.gdou.jingbanyou.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.schemafields.TextField;

/**
 * 景区知识库 Redis 向量检索配置
 * 与 FAQ 向量库分离，使用独立索引 doc-index
 * 供 manage（写入）和 tourist（读取）共用
 */
@Slf4j
@Configuration
public class KnowledgeVectorStoreConfig {

    @Value("${redis.vectorstore.host:localhost}")
    private String redisHost;

    @Value("${redis.vectorstore.port:6379}")
    private int redisPort;

    @Bean
    public VectorStore knowledgeVectorStore(EmbeddingModel embeddingModel) {
        JedisPooled jedis = new JedisPooled(redisHost, redisPort);
        createIndexIfNotExists(jedis);
        return RedisVectorStore.builder(jedis, embeddingModel)
                .indexName("doc-index")
                .prefix("doc:")
                .initializeSchema(false)
                .build();
    }

    private void createIndexIfNotExists(JedisPooled jedis) {
        try {
            jedis.ftInfo("doc-index");
            log.info("doc-index 索引已存在");
        } catch (Exception e) {
            log.info("doc-index 索引不存在，创建中...");
            try {
                jedis.ftCreate("doc-index", FTCreateParams.createParams()
                        .on(IndexDataType.JSON)
                        .addPrefix("doc:"),
                        TextField.of("content"),
                        TextField.of("scenicId"),
                        TextField.of("docId"),
                        TextField.of("docTitle"),
                        TextField.of("chunkIndex"),
                        TextField.of("embedding"),
                        TextField.of("parent_document_id"),
                        TextField.of("total_chunks"),
                        TextField.of("chunk_index"));
                log.info("doc-index 索引创建成功");
            } catch (Exception createEx) {
                if (createEx.getMessage() != null && createEx.getMessage().contains("ALREADY")) {
                    log.info("doc-index 索引已存在");
                } else {
                    log.warn("创建 doc-index 索引失败: {}", createEx.getMessage());
                }
            }
        }
    }
}
