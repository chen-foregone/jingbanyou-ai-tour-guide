package cn.edu.gdou.jingbanyou.tourist.config.chatclient;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
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
        // MCP 工具已在 application.yml 中全局启用 (spring.ai.dashscope.mcp.client.toolcallback.enabled: true)
        // 此处不需额外注册工具，LLM 会根据 system prompt 自主调用 amap MCP 工具
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
