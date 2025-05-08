package cn.edu.gdou.jingbanyou.common.core.service;

import cn.edu.gdou.jingbanyou.common.constant.CacheConstants;
import cn.edu.gdou.jingbanyou.common.core.redis.RedisCache;
import cn.edu.gdou.jingbanyou.common.core.domain.VisitorSessionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 游客会话服务
 * 负责 visitorId 的校验、自动创建和续期
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TouristSessionService {

    /**
     * 会话有效期（2小时）
     */
    private static final long SESSION_TIMEOUT = 2L;

    private final RedisCache redisCache;

    /**
     * 获取会话 Redis key
     */
    private String getSessionKey(String visitorId) {
        return CacheConstants.VISITOR_SESSION_KEY + visitorId;
    }

    /**
     * 校验或创建会话
     * 如果会话不存在，自动创建
     *
     * @param visitorId  游客ID
     * @param sceneId    景区ID
     * @param entranceId 入口ID
     * @return 游客会话信息
     */
    public VisitorSessionDTO getOrCreateSession(String visitorId, Long sceneId, String entranceId) {
        String key = getSessionKey(visitorId);
        VisitorSessionDTO session = redisCache.getCacheObject(key);

        if (session == null) {
            // 会话不存在，创建新会话
            session = new VisitorSessionDTO(visitorId, sceneId, entranceId);
            redisCache.setCacheObject(key, session, (int) SESSION_TIMEOUT, TimeUnit.HOURS);
            log.info("[游客会话] 创建新会话: visitorId={}, sceneId={}", visitorId, sceneId);
        } else {
            // 会话存在，更新最后活跃时间并续期
            session.setLastActiveTime(System.currentTimeMillis());
            redisCache.setCacheObject(key, session, (int) SESSION_TIMEOUT, TimeUnit.HOURS);
            log.debug("[游客会话] 续期会话: visitorId={}, sceneId={}", visitorId, sceneId);
        }

        return session;
    }

    /**
     * 校验会话是否存在
     *
     * @param visitorId 游客ID
     * @return true=存在, false=不存在或已过期
     */
    public boolean isSessionValid(String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            return false;
        }
        String key = getSessionKey(visitorId);
        return redisCache.hasKey(key);
    }

    /**
     * 记录游客在线心跳（按景区维度，每个请求刷新一次时间戳）
     *
     * @param visitorId 游客ID
     * @param sceneId   景区ID
     */
    public void heartbeat(String visitorId, Long sceneId) {
        if (sceneId == null || visitorId == null || visitorId.isBlank()) {
            return;
        }
        String key = CacheConstants.VISITOR_ONLINE_KEY + sceneId;
        redisCache.zAdd(key, visitorId, System.currentTimeMillis());
        redisCache.expire(key, 24, TimeUnit.HOURS);
    }

    /**
     * 获取景区实时在线人数（过去2小时内有心跳的游客）
     *
     * @param sceneId 景区ID
     * @return 在线人数
     */
    public long getOnlineCount(Long sceneId) {
        if (sceneId == null) {
            return 0;
        }
        String key = CacheConstants.VISITOR_ONLINE_KEY + sceneId;
        long twoHoursAgo = System.currentTimeMillis() - 2 * 3600 * 1000L;
        return redisCache.zCount(key, twoHoursAgo, Long.MAX_VALUE);
    }

    /**
     * 获取景区当日累计游客数
     *
     * @param sceneId 景区ID
     * @return 今日游客数
     */
    public long getDailyVisitorCount(Long sceneId) {
        if (sceneId == null) {
            return 0;
        }
        String key = CacheConstants.VISITOR_ONLINE_KEY + sceneId;
        return redisCache.zCard(key);
    }

    /**
     * 获取会话信息
     *
     * @param visitorId 游客ID
     * @return 会话信息，不存在返回null
     */
    public VisitorSessionDTO getSession(String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            return null;
        }
        String key = getSessionKey(visitorId);
        return redisCache.getCacheObject(key);
    }

    /**
     * 删除会话
     *
     * @param visitorId 游客ID
     */
    public void deleteSession(String visitorId) {
        if (visitorId != null && !visitorId.isBlank()) {
            String key = getSessionKey(visitorId);
            redisCache.deleteObject(key);
            log.info("[游客会话] 删除会话: visitorId={}", visitorId);
        }
    }

    /**
     * 刷新会话（续期）
     *
     * @param visitorId 游客ID
     */
    public void refreshSession(String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            return;
        }
        VisitorSessionDTO session = getSession(visitorId);
        if (session != null) {
            session.setLastActiveTime(System.currentTimeMillis());
            String key = getSessionKey(visitorId);
            redisCache.setCacheObject(key, session, (int) SESSION_TIMEOUT, TimeUnit.HOURS);
        }
    }
}
