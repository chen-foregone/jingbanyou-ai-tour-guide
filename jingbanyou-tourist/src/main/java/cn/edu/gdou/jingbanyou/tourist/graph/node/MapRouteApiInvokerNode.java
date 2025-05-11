package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.edu.gdou.jingbanyou.common.core.redis.RedisCache;
import cn.edu.gdou.jingbanyou.tourist.pojo.VisitorProfile;
import cn.edu.gdou.jingbanyou.tourist.service.IRouteCacheService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 路线收集节点
 *
 * 职责：
 * 1. 检测用户问题中起点/终点是否完整
 * 2. 缺参 → 返回引导语（GUIDE_MESSAGE），中断路线规划
 * 3. 参齐全 → 调用高德地图 MCP，返回多条路线（RAW_ROUTES）
 *
 * 优化：调用地图 API 前先查 Redis 缓存，命中则跳过 LLM 调用
 *
 * 协议：prompt 要求模型遵守输出格式约定：
 * - 参齐全：直接输出路线 JSON 数组 [...]
 * - 缺参：以纯文本回复（不含 JSON 结构），用于引导用户补充信息
 *
 * @author jingbanyou
 * @author jingbanyou
 */
@Slf4j
@Component
public class MapRouteApiInvokerNode implements NodeAction {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final IRouteCacheService routeCacheService;
    private final RedisCache redisCache;
    private final String systemPrompt;
    private static final String REDIS_PROFILE_KEY_PREFIX = "visitor:profile:";

