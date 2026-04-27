package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.entity.OperationStats;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

/**
 * 景区运营数据统计 Service 接口（数据大屏核心功能）
 *
 * @author jingbanyou
 */
public interface IOperationStatsService extends IService<OperationStats> {

    /**
     * 获取今日实时概览
     *
     * @param scenicId 景区ID
     * @return 包含 overview 的结果
     */
    Map<String, Object> getTodayOverview(Long scenicId);

    /**
     * 获取本周运营数据
     *
     * @param scenicId 景区ID
     * @return 包含 dailyStats 和 summary 的结果
     */
    Map<String, Object> getWeeklyStats(Long scenicId);

    /**
     * 获取热门问答 TOP N
     *
     * @param scenicId 景区ID
     * @param limit 条数限制
     * @return 包含 hotQuestions 列表的结果
     */
    Map<String, Object> getHotQuestions(Long scenicId, Integer limit);

    /**
     * 生成统计数据
     *
     * @param scenicId 景区ID
     * @param date 统计日期
     * @param type 统计类型
     */
    void generateStats(Long scenicId, String date, String type);
}
