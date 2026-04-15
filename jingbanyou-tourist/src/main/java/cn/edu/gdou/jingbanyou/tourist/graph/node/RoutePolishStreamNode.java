package cn.edu.gdou.jingbanyou.tourist.graph.node;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import cn.edu.gdou.jingbanyou.tourist.pojo.VisitorProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import reactor.core.publisher.Flux;

/**
 * 路线润色情式节点
 */
@Component
public class RoutePolishStreamNode extends AnswerStreamNode {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public RoutePolishStreamNode(
            @Qualifier("routePolishChatClient") ChatClient chatClient,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    protected ChatClient getChatClient(Function<String, Object> stateGetter) {
        return chatClient;
    }

    @Override
    public String buildUserText(Function<String, Object> stateGetter) {
        List<Map<String, Object>> rawRoutes = stateGetter.apply(GraphStateKey.RAW_ROUTES) instanceof List l
                ? (List<Map<String, Object>>) l : new ArrayList<>();

        VisitorProfile profile = stateGetter.apply(GraphStateKey.VISITOR_PROFILE) instanceof VisitorProfile p
                ? p : new VisitorProfile();

        String userProfileDesc = buildProfileDescription(profile);
        String rawRoutesJson;
        try {
            rawRoutesJson = objectMapper.writeValueAsString(rawRoutes);
        } catch (Exception e) {
            rawRoutesJson = "[]";
        }

        return "用户画像：\n" + userProfileDesc + "\n\n原始路线数据：\n" + rawRoutesJson;
    }

    /**
     * 执行路线润色情式生成
     */
    public Flux<String> polishAndStream(Function<String, Object> stateGetter) {
        String userText = buildUserText(stateGetter);
        String sessionId = getSessionId(stateGetter);
        return streamAnswer(chatClient, userText, sessionId);
    }

    private String buildProfileDescription(VisitorProfile profile) {
        if (profile == null) return "";
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
}
