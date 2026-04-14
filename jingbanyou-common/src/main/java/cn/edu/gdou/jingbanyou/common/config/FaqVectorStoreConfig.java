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

/**
 * FAQ Redis 向量检索配置
 * 使用独立索引 faq-index
 * 供 manage（写入）和 tourist（读取）共用
 */
@Slf4j
@Configuration
public class FaqVectorStoreConfig {

    @Value("${redis.vectorstore.host:localhost}")
    private String redisHost;

    @Value("${redis.vectorstore.port:6379}")
    private int redisPort;

    @Bean
    public VectorStore faqVectorStore(EmbeddingModel embeddingModel) {
        log.info("[FaqVectorStore] 连接 Redis host={}, port={}", redisHost, redisPort);
        try {
            JedisPooled test = new JedisPooled(redisHost, redisPort);
            String pong = test.ping();
            log.info("[FaqVectorStore] ping={}", pong);
            // 测试索引是否存在
            try {
                test.ftInfo("faq-index");
                log.info("[FaqVectorStore] faq-index 存在");
            } catch (Exception e) {
                log.warn("[FaqVectorStore] faq-index 不存在: {}", e.getMessage());
            }
            test.close();
        } catch (Exception e) {
            log.error("[FaqVectorStore] Redis 连接失败: {}", e.getMessage(), e);
        }
        return RedisVectorStore.builder(new JedisPooled(redisHost, redisPort), embeddingModel)
                .indexName("faq-index")
                .prefix("faq:")
                .initializeSchema(false)
                .metadataFields(
                        MetadataField.tag("scenicId"),
                        MetadataField.tag("faqId")
                )
                .build();
    }
}
