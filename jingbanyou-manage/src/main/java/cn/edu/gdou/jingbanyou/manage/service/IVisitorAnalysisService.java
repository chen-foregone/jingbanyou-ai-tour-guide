package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.entity.VisitorAnalysis;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.util.Map;

/**
 * 游客感受度分析 Service 接口（赛题要求核心功能）
 */
public interface IVisitorAnalysisService extends IService<VisitorAnalysis> {

    /**
     * 生成日报分析
     */
    VisitorAnalysis generateDailyReport(Long scenicId, LocalDate date);

    /**
     * 获取情感趋势数据
     */
    Map<String, Object> getEmotionTrend(Long scenicId, String startDate, String endDate);

    /**
     * 获取游客关注点 TOP10
     */
    Map<String, Object> getFocusPoints(Long scenicId, Integer limit);

    /**
     * 获取满意度趋势
     */
    Map<String, Object> getSatisfactionTrend(Long scenicId, Integer days);
}
