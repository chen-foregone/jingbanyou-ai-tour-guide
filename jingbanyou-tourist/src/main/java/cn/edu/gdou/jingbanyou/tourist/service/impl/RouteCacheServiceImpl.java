package cn.edu.gdou.jingbanyou.tourist.service.impl;

import cn.edu.gdou.jingbanyou.tourist.service.IRouteCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 路线缓存服务实现
 *
 * 职责：
 * 1. 按景区+起点+终点查询 Redis 缓存，命中则直接返回
 * 2. 润色完成后写入缓存，减少重复 MCP 调用
 */
@Slf4j
@Service
@ConfigurationProperties(prefix = "jingbanyou.ai.route-cache")
public class RouteCacheServiceImpl implements IRouteCacheService {

    /**
     * Redis key 前缀
     */
    private static final String KEY_PREFIX = "route:cache:";

    /**
     * 缓存过期时间（秒），默认 7 天
     */
    private long ttlSeconds = 7 * 24 * 60 * 60;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<Map<String, Object>> getCachedRoutes(Long scenicId, String startName, String endName) {
        if (scenicId == null || isBlank(startName) || isBlank(endName)) {
            return Collections.emptyList();
        }

        String key = buildKey(scenicId, startName, endName);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                log.debug("[路线缓存] 未命中，key={}", key);
                return Collections.emptyList();
            }

            List<Map<String, Object>> routes = objectMapper.readValue(
                    json, new TypeReference<List<Map<String, Object>>>() {});
            log.info("[路线缓存] 命中缓存，key={}, 路线数量={}", key, routes.size());
            return routes;
        } catch (Exception e) {
            log.warn("[路线缓存] 读取缓存异常，key={}", key, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void cacheRoutes(Long scenicId, String startName, String endName, List<Map<String, Object>> routes) {
        if (scenicId == null || isBlank(startName) || isBlank(endName) || routes == null || routes.isEmpty()) {
            return;
        }

        String key = buildKey(scenicId, startName, endName);
        try {
            String json = objectMapper.writeValueAsString(routes);
            redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
            log.info("[路线缓存] 写入缓存，key={}, ttl={}s, 路线数量={}", key, ttlSeconds, routes.size());
        } catch (Exception e) {
            log.warn("[路线缓存] 写入缓存异常，key={}", key, e);
        }
    }

    /**
     * 构建 Redis key
     * 格式：route:cache:{scenicId}:{startName}:{endName}
     * 起点/终点名称做归一化处理（全小写 + 下划线替代空格）
     */
    private String buildKey(Long scenicId, String startName, String endName) {
        return KEY_PREFIX + scenicId + ":" + normalize(startName) + ":" + normalize(endName);
    }

    private String normalize(String name) {
        return name.trim().toLowerCase().replace(" ", "_");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
