package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import cn.edu.gdou.jingbanyou.tourist.pojo.VisitorProfile;
import cn.edu.gdou.jingbanyou.tourist.service.IProfileVectorStoreService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import cn.edu.gdou.jingbanyou.common.core.redis.RedisCache;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 画像更新节点
 *
 * <p>位置：各业务节点 → ProfileUpdaterNode → END
 * <p>职责：
 * <p>  1. 读取本轮 question + answer，调用轻量模型提取4个画像字段
 * <p>  2. 合并到 profile（兴趣标签去重最多10个，已访问景点去重最多20个）
 * <p>  3. turnCount++，出行类型和路线偏好取最新值
 * <p>  4. 同步写 Redis Hash（TTL 24h）
 * <p>  5. 异步写向量库（长期备份，防止 Redis TTL 过期丢失历史）
 */
@Slf4j
@Component
public class ProfileUpdaterNode implements NodeAction {

    private static final String REDIS_KEY_PREFIX = "visitor:profile:";
    private static final int MAX_TAGS = 10;
    private static final int MAX_SPOTS = 20;

    private final ChatClient chatClient;
    private final RedisCache redisCache;
    private final IProfileVectorStoreService profileVectorStoreService;
    private final ObjectMapper objectMapper;

    public ProfileUpdaterNode(
            @Qualifier("profileUpdateChatClient") ChatClient chatClient,
            RedisCache redisCache,
            IProfileVectorStoreService profileVectorStoreService,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.redisCache = redisCache;
        this.profileVectorStoreService = profileVectorStoreService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        VisitorProfile profile = state.value(VISITOR_PROFILE, VisitorProfile.class)
                .orElse(new VisitorProfile());

        String question = state.value(QUESTION, String.class).orElse("");
        String answer = state.value(ANSWER, String.class).orElse("");

        ExtractedProfile extracted = extractProfile(question, answer);

        // 检查是否真的有变化
        boolean changed = false;
        if (extracted.getInterestTags() != null && !extracted.getInterestTags().isEmpty()
                || extracted.getVisitedSpots() != null && !extracted.getVisitedSpots().isEmpty()
                || extracted.getGroupType() != null
                || extracted.getPreferRouteType() != null) {
            changed = true;
        }

        mergeTags(profile, extracted.getInterestTags());
        mergeVisitedSpots(profile, extracted.getVisitedSpots());

        if (extracted.getGroupType() != null) {
            profile.setGroupType(extracted.getGroupType());
        }
        if (extracted.getPreferRouteType() != null) {
            profile.setPreferRouteType(extracted.getPreferRouteType());
        }

        profile.setTurnCount(profile.getTurnCount() + 1);

        log.debug("[画像更新] visitorId={}, 标签={}, 出行类型={}, 已游景点={}, 路线偏好={}, 轮次={}, changed={}",
                profile.getVisitorId(), profile.getInterestTags(), profile.getGroupType(),
                profile.getVisitedSpots(), profile.getPreferRouteType(), profile.getTurnCount(), changed);

        // 同步写 Redis
        saveToRedis(profile);

        // 仅当有实际变化时才异步写向量库
        if (changed) {
            asyncSaveToVectorStore(profile);
        }

        return state.updateState(Map.of(VISITOR_PROFILE, profile));
    }

    /**
     * 从对话中提取画像字段
     */
    private ExtractedProfile extractProfile(String question, String answer) {
        try {
            String userText = "游客问题：" + question + "\nAI回答：" + answer;

            String raw = chatClient.prompt()
                    .user(userText)
                    .call()
                    .content();

            log.debug("[画像提取] 模型原始输出: {}", raw);

            if (raw == null || raw.isBlank()) {
                return new ExtractedProfile();
            }

            // 去掉 markdown 代码块包裹
            raw = raw.trim();
            if (raw.startsWith("```")) {
                int firstBrace = raw.indexOf('{');
                int lastBrace = raw.lastIndexOf('}');
                if (firstBrace >= 0 && lastBrace > firstBrace) {
                    raw = raw.substring(firstBrace, lastBrace + 1);
                }
            }

            ExtractedProfile extracted = objectMapper.readValue(raw, ExtractedProfile.class);
            log.info("[画像提取] 解析成功: 标签={}, 出行={}, 景点={}, 偏好={}",
                    extracted.getInterestTags(), extracted.getGroupType(),
                    extracted.getVisitedSpots(), extracted.getPreferRouteType());
            return extracted;

        } catch (Exception e) {
            log.warn("[画像提取] 解析失败，跳过本轮更新: {}", e.getMessage());
            return new ExtractedProfile();
        }
    }

    private void mergeTags(VisitorProfile profile, List<String> newTags) {
        if (newTags == null || newTags.isEmpty()) {
            return;
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>(newTags);
        if (profile.getInterestTags() != null) {
            merged.addAll(profile.getInterestTags());
        }
        List<String> list = new ArrayList<>(merged);
        profile.setInterestTags(list.subList(0, Math.min(list.size(), MAX_TAGS)));
    }

    private void mergeVisitedSpots(VisitorProfile profile, List<String> newSpots) {
        if (newSpots == null || newSpots.isEmpty()) {
            return;
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>(newSpots);
        if (profile.getVisitedSpots() != null) {
            merged.addAll(profile.getVisitedSpots());
        }
        List<String> list = new ArrayList<>(merged);
        profile.setVisitedSpots(list.subList(0, Math.min(list.size(), MAX_SPOTS)));
    }

    private void saveToRedis(VisitorProfile profile) {
        if (profile.getVisitorId() == null || profile.getVisitorId().isBlank()) {
            return;
        }
        try {
            redisCache.setCacheObject(
                    REDIS_KEY_PREFIX + profile.getVisitorId(), profile, 24 * 60, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[画像] Redis 写入失败，visitorId={}", profile.getVisitorId(), e);
        }
    }

    @Async
    public void asyncSaveToVectorStore(VisitorProfile profile) {
        if (profile.getVisitorId() == null || profile.getVisitorId().isBlank()) {
            return;
        }
        try {
            // 先删旧记录再写入，保持单用户单条向量
            profileVectorStoreService.deleteProfile(profile.getVisitorId());
            profileVectorStoreService.saveProfile(profile);
            log.debug("[画像] 向量库备份成功，visitorId={}", profile.getVisitorId());
        } catch (Exception e) {
            log.warn("[画像] 向量库备份失败，visitorId={}", profile.getVisitorId(), e);
        }
    }

    /**
     * LLM 提取结果（内部用，不持久化）
     */
    @lombok.Data
    private static class ExtractedProfile {
        private List<String> interestTags = new ArrayList<>();
        private String groupType;
        private List<String> visitedSpots = new ArrayList<>();
        private String preferRouteType;
    }
}
