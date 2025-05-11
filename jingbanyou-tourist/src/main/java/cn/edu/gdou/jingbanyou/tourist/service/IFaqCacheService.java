package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.manage.entity.Faq;

import java.util.List;

/**
 * 热门 FAQ 缓存服务
 *
 * 职责：缓存按景区分组的热门问答列表，减少数据库查询
 */
public interface IFaqCacheService {

    /**
     * 从缓存获取热门 FAQ 列表
     *
     * @param scenicId 景区 ID
     * @param limit    返回数量上限
     * @return 热门 FAQ 列表，无缓存返回 null
     */
    List<Faq> getHotFaqs(Long scenicId, int limit);

    /**
     * 将热门 FAQ 列表写入缓存
     *
     * @param scenicId 景区 ID
     * @param limit    返回数量上限
     * @param faqs     FAQ 列表
     */
    void cacheHotFaqs(Long scenicId, int limit, List<Faq> faqs);

    /**
     * 失效指定景区的热门 FAQ 缓存
     *
     * @param scenicId 景区 ID
     */
    void invalidate(Long scenicId);

    /**
     * 从缓存获取热门 FAQ，缓存 miss 时自动从数据库加载并回填
     *
     * @param scenicId 景区 ID
     * @param limit    返回数量上限
     * @return 热门 FAQ 列表
     */
    List<Faq> getOrLoadHotFaqs(Long scenicId, int limit);
}
