package cn.edu.gdou.jingbanyou.tourist.config.chatclient;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * 路线参数提取节点 ChatClient 配置
 */
@Data
@Configuration
@PropertySource(value = "classpath:chatclient/route-param-extractor.yml", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "jingbanyou.ai.route-param-extractor")
public class RouteParamExtractorChatClientConfig {

    private String prompt;
    private ModelConfig model;

    @Bean("routeParamExtractorChatClient")
    public ChatClient chatClient(ChatClient.Builder builder) {
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
