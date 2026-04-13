package cn.edu.gdou.jingbanyou.tourist.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

/**
 * 用户画像向量库配置
 * 使用独立索引 profile-index
 * <p>索引由 Spring AI 自动创建和管理（initializeSchema=true）
 */
@Slf4j
@Configuration
public class ProfileVectorStoreConfig {

    @Value("${redis.vectorstore.host:localhost}")
    private String redisHost;

    @Value("${redis.vectorstore.port:6379}")
    private int redisPort;

    @Bean
    public VectorStore profileVectorStore(EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(new JedisPooled(redisHost, redisPort), embeddingModel)
                .indexName("profile-index")
                .prefix("profile:")
                .initializeSchema(true)
                .build();
    }
}
