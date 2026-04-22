package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.edu.gdou.jingbanyou.tourist.service.IRouteCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
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
 * 优化：调用地图 API 前先查 Redis 缓存，命中则跳过 LLM 调用
 * <p>
 * 协议：prompt 要求模型遵守输出格式约定：
 * - 参齐全：直接输出路线 JSON 数组 [...]
 * - 缺参：以纯文本回复（不含 JSON 结构），用于引导用户补充信息
 */
@Slf4j
@Component
public class MapRouteApiInvokerNode implements NodeAction {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final IRouteCacheService routeCacheService;

    public MapRouteApiInvokerNode(
            @Qualifier("mapRouteInvokerChatClient") ChatClient chatClient,
            ObjectMapper objectMapper,
            IRouteCacheService routeCacheService) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.routeCacheService = routeCacheService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");
        String sessionId = state.value(SESSION_ID, String.class).orElse(null);
        Long scenicId = state.value(SCENIC_ID, Long.class).orElse(null);

        log.info("[路线规划调用] 输入: question={}, sessionId={}, scenicId={}", question, sessionId, scenicId);

        // 先用 LLM 判断参数是否完整（调用地图 API 前）
        String llmResponse = chatClient.prompt()
                .user("用户问题：" + question)
                .advisors(new SimpleLoggerAdvisor())
                .advisors(ctx -> ctx.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
        log.info("[路线规划调用] 模型输出: {}", llmResponse);

        if (llmResponse != null && llmResponse.trim().startsWith("[")) {
            List<Map<String, Object>> rawRoutes = parseRoutes(llmResponse);

            // 从 rawRoutes 中提取起点/终点名称，查询缓存
            String startName = extractFirstString(rawRoutes, "startName");
            String endName = extractFirstString(rawRoutes, "endName");
            List<Map<String, Object>> polishedRoutes = routeCacheService.getCachedRoutes(scenicId, startName, endName);

            if (!polishedRoutes.isEmpty()) {
                log.info("[路线规划调用] 缓存命中，跳过 RoutePolish，rawRoutes={}条, polishedRoutes={}条",
                        rawRoutes.size(), polishedRoutes.size());
                return state.updateState(Map.of(
                        RAW_ROUTES, rawRoutes,
                        POLISHED_ROUTES, polishedRoutes,
                        ROUTE_STATUS, "success",
                        ROUTE_CACHE_HIT, true
                ));
            }

            // 未命中缓存，继续后续流程（MapRouteApiInvoker + RoutePolish 会写入缓存）
            return state.updateState(Map.of(
                    RAW_ROUTES, rawRoutes,
                    ROUTE_STATUS, "success"
            ));
        } else {
            String guideMsg = llmResponse != null ? llmResponse : "请告诉我您的起点和终点";
            return state.updateState(Map.of(
                    GUIDE_MESSAGE, guideMsg,
                    ANSWER, guideMsg,
                    ROUTE_STATUS, "pending",
                    RAW_ROUTES, new ArrayList<>()
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

    private String extractFirstString(List<Map<String, Object>> routes, String key) {
        if (routes == null || routes.isEmpty()) return "";
        Object val = routes.get(0).get(key);
        return val != null ? val.toString() : "";
    }
}
