package cn.edu.gdou.jingbanyou.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数字人服务配置类
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Slf4j
@Configuration
public class DigitalHumanConfig {

    /**
     * 数字人对话服务 Bean
     * 整合语音识别、大模型对话、语音合成
     */
    @Bean
    public DigitalHumanService digitalHumanService(
            ChatClient chatClient,
            EmbeddingModel embeddingModel,
            VectorStore vectorStore) {
        log.info("初始化数字人服务...");
        return new DigitalHumanService(chatClient, embeddingModel, vectorStore);
    }

}
