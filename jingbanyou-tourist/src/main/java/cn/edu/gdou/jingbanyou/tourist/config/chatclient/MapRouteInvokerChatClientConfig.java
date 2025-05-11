package cn.edu.gdou.jingbanyou.tourist.config.chatclient;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 地图路线 API 调用节点 ChatClient 配置
 * 职责：通过高德地图 MCP 工具获取多条路线数据
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jingbanyou.ai.map-route-invoker")
public class MapRouteInvokerChatClientConfig {

    private ModelConfig model;

    @Bean("mapRouteInvokerChatClient")
    public ChatClient chatClient(ChatClient.Builder builder,
                                 MessageChatMemoryAdvisor chatMemoryAdvisor,
                                 ToolCallbackProvider toolCallbackProvider) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(model.getName())
                .withTemperature(model.getTemperature())
                .withTopP(model.getTopP())
                .withMaxToken(model.getMaxTokens())
                .build();

        return builder
                .defaultAdvisors(chatMemoryAdvisor)
                .defaultToolCallbacks(toolCallbackProvider)
                .defaultOptions(options)
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
