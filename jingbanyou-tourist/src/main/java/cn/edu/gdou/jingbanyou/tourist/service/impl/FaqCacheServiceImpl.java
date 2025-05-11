package cn.edu.gdou.jingbanyou.tourist.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.Faq;
import cn.edu.gdou.jingbanyou.manage.service.IFaqService;
import cn.edu.gdou.jingbanyou.tourist.service.IFaqCacheService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 热门 FAQ 缓存实现
 *
 * 使用 Caffeine 本地缓存，TTL 1 分钟，最大缓存 200 个景区
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaqCacheServiceImpl implements IFaqCacheService {

    private static final int DEFAULT_LIMIT = 10;

    private final Cache<String, List<Faq>> cache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(200)
            .build();

    private final IFaqService faqService;

    @Override
    public List<Faq> getHotFaqs(Long scenicId, int limit) {
        if (scenicId == null) {
            return null;
        }
        String key = buildKey(scenicId, limit);
        List<Faq> faqs = cache.getIfPresent(key);
        if (faqs != null) {
            log.debug("[热门FAQ缓存] 命中，key={}", key);
        }
        return faqs;
    }

    @Override
    public void cacheHotFaqs(Long scenicId, int limit, List<Faq> faqs) {
        if (scenicId == null || faqs == null) {
            return;
        }
        String key = buildKey(scenicId, limit);
        cache.put(key, faqs);
        log.info("[热门FAQ缓存] 写入，key={}, 数量={}", key, faqs.size());
    }

    @Override
    public void invalidate(Long scenicId) {
        if (scenicId == null) {
            return;
        }
        // 清除该景区所有 limit 组合的缓存
        cache.asMap().keySet().removeIf(key -> key.startsWith("hot:" + scenicId + ":"));
        log.info("[热门FAQ缓存] 失效，scenicId={}", scenicId);
    }

    /**
     * 从缓存获取热门 FAQ，缓存 miss 时从数据库加载并回填
     *
     * @param scenicId 景区 ID
     * @param limit    返回数量上限
     * @return 热门 FAQ 列表
     */
    public List<Faq> getOrLoadHotFaqs(Long scenicId, int limit) {
        if (scenicId == null) {
            return List.of();
        }
        String key = buildKey(scenicId, limit);
        return cache.get(key, k -> {
            log.info("[热门FAQ缓存] 加载数据库，scenicId={}, limit={}", scenicId, limit);
            List<Faq> faqs = faqService.getHotQuestions(scenicId, limit);
            return faqs != null ? faqs : List.of();
        });
    }

    private String buildKey(Long scenicId, int limit) {
        return "hot:" + scenicId + ":" + limit;
    }
}
