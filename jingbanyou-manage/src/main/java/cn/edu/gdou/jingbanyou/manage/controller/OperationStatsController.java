package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Log;
import org.springframework.security.access.prepost.PreAuthorize;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.enums.BusinessType;
import cn.edu.gdou.jingbanyou.manage.service.IOperationStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据大屏统计 Controller（赛题要求核心功能）
 *
 * @author jingbanyou
 */
@Slf4j
@PreAuthorize("@ss.hasRole('admin') or @ss.hasRole('scenic_admin')")
@RestController
@RequestMapping("/manage/stats")
public class OperationStatsController extends BaseController
{
    @Autowired
    private IOperationStatsService statsService;

    /**
     * 今日实时概览
     */
    @GetMapping("/today-overview")
    public AjaxResult getTodayOverview(@RequestParam Long scenicId)
    {
        Map<String, Object> overview = statsService.getTodayOverview(scenicId);
        return success(overview);
    }

    /**
     * 本周运营数据统计
     */
    @GetMapping("/weekly-stats")
    public AjaxResult getWeeklyStats(@RequestParam Long scenicId)
    {
        Map<String, Object> stats = statsService.getWeeklyStats(scenicId);
        return success(stats);
    }

    /**
     * 热门问答 TOP10（数据大屏展示）
     */
    @GetMapping("/hot-questions")
    public AjaxResult getHotQuestions(@RequestParam Long scenicId,
                                      @RequestParam(defaultValue = "10") Integer limit)
    {
        Map<String, Object> questions = statsService.getHotQuestions(scenicId, limit);
        return success(questions);
    }

    /**
     * 手动触发统计任务
     */
    @Log(title = "统计数据", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public AjaxResult generateStats(@RequestParam Long scenicId,
                                     @RequestParam String date,
                                     @RequestParam(defaultValue = "daily") String type)
    {
        statsService.generateStats(scenicId, date, type);
        return success();
    }
}
