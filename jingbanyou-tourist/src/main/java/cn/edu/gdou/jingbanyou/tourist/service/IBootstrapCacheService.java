package cn.edu.gdou.jingbanyou.tourist.service;

import java.util.Map;

/**
 * 首屏引导数据缓存服务
 *
 * 职责：缓存 /api/tourist/bootstrap 接口的返回结果，减少重复的数据库查询
 */
public interface IBootstrapCacheService {

    /**
     * 从缓存中获取引导数据
     *
     * @param scenicId 景区 ID
     * @return 缓存的引导数据（scenic + digitalHuman + conversation），无缓存返回 null
     */
    Map<String, Object> getBootstrapData(Long scenicId);

    /**
     * 将引导数据写入缓存
     *
     * @param scenicId 景区 ID
     * @param data     引导数据（scenic + digitalHuman + conversation）
     */
    void cacheBootstrapData(Long scenicId, Map<String, Object> data);

    /**
     * 失效指定景区的缓存（景区信息更新时调用）
     *
     * @param scenicId 景区 ID
     */
    void invalidate(Long scenicId);
}
