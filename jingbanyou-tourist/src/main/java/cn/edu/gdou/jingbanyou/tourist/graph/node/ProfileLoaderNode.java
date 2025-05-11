package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import cn.edu.gdou.jingbanyou.common.core.redis.RedisCache;
import cn.edu.gdou.jingbanyou.tourist.pojo.VisitorProfile;
import cn.edu.gdou.jingbanyou.tourist.service.IProfileVectorStoreService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 画像加载节点
 *
 * <p>位置：START → ProfileLoaderNode → TextDistinguishNode / MultimodalDistinguishNode
 * <p>职责：
 * <p>  1. 优先从 Redis 读取画像（有 TTL 24h 热数据）
 * <p>  2. Redis 未命中时，从向量库读取并写回 Redis（防止历史丢失）
 * <p>  3. 均无数据时初始化空画像
 * <p>不调用 LLM，纯 IO 操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileLoaderNode implements NodeAction {

    private static final String REDIS_KEY_PREFIX = "visitor:profile:";

    private final RedisCache redisCache;
    private final IProfileVectorStoreService profileVectorStoreService;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String visitorId = state.value(VISITOR_ID, String.class).orElse(null);

        VisitorProfile profile = loadProfile(visitorId);

        log.debug("[画像加载] visitorId={}, 标签={}, 出行类型={}, 已游景点={}, 路线偏好={}, 轮次={}",
                profile.getVisitorId(), profile.getInterestTags(), profile.getGroupType(),
                profile.getVisitedSpots(), profile.getPreferRouteType(), profile.getTurnCount());

        return state.updateState(Map.of(
                VISITOR_PROFILE, profile,
                START_POINT, profile.getStartPoint() != null ? profile.getStartPoint() : "",
                END_POINT, profile.getEndPoint() != null ? profile.getEndPoint() : ""
        ));
    }

    /**
     * 加载画像：Redis 优先，向量库兜底
     *
     * <p>设计说明：
     * <p>- Redis 是热存储，TTL 24h，承载会话内高频读写
     * <p>- 向量库是持久化备份，当 Redis TTL 过期或新设备访问时恢复历史画像
     */
    private VisitorProfile loadProfile(String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            log.debug("[画像加载] 未提供 visitorId，初始化空画像");
            return new VisitorProfile();
        }

        // 第一步：读 Redis
        VisitorProfile profile = loadFromRedis(visitorId);
        if (profile != null) {
            return profile;
        }

        // 第二步：Redis 未命中，从向量库恢复
        profile = loadFromVectorStore(visitorId);
        if (profile != null) {
            log.info("[画像加载] Redis 未命中，从向量库恢复画像，visitorId={}", visitorId);
            saveToRedis(profile);
            return profile;
        }

        // 第三步：均无数据，初始化
        log.debug("[画像加载] 无历史画像，初始化新画像，visitorId={}", visitorId);
        VisitorProfile newProfile = new VisitorProfile();
        newProfile.setVisitorId(visitorId);
        return newProfile;
    }

    private VisitorProfile loadFromRedis(String visitorId) {
        try {
            VisitorProfile profile = redisCache.getCacheObject(REDIS_KEY_PREFIX + visitorId);
            if (profile != null) {
                log.debug("[画像加载] Redis 命中，visitorId={}", visitorId);
                return profile;
            }
        } catch (Exception e) {
            log.warn("[画像加载] Redis 读取异常，visitorId={}: {}", visitorId, e.getMessage());
        }
        return null;
    }

    private VisitorProfile loadFromVectorStore(String visitorId) {
        try {
            List<IProfileVectorStoreService.SimilarProfile> results =
                    profileVectorStoreService.retrieveByVisitorId(visitorId);
            if (results == null || results.isEmpty()) {
                return null;
            }

            IProfileVectorStoreService.SimilarProfile sp = results.get(0);
            Map<String, Object> metadata = sp.getMetadata();
            if (metadata == null) {
                return null;
            }

            VisitorProfile profile = new VisitorProfile();
            profile.setVisitorId(visitorId);

            String tags = (String) metadata.get("interestTags");
            if (tags != null && !tags.isBlank()) {
                List<String> tagList = new ArrayList<>(Arrays.asList(tags.split(",")));
                tagList.removeIf(String::isBlank);
                profile.setInterestTags(tagList);
            }

            String groupType = (String) metadata.get("groupType");
            if (groupType != null && !groupType.isBlank() && !"null".equals(groupType)) {
                profile.setGroupType(groupType);
            }

            String spots = (String) metadata.get("visitedSpots");
            if (spots != null && !spots.isBlank()) {
                List<String> spotList = new ArrayList<>(Arrays.asList(spots.split(",")));
                spotList.removeIf(String::isBlank);
                profile.setVisitedSpots(spotList);
            }

            String preferRouteType = (String) metadata.get("preferRouteType");
            if (preferRouteType != null && !preferRouteType.isBlank() && !"null".equals(preferRouteType)) {
                profile.setPreferRouteType(preferRouteType);
            }

            return profile;

        } catch (Exception e) {
            log.warn("[画像加载] 向量库读取异常，visitorId={}: {}", visitorId, e.getMessage());
            return null;
        }
    }

    private void saveToRedis(VisitorProfile profile) {
        try {
            redisCache.setCacheObject(
                    REDIS_KEY_PREFIX + profile.getVisitorId(), profile, 24 * 60, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[画像加载] 恢复后写 Redis 失败，visitorId={}", profile.getVisitorId(), e);
        }
    }
}
