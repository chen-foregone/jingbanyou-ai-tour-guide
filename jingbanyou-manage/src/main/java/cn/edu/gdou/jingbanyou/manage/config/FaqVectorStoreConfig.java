package cn.edu.gdou.jingbanyou.manage.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

/**
 * FAQ Redis 向量检索配置
 * 手动注册 RedisVectorStore Bean（spring-ai-redis-store 不含自动配置）
 *
 * @author jingbanyou
 */
@Configuration
public class FaqVectorStoreConfig
{
    @Value("${redis.vectorstore.host:localhost}")
    private String redisHost;

    @Value("${redis.vectorstore.port:6380}")
    private int redisPort;

    @Bean
    public VectorStore redisVectorStore(EmbeddingModel embeddingModel)
    {
        JedisPooled jedis = new JedisPooled(redisHost, redisPort);
        return RedisVectorStore.builder(jedis, embeddingModel)
                .indexName("faq-index")
                .prefix("faq:")
                .initializeSchema(true)
                .build();
    }
}
