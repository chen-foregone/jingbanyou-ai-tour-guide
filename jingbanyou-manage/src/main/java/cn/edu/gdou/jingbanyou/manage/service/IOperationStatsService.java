package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.dto.response.HotQuestionsResponse;
import cn.edu.gdou.jingbanyou.manage.dto.response.OperationOverviewResponse;
import cn.edu.gdou.jingbanyou.manage.dto.response.WeeklyStatsResponse;
import cn.edu.gdou.jingbanyou.manage.entity.OperationStats;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;

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
     * @return 今日运营数据概览响应
     */
    OperationOverviewResponse getTodayOverview(Long scenicId);

    /**
     * 获取本周运营数据
     *
     * @param scenicId 景区ID
     * @return 本周统计数据响应
     */
    WeeklyStatsResponse getWeeklyStats(Long scenicId);

    /**
     * 获取热门问答 TOP N
     *
     * @param scenicId 景区ID
     * @param limit 条数限制
     * @return 热门问答列表响应
     */
    HotQuestionsResponse getHotQuestions(Long scenicId, Integer limit);

    /**
     * 生成统计数据
     *
     * @param scenicId 景区ID
     * @param date 统计日期
     * @param type 统计类型
     */
    void generateStats(Long scenicId, LocalDate date, String type);
}
