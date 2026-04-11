package cn.edu.gdou.jingbanyou.tourist.config;

import com.alibaba.cloud.ai.memory.redis.RedisChatMemoryRepository;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆配置（基于 Spring AI Alibaba RedisChatMemoryRepository + Redis Stack）
 *
 * <p>使用 spring-ai-alibaba-starter-memory-redis 提供的 RedisChatMemoryRepository
 * <p>手动创建 Bean 指向 Redis Stack（6380），避免与自动配置冲突
 * <p>Redis 连接参数：host=localhost, port=6380（前缀=chat:memory:）
 */
@Configuration
public class ChatMemoryConfig {

    @Value("${redis.vectorstore.host:localhost}")
    private String redisHost;

    @Value("${redis.vectorstore.port:6380}")
    private int redisPort;

    @Value("${chat.memory.ttl-hours:24}")
    private int ttlHours;

    /**
     * RedisChatMemoryRepository：官方实现，连接 Redis Stack（6380）
     * 手动创建以覆盖自动配置的默认 Redis（6379）连接
     */
    @Bean
    public RedisChatMemoryRepository redisChatMemoryRepository() {
        return RedisChatMemoryRepository.builder()
                .host(redisHost)
                .port(redisPort)
                .keyPrefix("chat:memory:")
                .ttl(java.time.Duration.ofHours(ttlHours))
                .build();
    }

    /**
     * MessageWindowChatMemory：限制上下文窗口大小（默认保留最近 20 条消息）
     */
    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository redisChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    /**
     * MessageChatMemoryAdvisor：自动管理对话历史的 Advisor
     * 调用时通过 .advisors(ctx -> ctx.param(ChatMemory.CONVERSATION_ID, sessionId)) 指定会话
     */
    @Bean
    public MessageChatMemoryAdvisor chatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}
