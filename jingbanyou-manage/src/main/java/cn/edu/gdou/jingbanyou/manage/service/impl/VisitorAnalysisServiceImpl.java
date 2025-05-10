package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.dto.EmotionTrendVO;
import cn.edu.gdou.jingbanyou.manage.dto.FocusPointVO;
import cn.edu.gdou.jingbanyou.manage.dto.SatisfactionTrendVO;
import cn.edu.gdou.jingbanyou.manage.dto.response.EmotionTrendResponse;
import cn.edu.gdou.jingbanyou.manage.dto.response.FocusPointsResponse;
import cn.edu.gdou.jingbanyou.manage.dto.response.SatisfactionTrendResponse;
import cn.edu.gdou.jingbanyou.manage.entity.VisitorAnalysis;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorAnalysisMapper;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorInteractionMapper;
import cn.edu.gdou.jingbanyou.manage.service.IVisitorAnalysisService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 游客感受度分析 Service 实现类（赛题要求核心功能）
 *
 * @author jingbanyou
 */
@Slf4j
@Service
public class VisitorAnalysisServiceImpl extends ServiceImpl<VisitorAnalysisMapper, VisitorAnalysis> implements IVisitorAnalysisService {

    @Autowired
    private VisitorInteractionMapper visitorInteractionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 生成日报分析
     *
     * @param scenicId 景区ID
     * @param date 统计日期
     * @return 日报实体，无交互数据时返回 null
     */
    @Override
    public VisitorAnalysis generateDailyReport(Long scenicId, LocalDate date) {
        log.info("生成日报分析：scenicId={}, date={}", scenicId, date);
        String dateStr = date.toString();

        // 查询当日交互汇总
        Map<String, Object> summary = visitorInteractionMapper.selectDailyReportSummary(scenicId, dateStr);
        if (summary == null || summary.get("totalInteractions") == null)
        {
            log.warn("无交互数据，无法生成日报：scenicId={}, date={}", scenicId, dateStr);
            return null;
        }

        // 查询关注点分布
        List<FocusPointVO> focusPoints = visitorInteractionMapper.selectIntentDistribution(scenicId, 10);

        // 构建日报实体
        VisitorAnalysis analysis = new VisitorAnalysis();
        analysis.setScenicId(scenicId);
        analysis.setStatsDate(java.sql.Date.valueOf(date));
        analysis.setStatsType("daily");
        analysis.setTotalInteractions(((Number) summary.get("totalInteractions")).intValue());
        analysis.setUniqueVisitors(((Number) summary.get("uniqueVisitors")).intValue());
        analysis.setSatisfactionRate(new BigDecimal(summary.get("avgSatisfaction").toString()));
        analysis.setEmotionDistribution(summary.get("emotionDistribution") != null ? summary.get("emotionDistribution").toString() : "{}");
        try {
            analysis.setFocusPoints(objectMapper.writeValueAsString(focusPoints));
        } catch (Exception e) {
            log.warn("FocusPoints 序列化失败，使用 toString: {}", e.getMessage());
            analysis.setFocusPoints(focusPoints.toString());
        }
        analysis.setAiGenerated(1);
        analysis.setCreateTime(new Date());

        // 按唯一键查询：存在则更新，不存在则新增
        VisitorAnalysis existing = lambdaQuery()
                .eq(VisitorAnalysis::getScenicId, scenicId)
                .eq(VisitorAnalysis::getStatsDate, analysis.getStatsDate())
                .eq(VisitorAnalysis::getStatsType, "daily")
                .one();

        if (existing != null) {
            analysis.setId(existing.getId());
            analysis.setUpdateTime(new Date());
            updateById(analysis);
            log.info("日报分析更新成功：scenicId={}, date={}, id={}", scenicId, dateStr, analysis.getId());
        } else {
            save(analysis);
            log.info("日报分析生成成功：scenicId={}, date={}, id={}", scenicId, dateStr, analysis.getId());
        }
        return analysis;
    }

    /**
     * 获取情感趋势数据
     *
     * @param scenicId 景区ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 情感趋势数据响应
     */
    @Override
    public EmotionTrendResponse getEmotionTrend(Long scenicId, LocalDate startDate, LocalDate endDate) {
        List<EmotionTrendVO> trend = visitorInteractionMapper.selectEmotionTrend(scenicId, startDate.toString(), endDate.toString());
        return EmotionTrendResponse.builder()
                .trend(trend)
                .build();
    }

    /**
     * 获取游客关注点 TOP N
     *
     * @param scenicId 景区ID
     * @param limit 条数限制
     * @return 关注点列表响应
     */
    @Override
    public FocusPointsResponse getFocusPoints(Long scenicId, Integer limit) {
        List<FocusPointVO> points = visitorInteractionMapper.selectIntentDistribution(scenicId, limit != null ? limit : 10);
        return FocusPointsResponse.builder()
                .focusPoints(points)
                .build();
    }

    /**
     * 获取满意度趋势
     *
     * @param scenicId 景区ID
     * @param days 天数
     * @return 满意度趋势数据响应
     */
    @Override
    public SatisfactionTrendResponse getSatisfactionTrend(Long scenicId, Integer days) {
        List<SatisfactionTrendVO> trend = visitorInteractionMapper.selectSatisfactionTrend(scenicId, days != null ? days : 30);
        return SatisfactionTrendResponse.builder()
                .trend(trend)
                .build();
    }
}
