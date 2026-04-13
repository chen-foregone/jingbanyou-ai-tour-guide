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
 * <p>两个独立 ChatClient：
 * - textDistinguishChatClient：纯文本输入（使用 yml prompt）
 * - multimodalDistinguishChatClient：多模态输入（音频 + 文字，使用 multimodalPrompt）
 */
@Configuration
@ConfigurationProperties(prefix = "jingbanyou.ai.distinguish")
public class DistinguishChatClientConfig {

    /**
     * 纯文本场景的 system prompt（来自 distinguish.yml）
     * 注意：不注入对话历史，避免 AI 问候语干扰分类结果
     */
    private String prompt;

    /**
     * 多模态场景的 system prompt（音频 + 文字，prompt 简短：只描述任务）
     * 注意：不注入对话历史
     */
    private String multimodalPrompt;

    private ModelConfig model;

    // ===== 纯文本 ChatClient =====

    @Bean("textDistinguishChatClient")
    public ChatClient textDistinguishChatClient(ChatClient.Builder builder) {
        validateModel();
        DashScopeChatOptions options = buildOptions();
        return builder
                .defaultSystem(prompt)
                .defaultOptions(options)
                .build();
    }

    // ===== 多模态 ChatClient =====

    @Bean("multimodalDistinguishChatClient")
    public ChatClient multimodalDistinguishChatClient(ChatClient.Builder builder) {
        validateModel();
        DashScopeChatOptions options = buildOptions();
        return builder
                .defaultSystem(multimodalPrompt)
                .defaultOptions(options)
                .build();
    }

    private void validateModel() {
        if (model == null) {
            throw new IllegalStateException(
                "DistinguishClient 配置未加载！请检查 jingbanyou.ai.distinguish 配置"
            );
        }
    }

    private DashScopeChatOptions buildOptions() {
        if ("JSON_OBJECT".equals(model.getResponseFormat())) {
            return DashScopeChatOptions.builder()
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
        }
        return DashScopeChatOptions.builder()
                .withModel(model.getName())
                .withTemperature(model.getTemperature())
                .withTopP(model.getTopP())
                .withMaxToken(model.getMaxTokens())
                .build();
    }

    // ===== getters/setters =====

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getMultimodalPrompt() { return multimodalPrompt; }
    public void setMultimodalPrompt(String multimodalPrompt) { this.multimodalPrompt = multimodalPrompt; }

    public ModelConfig getModel() { return model; }
    public void setModel(ModelConfig model) { this.model = model; }

    @Data
    public static class ModelConfig {
        private String name;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private String responseFormat;
    }
}
