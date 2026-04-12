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

import java.util.List;

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

    @Value("${redis.vectorstore.port:6380}")
    private int redisPort;

    @Bean
    public VectorStore knowledgeVectorStore(EmbeddingModel embeddingModel) {
        JedisPooled jedis = new JedisPooled(redisHost, redisPort);
        tryCreateIndex(jedis, "doc-index", "doc:", "content");
        return RedisVectorStore.builder(jedis, embeddingModel)
                .indexName("doc-index")
                .prefix("doc:")
                .initializeSchema(false)
                .build();
    }

    private void tryCreateIndex(JedisPooled jedis, String indexName, String prefix, String... textFields) {
        try {
            jedis.ftInfo(indexName);
            log.info("索引 {} 已存在，跳过创建", indexName);
        } catch (Exception e) {
            try {
                TextField[] fields = new TextField[textFields.length];
                for (int i = 0; i < textFields.length; i++) {
                    fields[i] = TextField.of(textFields[i]);
                }
                FTCreateParams params = FTCreateParams.createParams()
                        .on(IndexDataType.HASH)
                        .addPrefix(prefix);
                jedis.ftCreate(indexName, params, List.of(fields));
                log.info("索引 {} 创建成功", indexName);
            } catch (Exception createEx) {
                if (createEx.getMessage() != null && createEx.getMessage().contains("ALREADY")) {
                    log.info("索引 {} 已存在，跳过创建", indexName);
                } else {
                    log.warn("创建索引 {} 失败: {}", indexName, createEx.getMessage());
                }
            }
        }
    }
}
