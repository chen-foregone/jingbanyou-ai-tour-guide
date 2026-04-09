package cn.edu.gdou.jingbanyou.tourist.config.chatclient;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 路线润色节点 ChatClient 配置（阶段二）
 * 职责：结合用户画像，对多条原始路线进行润色、筛选、排序
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jingbanyou.ai.route-polish")
public class RoutePolishChatClientConfig {

    private String prompt;
    private ModelConfig model;

    @Bean("routePolishChatClient")
    public org.springframework.ai.chat.client.ChatClient chatClient(
            org.springframework.ai.chat.client.ChatClient.Builder builder) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(model.getName())
                .withTemperature(model.getTemperature())
                .withTopP(model.getTopP())
                .withMaxToken(model.getMaxTokens())
                .build();
        return builder.defaultSystem(prompt).defaultOptions(options).build();
    }

    @Data
    public static class ModelConfig {
        private String name;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
    }
}
