package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.entity.OperationStats;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

/**
 * 景区运营数据统计 Service 接口（数据大屏核心功能）
 */
public interface IOperationStatsService extends IService<OperationStats> {

    /**
     * 获取今日实时概览
     */
    Map<String, Object> getTodayOverview(Long scenicId);

    /**
     * 获取本周运营数据
     */
    Map<String, Object> getWeeklyStats(Long scenicId);

    /**
     * 获取热门问答 TOP10
     */
    Map<String, Object> getHotQuestions(Long scenicId, Integer limit);

    /**
     * 获取设备分布统计
     */
    Map<String, Object> getDeviceDistribution(Long scenicId);

    /**
     * 生成统计数据
     */
    void generateStats(Long scenicId, String date, String type);
}
