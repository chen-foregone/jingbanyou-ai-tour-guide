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
 * 路线收集节点
 * 职责：
 * 1. 检测用户问题中起点/终点是否完整
 * 2. 缺参 → 返回引导语（GUIDE_MESSAGE），中断路线规划
 * 3. 参齐全 → 调用高德地图 MCP，返回多条路线（RAW_ROUTES）
 * <p>
 * 协议：prompt 要求模型遵守输出格式约定：
 * - 参齐全：直接输出路线 JSON 数组 [...]
 * - 缺参：以纯文本回复（不含 JSON 结构），用于引导用户补充信息
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
        String question = state.value(GraphStateKey.QUESTION, String.class).orElse("");

        String llmResponse = chatClient.prompt()
                .user(userSpec -> userSpec.params(Map.of("question", question)))
                .call()
                .content();

        // 协议判断：JSON 数组以 [ 开头
        if (llmResponse != null && llmResponse.trim().startsWith("[")) {
            List<Map<String, Object>> rawRoutes = parseRoutes(llmResponse);
            return state.updateState(Map.of(GraphStateKey.RAW_ROUTES, rawRoutes));
        } else {
            // 缺参或解析失败，返回引导语
            return state.updateState(Map.of(
                    GraphStateKey.GUIDE_MESSAGE,
                    llmResponse != null ? llmResponse : "请告诉我您的起点和终点",
                    GraphStateKey.RAW_ROUTES,
                    new ArrayList<>()
            ));
        }
    }

    private List<Map<String, Object>> parseRoutes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            String cleaned = cleanJson(json);
            try {
                return objectMapper.readValue(cleaned, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception ex) {
                return new ArrayList<>();
            }
        }
    }

    private String cleanJson(String json) {
        if (json == null) return "[]";
        String cleaned = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        return cleaned.trim();
    }
}
