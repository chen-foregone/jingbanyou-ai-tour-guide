package cn.edu.gdou.jingbanyou.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.ChromaVectorStore;
import org.springframework.ai.vectorstore.store.ChromaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 核心配置类
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Slf4j
@Configuration
public class AiConfig {

    /**
     * ChatClient Bean - AI 对话客户端
     * 用于与通义千问等大模型进行交互
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        log.info("初始化 ChatClient...");
        return ChatClient.builder(chatModel).build();
    }

}
