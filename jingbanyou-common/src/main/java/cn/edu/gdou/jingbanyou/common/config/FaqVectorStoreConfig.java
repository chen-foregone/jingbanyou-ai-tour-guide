package cn.edu.gdou.jingbanyou.common.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

/**
 * FAQ Redis 向量检索配置
 * 使用独立索引 faq-index
 * 供 manage（写入）和 tourist（读取）共用
 */
@Configuration
public class FaqVectorStoreConfig {

    @Value("${redis.vectorstore.host:localhost}")
    private String redisHost;

    @Value("${redis.vectorstore.port:6380}")
    private int redisPort;

    @Bean
    public VectorStore faqVectorStore(EmbeddingModel embeddingModel) {
        JedisPooled jedis = new JedisPooled(redisHost, redisPort);
        return RedisVectorStore.builder(jedis, embeddingModel)
                .indexName("faq-index")
                .prefix("faq:")
                .initializeSchema(true)
                .build();
    }
}
