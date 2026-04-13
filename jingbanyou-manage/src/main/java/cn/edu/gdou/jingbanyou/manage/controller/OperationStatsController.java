package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Log;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.enums.BusinessType;
import cn.edu.gdou.jingbanyou.manage.service.IOperationStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 运营数据统计
 *
 * @author jingbanyou
 */
@Slf4j
@PreAuthorize("@ss.hasRole('admin') or @ss.hasRole('scenic_admin')")
@RestController
@RequestMapping("/manage/stats")
public class OperationStatsController extends BaseController {

    @Autowired
    private IOperationStatsService statsService;

    /**
     * 今日实时概览
     * @param scenicId 景区ID
     * @return 当日运营数据
     */
    @GetMapping("/today-overview")
    public AjaxResult getTodayOverview(@RequestParam Long scenicId) {
        Map<String, Object> overview = statsService.getTodayOverview(scenicId);
        return success(overview);
    }

    /**
     * 本周运营数据统计
     * @param scenicId 景区ID
     * @return 本周统计数据
     */
    @GetMapping("/weekly-stats")
    public AjaxResult getWeeklyStats(@RequestParam Long scenicId) {
        Map<String, Object> stats = statsService.getWeeklyStats(scenicId);
        return success(stats);
    }

    /**
     * 热门问答TOP榜单
     * @param scenicId 景区ID
     * @param limit 返回数量
     * @return 热门问答列表
     */
    @GetMapping("/hot-questions")
    public AjaxResult getHotQuestions(@RequestParam Long scenicId,
                                      @RequestParam(defaultValue = "10") Integer limit) {
        Map<String, Object> questions = statsService.getHotQuestions(scenicId, limit);
        return success(questions);
    }

    /**
     * 手动触发统计任务
     * @param scenicId 景区ID
     * @param date 统计日期
     * @param type 统计类型
     */
    @Log(title = "统计数据", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public AjaxResult generateStats(@RequestParam Long scenicId,
                                     @RequestParam String date,
                                     @RequestParam(defaultValue = "daily") String type) {
        statsService.generateStats(scenicId, date, type);
        return success();
    }
}
