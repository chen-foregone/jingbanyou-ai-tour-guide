package cn.edu.gdou.jingbanyou.tourist.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 路线润色节点（导航话术生成）
 * 任务：把结构化路线数据（经纬度、距离、时间）转换成自然、友好、游客易懂的导航话术
 * 注意：暂时禁用，等待配置完善后启用
 */
// @Component  // TODO: 添加 jingbanyou.ai.map-route-invoker 配置后启用
public class MapRouteApiInvokerNode implements NodeAction {

    private final ChatClient chatClient;

    public MapRouteApiInvokerNode(@Qualifier("mapRouteInvokerChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // TODO: 实现地图路线API调用逻辑
        return new HashMap<>();
    }
}
