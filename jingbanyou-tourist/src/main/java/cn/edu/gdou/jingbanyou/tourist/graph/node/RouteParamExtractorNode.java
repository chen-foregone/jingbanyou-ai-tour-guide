package cn.edu.gdou.jingbanyou.tourist.graph.node;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.druid.support.json.JSONUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        ObjectMapper objectMapper = new ObjectMapper();
        //对用户的路径参数进行提取
        String question = state.value(GraphStateKey.QUESTION,String.class).orElse("");
        String questionJson = JSONUtils.toJSONString(question);
        Map map = objectMapper.readValue(questionJson, Map.class);
        //起点
        String routeStart = map.get(GraphStateKey.ROUTE_START).toString();
        //终点
        String routeEnd = map.get(GraphStateKey.ROUTE_END).toString();
        //判断起始点是否为空
        if (routeStart == null || routeStart.isEmpty() || routeEnd == null || routeEnd.isEmpty()) {
            return state.updateState(Map.of(GraphStateKey.ROUTE_PARAMS_EXIST, false));
        }
        return state.updateState(Map.of(GraphStateKey.ROUTE_PARAMS_EXIST, true, GraphStateKey.ROUTE_START, routeStart, GraphStateKey.ROUTE_END, routeEnd));
    }
}
