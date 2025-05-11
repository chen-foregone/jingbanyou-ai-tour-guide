package cn.edu.gdou.jingbanyou.tourist.config.chatclient;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 混合检索流式 ChatClient 配置
 *
 * <p>注入 MessageChatMemoryAdvisor，流式调用时自动注入对话历史并回写。
 *
 * @author jingbanyou
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jingbanyou.ai.hybrid-retrieval")
public class HybridRetrievalStreamingChatClientConfig {

    private String prompt;
    private ModelConfig model;

    @Bean("hybridRetrievalStreamingChatClient")
    public ChatClient streamingChatClient(ChatClient.Builder builder,
                                          MessageChatMemoryAdvisor chatMemoryAdvisor) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(model.getName())
                .withTemperature(model.getTemperature())
                .withTopP(model.getTopP())
                .withMaxToken(model.getMaxTokens())
                .build();
        return builder
                .defaultSystem(prompt)
                .defaultOptions(options)
                .defaultAdvisors(chatMemoryAdvisor)
                .build();
    }

    @Data
    public static class ModelConfig {
        private String name;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
    }
}
