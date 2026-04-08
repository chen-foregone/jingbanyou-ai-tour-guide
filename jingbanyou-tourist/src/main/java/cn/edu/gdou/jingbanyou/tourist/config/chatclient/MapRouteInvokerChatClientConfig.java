package cn.edu.gdou.jingbanyou.tourist.config.chatclient;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * 地图路线 API 调用节点 ChatClient 配置
 */
@Data
@Configuration
@PropertySource(value = "classpath:chatclient/map-route-invoker.yml", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "jingbanyou.ai.map-route-invoker")
public class MapRouteInvokerChatClientConfig {

    private String prompt;
    private ModelConfig model;

    @Bean("mapRouteInvokerChatClient")
    public ChatClient chatClient(ChatClient.Builder builder) {
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
