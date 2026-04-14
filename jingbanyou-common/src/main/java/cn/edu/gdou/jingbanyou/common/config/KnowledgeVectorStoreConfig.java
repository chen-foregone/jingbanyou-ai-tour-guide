package cn.edu.gdou.jingbanyou.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

import java.util.Map;

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
        log.info("[KnowledgeVectorStore] 连接 Redis host={}, port={}", redisHost, redisPort);
        JedisPooled jedis = new JedisPooled(redisHost, redisPort);
        try {
            String pong = jedis.ping();
            log.info("[KnowledgeVectorStore] ping={}", pong);
            // 测试索引
            try {
                Map<String, Object> info = jedis.ftInfo("doc-index");
                log.info("[KnowledgeVectorStore] doc-index 存在, vector_index_sz_mb={}", info.get("vector_index_sz_mb"));
            } catch (Exception e) {
                log.warn("[KnowledgeVectorStore] doc-index 查询失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("[KnowledgeVectorStore] Redis 连接失败: {}", e.getMessage(), e);
        }

        return RedisVectorStore.builder(jedis, embeddingModel)
                .indexName("doc-index")
                .prefix("doc:")
                .initializeSchema(false)
                .metadataFields(
                        MetadataField.numeric("scenicId"),
                        MetadataField.numeric("docId"),
                        MetadataField.text("docTitle"),
                        MetadataField.numeric("chunkIndex")
                )
                .build();
    }
}
