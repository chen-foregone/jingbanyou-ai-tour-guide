package cn.edu.gdou.jingbanyou.tourist.graph.node;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 路线收集节点（阶段一）
 * 任务：通过高德地图 MCP 工具获取「起点→终点」的多种路线方案（步行/驾车/公交）
 * 输出：多条原始路线数据，存入 state.RAW_ROUTES
 */
@Component
public class MapRouteApiInvokerNode implements NodeAction {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public MapRouteApiInvokerNode(
            @Qualifier("mapRouteInvokerChatClient") ChatClient chatClient,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 获取起点和终点
        String start = state.value(GraphStateKey.ROUTE_START, String.class).orElse("");
        String end = state.value(GraphStateKey.ROUTE_END, String.class).orElse("");

        if (start.isEmpty() || end.isEmpty()) {
            throw new IllegalStateException("起点或终点为空，无法规划路线");
        }

        // 2. 调用 ChatClient，通过 MCP 工具获取多条路线
        // LLM 会自动调用 amap MCP 工具获取步行、驾车、公交路线
        String routesJson = chatClient.prompt()
                .user(userSpec -> userSpec.params(Map.of(
                        "routeStart", start,
                        "routeEnd", end
                )))
                .call()
                .content();

        // 3. 解析 JSON 响应
        List<Map<String, Object>> rawRoutes = parseRoutes(routesJson);

        // 4. 存入 state
        return state.updateState(Map.of(GraphStateKey.RAW_ROUTES, rawRoutes));
    }

    /**
     * 解析 LLM 返回的路线 JSON
     */
    private List<Map<String, Object>> parseRoutes(String json) {
        try {
            // 尝试直接解析为 List
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            // 如果解析失败，尝试清理 JSON（去除 markdown 代码块等）
            String cleaned = cleanJson(json);
            try {
                return objectMapper.readValue(cleaned, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception ex) {
                // 解析失败，返回空列表
                return new ArrayList<>();
            }
        }
    }

    /**
     * 清理 JSON 字符串
     */
    private String cleanJson(String json) {
        if (json == null) return "[]";
        // 去除 markdown 代码块标记
        String cleaned = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        // 去除多余空白
        cleaned = cleaned.trim();
        return cleaned;
    }
}