    public MapRouteApiInvokerNode(
            @Qualifier("mapRouteInvokerChatClient") ChatClient chatClient,
            ObjectMapper objectMapper,
            IRouteCacheService routeCacheService,
            RedisCache redisCache,
            @Value("${jingbanyou.ai.map-route-invoker.prompt:}") String systemPrompt) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.routeCacheService = routeCacheService;
        this.redisCache = redisCache;
        this.systemPrompt = systemPrompt;
    }

    @Override
    @CircuitBreaker(name = "amap-route", fallbackMethod = "applyFallback")
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");
        String sessionId = state.value(SESSION_ID, String.class).orElse(null);
        Long scenicId = state.value(SCENIC_ID, Long.class).orElse(null);
        String startPoint = state.value(START_POINT, String.class).orElse("");
        String endPoint = state.value(END_POINT, String.class).orElse("");

        log.info("[路线规划] 输入: question={}, sessionId={}, scenicId={}, 历史起点={}, 历史终点={}",
                question, sessionId, scenicId, startPoint, endPoint);

        // 手动替换占位符，避免 StringTemplate {} 分隔符与 JSON 示例冲突
        String resolvedPrompt = systemPrompt
                .replace("<startPoint>", startPoint != null ? startPoint : "")
                .replace("<endPoint>", endPoint != null ? endPoint : "");

        String llmResponse = chatClient.prompt()
                .system(sp -> sp.text(resolvedPrompt))
                .user(question)
                .advisors(ctx -> ctx.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
        log.info("[路线规划] 模型输出: {}", llmResponse);

        if (llmResponse != null && llmResponse.trim().startsWith("[")) {
            List<Map<String, Object>> rawRoutes = parseRoutes(llmResponse);
            if (rawRoutes.isEmpty()) {
                return state.updateState(Map.of(
                        GUIDE_MESSAGE, "抱歉，暂时无法获取路线信息",
                        ANSWER, "抱歉，暂时无法获取路线信息",
                        ROUTE_STATUS, "pending",
                        RAW_ROUTES, new ArrayList<>()
                ));
            }

            // 解析 contextAction 决定如何处理上下文
            String contextAction = extractContextAction(rawRoutes);
            String startName = extractFirstString(rawRoutes, "startName");
            String endName = extractFirstString(rawRoutes, "endName");

            log.info("[路线规划] contextAction={}, startName={}, endName={}", contextAction, startName, endName);

            // 根据 AI 判断处理上下文
            applyContextAction(state, contextAction, startName, endName);

            // 查询润色路线缓存
            List<Map<String, Object>> polishedRoutes =
                    routeCacheService.getCachedRoutes(scenicId, startName, endName);

            if (!polishedRoutes.isEmpty()) {
                log.info("[路线规划] 缓存命中，polishedRoutes={}条", polishedRoutes.size());
                return state.updateState(Map.of(
                        RAW_ROUTES, rawRoutes,
                        POLISHED_ROUTES, polishedRoutes,
                        ROUTE_STATUS, "success",
                        ROUTE_CACHE_HIT, true,
                        START_POINT, startName,
                        END_POINT, endName
                ));
            }

            return state.updateState(Map.of(
                    RAW_ROUTES, rawRoutes,
                    ROUTE_STATUS, "success",
                    START_POINT, startName,
                    END_POINT, endName
            ));
        } else {
            String guideMsg = extractGuideMessage(llmResponse);
            return state.updateState(Map.of(
                    GUIDE_MESSAGE, guideMsg,
                    ANSWER, guideMsg,
                    ROUTE_STATUS, "pending",
                    RAW_ROUTES, new ArrayList<>()
            ));
        }
    }

    /**
     * 从 LLM 输出中提取引导语，去除推理过程
     * LLM 可能输出多行（推理+引导语），只取最后一行非空内容
     */
    private String extractGuideMessage(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return "请告诉我您的起点和终点";
        }
        String[] lines = llmResponse.trim().split("\n");
        // 从最后一行往前找第一个有意义的行
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isBlank()) {
                // 如果该行以问号结尾，直接返回
                if (line.contains("？") || line.contains("?")) {
                    return line;
                }
            }
        }
        // 没有问号，返回最后一行
        return lines[lines.length - 1].trim();
    }

    /**
     * 解析路线 JSON 字符串
     *
     * @param json 原始 JSON 字符串
     * @return 路线列表
     */
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

    /**
     * 清理 JSON 字符串中的 markdown 代码块标记
     *
     * @param json 原始字符串
     * @return 清理后的字符串
     */
    private String cleanJson(String json) {
        if (json == null) return "[]";
        String cleaned = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        return cleaned.trim();
    }

    /**
     * 从路线列表中提取指定 key 的字符串值（取第一条）
     *
     * @param routes 路线列表
     * @param key 字段名
     * @return 字段值
     */
    private String extractFirstString(List<Map<String, Object>> routes, String key) {
        if (routes == null || routes.isEmpty()) return "";
        Object val = routes.get(0).get(key);
        return val != null ? val.toString() : "";
    }

    /**
     * 从路线列表中提取 contextAction（取第一条）
     * 取值：update / keep / clear
     */
    private String extractContextAction(List<Map<String, Object>> routes) {
        if (routes == null || routes.isEmpty()) return "clear";
        Object action = routes.get(0).get("contextAction");
        if (action == null) return "update";
        String a = action.toString().trim().toLowerCase();
        if ("keep".equals(a) || "update".equals(a) || "clear".equals(a)) {
            return a;
        }
        return "update";
    }

    /**
     * 根据 AI 判断的 contextAction 处理上下文
     * - update: 用新的 startName/endName 更新画像并持久化
     * - keep: 保留历史上下文，不更新
     * - clear: 清空画像中的 startPoint/endPoint
     */
    private void applyContextAction(OverAllState state, String contextAction,
                                    String newStartName, String newEndName) {
        try {
            String visitorId = state.value(VISITOR_ID, String.class).orElse(null);
            if (visitorId == null || visitorId.isBlank()) {
                return;
            }

            VisitorProfile profile = state.value(VISITOR_PROFILE, VisitorProfile.class)
                    .orElseGet(() -> {
                        VisitorProfile p = new VisitorProfile();
                        p.setVisitorId(visitorId);
                        return p;
                    });

            if ("clear".equals(contextAction)) {
                profile.setStartPoint(null);
                profile.setEndPoint(null);
                log.debug("[路线上下文] AI判断清除, visitorId={}", visitorId);
            } else if ("update".equals(contextAction)) {
                if (newStartName != null && !newStartName.isBlank()) {
                    profile.setStartPoint(newStartName);
                }
                if (newEndName != null && !newEndName.isBlank()) {
                    profile.setEndPoint(newEndName);
                }
                log.debug("[路线上下文] AI判断更新, visitorId={}, startPoint={}, endPoint={}",
                        visitorId, profile.getStartPoint(), profile.getEndPoint());
            } else {
                log.debug("[路线上下文] AI判断保持, visitorId={}, 不修改", visitorId);
                return;
            }

            redisCache.setCacheObject(
                    REDIS_PROFILE_KEY_PREFIX + visitorId, profile, 24 * 60, TimeUnit.MINUTES);

        } catch (Exception e) {
            log.warn("[路线上下文] 处理失败: {}", e.getMessage());
        }
    }

    /**
     * 路线规划熔断降级方法
     */
    private Map<String, Object> applyFallback(OverAllState state, Throwable t) {
        log.warn("[路线规划] 熔断降级: {}", t.getMessage());
        return state.updateState(Map.of(
                GUIDE_MESSAGE, "路线规划服务暂时不可用，请稍后重试",
                ANSWER, "路线规划服务暂时不可用，请稍后重试",
                ROUTE_STATUS, "pending",
                RAW_ROUTES, new ArrayList<>()
        ));
    }
}
