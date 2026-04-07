package cn.edu.gdou.jingbanyou.tourist.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI ChatClient 配置类
 * 为每个节点创建独立的 ChatClient Bean，统一管理提示词和模型参数
 * 
 * @author JingbanYou Team
 * @date 2026-04-07
 */
@Configuration
public class AiChatClientConfig {
    
    @Autowired
    private ChatClient.Builder chatClientBuilder;
    
    @Autowired
    private AiNodeProperties aiNodeProperties;
    
    /**
     * 创建意图分类节点的 ChatClient
     */
    @Bean("distinguishChatClient")
    public ChatClient distinguishChatClient() {
        return createChatClient("distinguish");
    }
    
    /**
     * 创建路线参数提取节点的 ChatClient
     */
    @Bean("routeParamExtractorChatClient")
    public ChatClient routeParamExtractorChatClient() {
        return createChatClient("route-param-extractor");
    }
    
    /**
     * 创建 FAQ 润色节点的 ChatClient
     */
    @Bean("faqPolishChatClient")
    public ChatClient faqPolishChatClient() {
        return createChatClient("faq-polish");
    }
    
    /**
     * 创建景区知识问答节点的 ChatClient
     */
    @Bean("scenicKnowledgeChatClient")
    public ChatClient scenicKnowledgeChatClient() {
        return createChatClient("scenic-knowledge");
    }
    
    /**
     * 创建闲聊兜底节点的 ChatClient
     */
    @Bean("generalChatChatClient")
    public ChatClient generalChatChatClient() {
        return createChatClient("general-chat");
    }
    
    /**
     * 创建参数缺失引导节点的 ChatClient
     */
    @Bean("missingParamGuideChatClient")
    public ChatClient missingParamGuideChatClient() {
        return createChatClient("missing-param-guide");
    }
    
    /**
     * 创建地图路线 API 调用节点的 ChatClient
     */
    @Bean("mapRouteInvokerChatClient")
    public ChatClient mapRouteInvokerChatClient() {
        return createChatClient("map-route-invoker");
    }
    
    /**
     * 通用方法：根据节点名称创建 ChatClient
     * 
     * @param nodeName 节点名称（与 YAML 配置中的 key 对应）
     * @return ChatClient 实例
     */
    private ChatClient createChatClient(String nodeName) {
        AiNodeProperties.NodeConfig nodeConfig = aiNodeProperties.getNodes().get(nodeName);
        
        if (nodeConfig == null) {
            throw new IllegalArgumentException("未找到节点配置: " + nodeName);
        }
        
        if (nodeConfig.getPrompt() == null || nodeConfig.getModel() == null) {
            throw new IllegalArgumentException("节点配置不完整: " + nodeName);
        }
        
        // 构建模型选项
        DashScopeChatOptions options;
        
        // 如果配置了响应格式，添加 JSON 格式支持
        if ("JSON_OBJECT".equals(nodeConfig.getModel().getResponseFormat())) {
            options = DashScopeChatOptions.builder()
                    .withModel(nodeConfig.getModel().getName())
                    .withTemperature(nodeConfig.getModel().getTemperature())
                    .withTopP(nodeConfig.getModel().getTopP())
                    .withMaxToken(nodeConfig.getModel().getMaxTokens())
                    .withResponseFormat(
                            DashScopeResponseFormat.builder()
                                    .type(DashScopeResponseFormat.Type.JSON_OBJECT)
                                    .build()
                    )
                    .build();
        } else {
            options = DashScopeChatOptions.builder()
                    .withModel(nodeConfig.getModel().getName())
                    .withTemperature(nodeConfig.getModel().getTemperature())
                    .withTopP(nodeConfig.getModel().getTopP())
                    .withMaxToken(nodeConfig.getModel().getMaxTokens())
                    .build();
        }
        
        // 创建 ChatClient
        return chatClientBuilder
                .defaultSystem(nodeConfig.getPrompt())
                .defaultOptions(options)
                .build();
    }
}
