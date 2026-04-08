package cn.edu.gdou.jingbanyou.tourist.config.chatclient;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 意图分类节点 ChatClient 配置
 * 从 application.yml 中读取 jingbanyou.ai.distinguish 配置
 */
@Configuration
@ConfigurationProperties(prefix = "jingbanyou.ai.distinguish")
public class DistinguishChatClientConfig {

    private String prompt;
    private ModelConfig model;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public ModelConfig getModel() {
        return model;
    }

    public void setModel(ModelConfig model) {
        this.model = model;
    }

    @Bean("distinguishChatClient")
    public ChatClient chatClient(ChatClient.Builder builder) {
        // 验证配置是否加载
        if (model == null) {
            throw new IllegalStateException(
                "DistinguishChatClient 配置未正确加载！请检查 application.yml 中的 jingbanyou.ai.distinguish 配置"
            );
        }
        
        DashScopeChatOptions options;
        if ("JSON_OBJECT".equals(model.getResponseFormat())) {
            options = DashScopeChatOptions.builder()
                    .withModel(model.getName())
                    .withTemperature(model.getTemperature())
                    .withTopP(model.getTopP())
                    .withMaxToken(model.getMaxTokens())
                    .withResponseFormat(
                            DashScopeResponseFormat.builder()
                                    .type(DashScopeResponseFormat.Type.JSON_OBJECT)
                                    .build()
                    )
                    .build();
        } else {
            options = DashScopeChatOptions.builder()
                    .withModel(model.getName())
                    .withTemperature(model.getTemperature())
                    .withTopP(model.getTopP())
                    .withMaxToken(model.getMaxTokens())
                    .build();
        }
        return builder.defaultSystem(prompt).defaultOptions(options).build();
    }

    @Data
    public static class ModelConfig {
        private String name;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private String responseFormat;
    }
}
