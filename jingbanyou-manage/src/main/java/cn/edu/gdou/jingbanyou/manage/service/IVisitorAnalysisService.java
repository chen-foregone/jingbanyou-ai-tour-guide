package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.dto.response.EmotionTrendResponse;
import cn.edu.gdou.jingbanyou.manage.dto.response.FocusPointsResponse;
import cn.edu.gdou.jingbanyou.manage.dto.response.SatisfactionTrendResponse;
import cn.edu.gdou.jingbanyou.manage.entity.VisitorAnalysis;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;

/**
 * 游客感受度分析 Service 接口（赛题要求核心功能）
 *
 * @author jingbanyou
 */
public interface IVisitorAnalysisService extends IService<VisitorAnalysis> {

    /**
     * 生成日报分析
     *
     * @param scenicId 景区ID
     * @param date 统计日期
     * @return 日报实体，无交互数据时返回 null
     */
    VisitorAnalysis generateDailyReport(Long scenicId, LocalDate date);

    /**
     * 获取情感趋势数据
     *
     * @param scenicId 景区ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 情感趋势数据响应
     */
    EmotionTrendResponse getEmotionTrend(Long scenicId, LocalDate startDate, LocalDate endDate);

    /**
     * 获取游客关注点 TOP N
     *
     * @param scenicId 景区ID
     * @param limit 条数限制
     * @return 关注点列表响应
     */
    FocusPointsResponse getFocusPoints(Long scenicId, Integer limit);

    /**
     * 获取满意度趋势
     *
     * @param scenicId 景区ID
     * @param days 天数
     * @return 满意度趋势数据响应
     */
    SatisfactionTrendResponse getSatisfactionTrend(Long scenicId, Integer days);
}
