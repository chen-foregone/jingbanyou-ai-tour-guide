package cn.edu.gdou.jingbanyou.tourist.graph.node;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import cn.edu.gdou.jingbanyou.tourist.pojo.VisitorProfile;
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
 * 路线润色节点（阶段二）
 * 任务：结合用户画像，对多条原始路线进行润色、筛选、排序
 * 输入：state.RAW_ROUTES（多条原始路线）, state.VISITOR_PROFILE（用户画像）
 * 输出：state.POLISHED_ROUTES（润色后的路线列表）
 */
@Component
public class RoutePolishNode implements NodeAction {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public RoutePolishNode(
            @Qualifier("routePolishChatClient") ChatClient chatClient,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 获取原始路线
        List<Map<String, Object>> rawRoutes = state.value(GraphStateKey.RAW_ROUTES, List.class)
                .orElse(new ArrayList<>());

        if (rawRoutes.isEmpty()) {
            return state.updateState(Map.of(GraphStateKey.POLISHED_ROUTES, new ArrayList<>()));
        }

        // 2. 获取用户画像
        VisitorProfile profile = state.value(GraphStateKey.VISITOR_PROFILE, VisitorProfile.class)
                .orElse(new VisitorProfile());

        // 3. 构建用户画像描述（用于 prompt）
        String userProfileDesc = buildProfileDescription(profile);

        // 4. 将原始路线序列化为 JSON
        String rawRoutesJson = objectMapper.writeValueAsString(rawRoutes);

        // 5. 调用 LLM 进行润色
        String polishedJson = chatClient.prompt()
                .user(userSpec -> userSpec.params(Map.of(
                        "userProfile", userProfileDesc,
                        "rawRoutes", rawRoutesJson
                )))
                .call()
                .content();

        // 6. 解析润色后的路线
        List<Map<String, Object>> polishedRoutes = parsePolishedRoutes(polishedJson);

        // 7. 存入 state
        return state.updateState(Map.of(GraphStateKey.POLISHED_ROUTES, polishedRoutes));
    }

    /**
     * 构建用户画像描述字符串
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
     */
    private List<Map<String, Object>> parsePolishedRoutes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            // 解析失败，清理 JSON 后重试
            String cleaned = cleanJson(json);
            try {
                return objectMapper.readValue(cleaned, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception ex) {
                return new ArrayList<>();
            }
        }
    }

    /**
     * 清理 JSON 字符串
     */
    private String cleanJson(String json) {
        if (json == null) return "[]";
        String cleaned = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        return cleaned.trim();
    }
}
