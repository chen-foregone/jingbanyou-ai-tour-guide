package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import cn.edu.gdou.jingbanyou.tourist.pojo.VisitorProfile;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 画像更新节点
 * 位置：[各业务节点] → ProfileUpdaterNode → END
 * 逻辑：
 *   1. 读取本轮 question + answer，调用轻量模型提取兴趣标签
 *   2. 合并标签到 profile（去重，最多 10 个）
 *   3. 累加已访问景点（最多 20 个）
 *   4. turnCount++
 *   5. 写回 OverAllState，异步写 Redis（TTL 24h）
 */
@Slf4j
@Component
public class ProfileUpdaterNode implements NodeAction {

    private static final String REDIS_KEY_PREFIX = "visitor:profile:";
    private static final int MAX_TAGS = 10;

    private final ChatClient chatClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ProfileUpdaterNode(
            @Qualifier("profileUpdateChatClient") ChatClient chatClient,
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        VisitorProfile profile = state.value(VISITOR_PROFILE, VisitorProfile.class)
                .orElse(new VisitorProfile());

        String question = state.value(QUESTION, String.class).orElse("");
        String answer = state.value(ANSWER, String.class)
                .or(() -> state.value(CHAT_RESPONSE, String.class))
                .or(() -> state.value(ROUTE_DESCRIPTION, String.class))
                .orElse("");

        List<String> newTags = extractTags(question, answer);

        LinkedHashSet<String> merged = new LinkedHashSet<>(newTags);
        if (profile.getInterestTags() != null) {
            merged.addAll(profile.getInterestTags());
        }
        List<String> mergedList = new ArrayList<>(merged);
        profile.setInterestTags(mergedList.subList(0, Math.min(mergedList.size(), MAX_TAGS)));

        profile.setTurnCount(profile.getTurnCount() + 1);

        log.debug("画像更新完成，visitorId={}, 兴趣标签={}, 轮次={}",
                profile.getVisitorId(), profile.getInterestTags(), profile.getTurnCount());

        asyncSaveToRedis(profile);

        return state.updateState(Map.of(VISITOR_PROFILE, profile));
    }

    private List<String> extractTags(String question, String answer) {
        try {
            String raw = chatClient.prompt()
                    .user(u -> u.text("问题：{question}\n回答：{answer}")
                            .param("question", question)
                            .param("answer", answer))
                    .call()
                    .content();

            if (raw != null && raw.contains("[")) {
                String jsonPart = raw.substring(raw.indexOf('['), raw.lastIndexOf(']') + 1);
                return objectMapper.readValue(jsonPart,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
        } catch (Exception e) {
            log.warn("提取兴趣标签失败，跳过本轮标签更新", e);
        }
        return List.of();
    }

    @Async
    public void asyncSaveToRedis(VisitorProfile profile) {
        if (profile.getVisitorId() == null || profile.getVisitorId().isBlank()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(profile);
            redisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + profile.getVisitorId(), json, 24, TimeUnit.HOURS);
            log.debug("画像已异步写入 Redis，visitorId={}", profile.getVisitorId());
        } catch (Exception e) {
            log.warn("画像写入 Redis 失败，visitorId={}", profile.getVisitorId(), e);
        }
    }
}
