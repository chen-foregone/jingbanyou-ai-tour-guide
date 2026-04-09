package cn.edu.gdou.jingbanyou.tourist.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

/**
 * 用户画像向量库配置
 * 使用独立的 Redis Stack 实例（端口 6380）存储游客画像向量
 */
@Configuration
public class ProfileVectorStoreConfig {

    @Value("${redis.vectorstore.host:localhost}")
    private String redisHost;

    @Value("${redis.vectorstore.port:6380}")
    private int redisPort;

    @Bean
    public VectorStore profileVectorStore(EmbeddingModel embeddingModel) {
        JedisPooled jedis = new JedisPooled(redisHost, redisPort);
        return RedisVectorStore.builder(jedis, embeddingModel)
                .indexName("profile-index")
                .prefix("profile:")
                .initializeSchema(true)
                .build();
    }
}
