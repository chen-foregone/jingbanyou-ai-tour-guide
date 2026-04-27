package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.entity.VisitorAnalysis;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.util.Map;

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
     * @return 包含 trend 列表的结果
     */
    Map<String, Object> getEmotionTrend(Long scenicId, String startDate, String endDate);

    /**
     * 获取游客关注点 TOP N
     *
     * @param scenicId 景区ID
     * @param limit 条数限制
     * @return 包含 focusPoints 列表的结果
     */
    Map<String, Object> getFocusPoints(Long scenicId, Integer limit);

    /**
     * 获取满意度趋势
     *
     * @param scenicId 景区ID
     * @param days 天数
     * @return 包含 trend 列表的结果
     */
    Map<String, Object> getSatisfactionTrend(Long scenicId, Integer days);
}
