package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import cn.edu.gdou.jingbanyou.tourist.pojo.VisitorProfile;
import cn.edu.gdou.jingbanyou.tourist.service.ProfileVectorStoreService;
import cn.edu.gdou.jingbanyou.tourist.service.ProfileVectorStoreService.SimilarProfile;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 画像加载节点
 * 位置：START → ProfileLoaderNode → (条件路由) → TextDistinguishNode / MultimodalDistinguishNode
 * 逻辑：
 *   1. 从 Redis 读取历史画像，无则初始化空画像
 *   2. 从向量库检索相似历史偏好（如用户说"上次那个有花的地方"）
 *   3. 合并检索结果到画像，写入 OverAllState
 * 不调用 LLM，纯 IO 操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileLoaderNode implements NodeAction {

    private static final String REDIS_KEY_PREFIX = "visitor:profile:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProfileVectorStoreService profileVectorStoreService;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String visitorId = state.value(VISITOR_ID, String.class).orElse(null);

        VisitorProfile profile;

        if (visitorId == null || visitorId.isBlank()) {
            log.debug("未提供 visitorId，使用空画像");
            profile = new VisitorProfile();
        } else {
            String json = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + visitorId);
            if (json != null) {
                profile = objectMapper.readValue(json, VisitorProfile.class);
                log.debug("从 Redis 加载画像成功，visitorId={}, 已有兴趣标签: {}", visitorId, profile.getInterestTags());
            } else {
                profile = new VisitorProfile();
                profile.setVisitorId(visitorId);
                log.debug("Redis 中无历史画像，初始化空画像，visitorId={}", visitorId);
            }
            enrichFromVectorStore(profile);
        }

        return state.updateState(Map.of(VISITOR_PROFILE, profile));
    }

    private void enrichFromVectorStore(VisitorProfile profile) {
        if (profile.getVisitorId() == null || profile.getVisitorId().isBlank()) {
            return;
        }

        try {
            String query = buildRetrievalQuery(profile);
            if (query.isBlank()) {
                return;
            }

            List<SimilarProfile> similarProfiles =
                    profileVectorStoreService.retrieveSimilarProfiles(profile.getVisitorId(), query);

            if (similarProfiles.isEmpty()) {
                return;
            }

            for (SimilarProfile sp : similarProfiles) {
                if (sp.getScore() > 0.7) {
                    mergeSimilarProfile(profile, sp);
                }
            }

            log.debug("从向量库合并相似偏好完成，visitorId={}, 检索到 {} 条记录",
                    profile.getVisitorId(), similarProfiles.size());

        } catch (Exception e) {
            log.warn("从向量库检索相似偏好失败，visitorId={}", profile.getVisitorId(), e);
        }
    }

    private String buildRetrievalQuery(VisitorProfile profile) {
        StringBuilder query = new StringBuilder();

        if (profile.getPreferRouteType() != null) {
            query.append(profile.getPreferRouteType()).append("路线 ");
        }

        if (profile.getInterestTags() != null && !profile.getInterestTags().isEmpty()) {
            query.append(String.join("、", profile.getInterestTags())).append("景点 ");
        }

        return query.toString().trim();
    }

    private void mergeSimilarProfile(VisitorProfile profile, SimilarProfile sp) {
        Map<String, Object> metadata = sp.getMetadata();
        if (metadata == null) {
            return;
        }

        String tags = (String) metadata.get("interestTags");
        if (tags != null && !tags.isBlank()) {
            String[] newTags = tags.split(",");
            Set<String> mergedTags = new LinkedHashSet<>();
            if (profile.getInterestTags() != null) {
                mergedTags.addAll(profile.getInterestTags());
            }
            Collections.addAll(mergedTags, newTags);
            profile.setInterestTags(new ArrayList<>(mergedTags));
        }

        String spots = (String) metadata.get("visitedSpots");
        if (spots != null && !spots.isBlank()) {
            String[] newSpots = spots.split(",");
            Set<String> mergedSpots = new LinkedHashSet<>();
            if (profile.getVisitedSpots() != null) {
                mergedSpots.addAll(profile.getVisitedSpots());
            }
            Collections.addAll(mergedSpots, newSpots);
            profile.setVisitedSpots(new ArrayList<>(mergedSpots));
        }

        if (profile.getPreferRouteType() == null || profile.getPreferRouteType().isBlank()) {
            String preferRouteType = (String) metadata.get("preferRouteType");
            if (preferRouteType != null && !preferRouteType.isBlank()) {
                profile.setPreferRouteType(preferRouteType);
            }
        }
    }
}
