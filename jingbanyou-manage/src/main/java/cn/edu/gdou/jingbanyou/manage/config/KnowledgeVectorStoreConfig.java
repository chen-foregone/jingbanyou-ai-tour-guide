package cn.edu.gdou.jingbanyou.manage.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

/**
 * 景区知识库 Redis 向量检索配置
 * 与 FAQ 向量库分离，使用独立索引 doc-index
 *
 * @author jingbanyou
 */
@Configuration
public class KnowledgeVectorStoreConfig {

    @Value("${redis.vectorstore.host:localhost}")
    private String redisHost;

    @Value("${redis.vectorstore.port:6380}")
    private int redisPort;

    @Bean
    public VectorStore knowledgeVectorStore(EmbeddingModel embeddingModel) {
        JedisPooled jedis = new JedisPooled(redisHost, redisPort);
        return RedisVectorStore.builder(jedis, embeddingModel)
                .indexName("doc-index")
                .prefix("doc:")
                .initializeSchema(true)
                .build();
    }
}
