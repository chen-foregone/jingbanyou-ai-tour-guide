package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import cn.edu.gdou.jingbanyou.tourist.pojo.VisitorProfile;
import cn.edu.gdou.jingbanyou.tourist.service.IRouteCacheService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 路线润色节点（阶段二）
 *
 * 任务：结合用户画像，对多条原始路线进行润色、筛选、排序
 * 输入：state.RAW_ROUTES（多条原始路线）, state.VISITOR_PROFILE（用户画像）
 * 输出：state.POLISHED_ROUTES（润色后的路线列表）
 *
 * 优化：润色完成后写入 Redis 缓存（按起点+终点），供后续同路线查询命中
 *
 * @author jingbanyou
 * @author jingbanyou
 */
@Slf4j
@Component
public class RoutePolishNode implements NodeAction {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final IRouteCacheService routeCacheService;

    public RoutePolishNode(
            @Qualifier("routePolishChatClient") ChatClient chatClient,
            ObjectMapper objectMapper,
            IRouteCacheService routeCacheService) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.routeCacheService = routeCacheService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        List<Map<String, Object>> rawRoutes = state.value(RAW_ROUTES, List.class)
                .orElse(new ArrayList<>());

        if (rawRoutes.isEmpty()) {
            return state.updateState(Map.of(POLISHED_ROUTES, new ArrayList<>()));
        }

        VisitorProfile profile = state.value(VISITOR_PROFILE, VisitorProfile.class)
                .orElse(new VisitorProfile());
        Long scenicId = state.value(SCENIC_ID, Long.class).orElse(null);

        String userProfileDesc = buildProfileDescription(profile);
        String rawRoutesJson = objectMapper.writeValueAsString(rawRoutes);

        log.info("[路线润色] 输入: userProfile={}, rawRoutes={}", userProfileDesc, rawRoutesJson);
        String userText = "用户画像：\n" + userProfileDesc + "\n\n原始路线数据：\n" + rawRoutesJson;
        log.info("[路线润色] userText={}", userText);
        String polishedJson = chatClient.prompt()
                .user(userText)
                .call()
                .content();
        log.info("[路线润色] 输出: {}", polishedJson);

        List<Map<String, Object>> polishedRoutes = parsePolishedRoutes(polishedJson);

        // 写入缓存（按起点+终点，不含用户画像个人信息）
        String startName = extractFirst(rawRoutes, "startName");
        String endName = extractFirst(rawRoutes, "endName");
        if (!startName.isBlank() && !endName.isBlank()) {
            routeCacheService.cacheRoutes(scenicId, startName, endName, polishedRoutes);
        }

        return state.updateState(Map.of(
                ANSWER, polishedJson != null ? polishedJson : "",
                POLISHED_ROUTES, polishedRoutes
        ));
    }

    /**
     * 从路线列表中提取指定 key 的值（取第一条）
     *
     * @param routes 路线列表
     * @param key 字段名
     * @return 字段值
     */
    private String extractFirst(List<Map<String, Object>> routes, String key) {
        if (routes == null || routes.isEmpty()) return "";
        Object val = routes.get(0).get(key);
        return val != null ? val.toString() : "";
    }

    /**
     * 构建用户画像描述文本（供路线润色 prompt 使用）
     *
     * @param profile 游客画像
     * @return 画像描述文本
     */
    private String buildProfileDescription(VisitorProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("游客ID: ").append(profile.getVisitorId()).append("\n");

        if (profile.getGroupType() != null) {
            sb.append("出行类型: ").append(profile.getGroupType());
            switch (profile.getGroupType()) {
                case "solo" -> sb.append("（独自一人）");
                case "couple" -> sb.append("（情侣/夫妻）");
                case "family" -> sb.append("（家庭/亲子）");
                case "group" -> sb.append("（团队/多人）");
            }
            sb.append("\n");
        }

        if (profile.getPreferRouteType() != null) {
            sb.append("偏好路线类型: ").append(profile.getPreferRouteType()).append("\n");
        }

        if (profile.getInterestTags() != null && !profile.getInterestTags().isEmpty()) {
            sb.append("兴趣标签: ").append(String.join(", ", profile.getInterestTags())).append("\n");
        }

        if (profile.getVisitedSpots() != null && !profile.getVisitedSpots().isEmpty()) {
            sb.append("已游览景点: ").append(String.join(", ", profile.getVisitedSpots())).append("\n");
        }

        return sb.toString();
    }

    /**
     * 解析润色后的路线 JSON
     *
     * @param json 原始 JSON 字符串
     * @return 路线列表
     */
    private List<Map<String, Object>> parsePolishedRoutes(String json) {
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
}
