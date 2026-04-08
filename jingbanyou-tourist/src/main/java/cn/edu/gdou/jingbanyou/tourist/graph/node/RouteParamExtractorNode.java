package cn.edu.gdou.jingbanyou.tourist.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 参数提取节点（路线规划用）
 * 任务：从用户问题里提取「起点、终点」，结构化输出，要求精准、无遗漏
 * 注意：暂时禁用，等待配置完善后启用
 */
// @Component  // TODO: 添加 jingbanyou.ai.route-param-extractor 配置后启用
public class RouteParamExtractorNode implements NodeAction {

    private final ChatClient chatClient;

    public RouteParamExtractorNode(@Qualifier("routeParamExtractorChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // TODO: 实现路线参数提取逻辑
        return new HashMap<>();
    }
}
