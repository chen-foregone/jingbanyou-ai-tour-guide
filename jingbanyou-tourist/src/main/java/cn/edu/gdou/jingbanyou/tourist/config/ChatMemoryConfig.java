package cn.edu.gdou.jingbanyou.tourist.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 对话记忆 Redis 缓存配置
 * <p>
 * 双层存储架构：
 * - Redis（热缓存）：ChatMemory 存储最近 N 轮对话，TTL 24h，供实时推理使用
 * - MySQL（冷存储）：对话结束后通过 ChatMemoryService 持久化到 VisitorInteraction 表
 * <p>
 * ChatMemory key 格式：chat:memory:{sessionId}
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * 对话记忆在 Redis 中的 Key 前缀
     */
    public static final String REDIS_KEY_PREFIX = "chat:memory:";

    /**
     * RedisTemplate，用于 ChatMemoryService 操作消息序列化
     */
    @Bean
    public RedisTemplate<String, String> chatMemoryRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    /**
     * Spring AI ChatMemory（Redis 后端，生产环境使用）
     * 保留最近 20 条消息（约 10 轮对话）
     */
    @Bean
    public ChatMemory chatMemory(RedisConnectionFactory connectionFactory) {
        return RedisChatMemory.builder()
                .connectionFactory(connectionFactory)
                .build();
    }

    /**
     * 本地滑动窗口 ChatMemory（备用，不依赖 Redis）
     * 保留最近 10 条消息，用于开发/测试
     */
    @Bean("localChatMemory")
    public ChatMemory localChatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();
    }

    /**
     * 获取指定 session 的 Redis key
     */
    public static String redisKey(String sessionId) {
        return REDIS_KEY_PREFIX + sessionId;
    }
}
