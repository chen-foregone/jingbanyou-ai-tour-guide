package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.core.domain.R;
import cn.edu.gdou.jingbanyou.manage.entity.VisitorAnalysis;
import cn.edu.gdou.jingbanyou.manage.service.IVisitorAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 游客感受度分析 Controller（赛题要求核心功能）
 * 
 * 功能：分析交互记录，生成游客关注点、情感趋势报告及服务建议
 */
@Slf4j
@RestController
@RequestMapping("/manage/analysis")
public class VisitorAnalysisController {

    @Autowired
    private IVisitorAnalysisService analysisService;

    /**
     * 获取情感趋势数据
     */
    @GetMapping("/emotion-trend")
    public R<Map<String, Object>> getEmotionTrend(
            @RequestParam Long scenicId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") String startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") String endDate) {
        
        Map<String, Object> trend = analysisService.getEmotionTrend(scenicId, startDate, endDate);
        return R.ok(trend);
    }

    /**
     * 获取游客关注点 TOP10
     */
    @GetMapping("/focus-points")
    public R<Map<String, Object>> getFocusPoints(
            @RequestParam Long scenicId,
            @RequestParam(defaultValue = "10") Integer limit) {
        
        Map<String, Object> points = analysisService.getFocusPoints(scenicId, limit);
        return R.ok(points);
    }

    /**
     * 获取满意度趋势
     */
    @GetMapping("/satisfaction-trend")
    public R<Map<String, Object>> getSatisfactionTrend(
            @RequestParam Long scenicId,
            @RequestParam(defaultValue = "30") Integer days) {
        
        Map<String, Object> trend = analysisService.getSatisfactionTrend(scenicId, days);
        return R.ok(trend);
    }

    /**
     * 生成日报分析（AI 自动生成）
     */
    @PostMapping("/generate-daily-report")
    public R<VisitorAnalysis> generateDailyReport(
            @RequestParam Long scenicId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        
        VisitorAnalysis report = analysisService.generateDailyReport(scenicId, date);
        return R.ok(report);
    }

    /**
     * 查看历史分析报告
     */
    @GetMapping("/{id}")
    public R<VisitorAnalysis> getReport(@PathVariable Long id) {
        VisitorAnalysis report = analysisService.getById(id);
        return R.ok(report);
    }
}
