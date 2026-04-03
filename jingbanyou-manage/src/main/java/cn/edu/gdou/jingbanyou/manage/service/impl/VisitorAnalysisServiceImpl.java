package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.dto.EmotionTrendVO;
import cn.edu.gdou.jingbanyou.manage.entity.VisitorAnalysis;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorAnalysisMapper;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorInteractionMapper;
import cn.edu.gdou.jingbanyou.manage.service.IVisitorAnalysisService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游客感受度分析 Service 实现类（赛题要求核心功能）
 *
 * @author jingbanyou
 */
@Slf4j
@Service
public class VisitorAnalysisServiceImpl extends ServiceImpl<VisitorAnalysisMapper, VisitorAnalysis> implements IVisitorAnalysisService
{
    @Autowired
    private VisitorInteractionMapper visitorInteractionMapper;

    @Override
    public VisitorAnalysis generateDailyReport(Long scenicId, LocalDate date)
    {
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
        List<Map<String, Object>> focusPoints = visitorInteractionMapper.selectIntentDistribution(scenicId, 10);

        // 构建日报实体
        VisitorAnalysis analysis = new VisitorAnalysis();
        analysis.setScenicId(scenicId);
        analysis.setStatsDate(java.sql.Date.valueOf(date));
        analysis.setStatsType("daily");
        analysis.setTotalInteractions(((Number) summary.get("totalInteractions")).intValue());
        analysis.setUniqueVisitors(((Number) summary.get("uniqueVisitors")).intValue());
        analysis.setSatisfactionRate(new BigDecimal(summary.get("avgSatisfaction").toString()));
        analysis.setEmotionDistribution(summary.get("emotionDistribution") != null ? summary.get("emotionDistribution").toString() : "{}");
        analysis.setFocusPoints(focusPoints.toString());
        analysis.setAiGenerated(1);
        analysis.setCreateTime(new Date());

        // 保存
        save(analysis);
        log.info("日报分析生成成功：scenicId={}, date={}, id={}", scenicId, dateStr, analysis.getId());
        return analysis;
    }

    @Override
    public Map<String, Object> getEmotionTrend(Long scenicId, String startDate, String endDate)
    {
        Map<String, Object> result = new HashMap<>();
        List<EmotionTrendVO> trend = visitorInteractionMapper.selectEmotionTrend(scenicId, startDate, endDate);
        result.put("trend", trend);
        return result;
    }

    @Override
    public Map<String, Object> getFocusPoints(Long scenicId, Integer limit)
    {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> points = visitorInteractionMapper.selectIntentDistribution(scenicId, limit != null ? limit : 10);
        result.put("focusPoints", points);
        return result;
    }

    @Override
    public Map<String, Object> getSatisfactionTrend(Long scenicId, Integer days)
    {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> trend = visitorInteractionMapper.selectSatisfactionTrend(scenicId, days != null ? days : 30);
        result.put("trend", trend);
        return result;
    }
}
