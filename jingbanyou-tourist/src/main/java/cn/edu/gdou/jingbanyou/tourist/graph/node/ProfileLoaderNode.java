package cn.edu.gdou.jingbanyou.tourist.graph.node;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import cn.edu.gdou.jingbanyou.tourist.pojo.VisitorProfile;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 画像加载节点
 * 位置：START → ProfileLoaderNode → DistinguishNode → ...
 * 逻辑：从 Redis 读取历史画像，无则初始化空画像，写入 OverAllState
 * 不调用 LLM，纯 IO 操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileLoaderNode implements NodeAction {

    private static final String REDIS_KEY_PREFIX = "visitor:profile:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String visitorId = state.value(GraphStateKey.VISITOR_ID, String.class)
                .orElse(null);

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
        }

        Map<String, Object> result = new HashMap<>();
        result.put(GraphStateKey.VISITOR_PROFILE, profile);
        return result;
    }
}
