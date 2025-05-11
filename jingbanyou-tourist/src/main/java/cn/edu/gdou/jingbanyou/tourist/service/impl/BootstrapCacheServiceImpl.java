package cn.edu.gdou.jingbanyou.tourist.service.impl;

import cn.edu.gdou.jingbanyou.tourist.service.IBootstrapCacheService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 首屏引导数据缓存实现
 *
 * 使用 Caffeine 本地缓存，TTL 5 分钟，最大缓存 100 个景区
 *
 * @author jingbanyou
 */
@Slf4j
@Service
public class BootstrapCacheServiceImpl implements IBootstrapCacheService {

    private final Cache<Long, Map<String, Object>> cache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    @Override
    public Map<String, Object> getBootstrapData(Long scenicId) {
        if (scenicId == null) {
            return null;
        }
        Map<String, Object> data = cache.getIfPresent(scenicId);
        if (data != null) {
            log.debug("[引导缓存] 命中，scenicId={}", scenicId);
        }
        return data;
    }

    @Override
    public void cacheBootstrapData(Long scenicId, Map<String, Object> data) {
        if (scenicId == null || data == null) {
            return;
        }
        cache.put(scenicId, data);
        log.info("[引导缓存] 写入，scenicId={}", scenicId);
    }

    @Override
    public void invalidate(Long scenicId) {
        if (scenicId == null) {
            return;
        }
        cache.invalidate(scenicId);
        log.info("[引导缓存] 失效，scenicId={}", scenicId);
    }
}
