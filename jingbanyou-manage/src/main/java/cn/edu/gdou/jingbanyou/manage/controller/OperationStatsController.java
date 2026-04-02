package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.core.domain.R;
import cn.edu.gdou.jingbanyou.manage.service.IOperationStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据大屏统计 Controller（赛题要求核心功能）
 * 
 * 功能：展示服务人次、热门问答、满意度趋势等核心运营数据
 */
@Slf4j
@RestController
@RequestMapping("/manage/stats")
public class OperationStatsController {

    @Autowired
    private IOperationStatsService statsService;

    /**
     * 今日实时概览（数据大屏核心指标）
     */
    @GetMapping("/today-overview")
    public R<Map<String, Object>> getTodayOverview(@RequestParam Long scenicId) {
        Map<String, Object> overview = statsService.getTodayOverview(scenicId);
        return R.ok(overview);
    }

    /**
     * 本周运营数据统计
     */
    @GetMapping("/weekly-stats")
    public R<Map<String, Object>> getWeeklyStats(@RequestParam Long scenicId) {
        Map<String, Object> stats = statsService.getWeeklyStats(scenicId);
        return R.ok(stats);
    }

    /**
     * 热门问答 TOP10（数据大屏展示）
     */
    @GetMapping("/hot-questions")
    public R<Map<String, Object>> getHotQuestions(
            @RequestParam Long scenicId,
            @RequestParam(defaultValue = "10") Integer limit) {
        
        Map<String, Object> questions = statsService.getHotQuestions(scenicId, limit);
        return R.ok(questions);
    }

    /**
     * 设备分布统计
     */
    @GetMapping("/device-distribution")
    public R<Map<String, Object>> getDeviceDistribution(@RequestParam Long scenicId) {
        Map<String, Object> distribution = statsService.getDeviceDistribution(scenicId);
        return R.ok(distribution);
    }

    /**
     * 手动触发统计任务
     */
    @PostMapping("/generate")
    public R<Boolean> generateStats(
            @RequestParam Long scenicId,
            @RequestParam String date,
            @RequestParam(defaultValue = "daily") String type) {
        
        statsService.generateStats(scenicId, date, type);
        return R.ok();
    }
}
