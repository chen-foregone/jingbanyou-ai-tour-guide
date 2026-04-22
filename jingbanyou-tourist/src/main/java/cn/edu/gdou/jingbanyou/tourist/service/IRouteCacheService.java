package cn.edu.gdou.jingbanyou.tourist.service;

import java.util.List;
import java.util.Map;

/**
 * 路线缓存服务
 *
 * 职责：
 * 1. 按景区+起点+终点查询 Redis 缓存，命中则直接返回
 * 2. 润色完成后写入缓存，减少重复 MCP 调用
 */
public interface IRouteCacheService {

    /**
     * 从 Redis 缓存中查询路线
     *
     * @param scenicId 景区 ID
     * @param startName 起点名称
     * @param endName 终点名称
     * @return 缓存的路线列表，无缓存则返回空列表
     */
    List<Map<String, Object>> getCachedRoutes(Long scenicId, String startName, String endName);

    /**
     * 将润色后的路线写入 Redis 缓存
     *
     * @param scenicId 景区 ID
     * @param startName 起点名称
     * @param endName 终点名称
     * @param routes 路线列表
     */
    void cacheRoutes(Long scenicId, String startName, String endName, List<Map<String, Object>> routes);
}
