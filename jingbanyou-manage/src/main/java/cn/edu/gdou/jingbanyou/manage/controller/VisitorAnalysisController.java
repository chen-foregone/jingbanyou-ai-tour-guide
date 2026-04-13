package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Log;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.enums.BusinessType;
import cn.edu.gdou.jingbanyou.manage.entity.VisitorAnalysis;
import cn.edu.gdou.jingbanyou.manage.service.IVisitorAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 游客感受度分析
 *
 * @author jingbanyou
 */
@Slf4j
@PreAuthorize("@ss.hasRole('admin') or @ss.hasRole('scenic_admin')")
@RestController
@RequestMapping("/manage/analysis")
public class VisitorAnalysisController extends BaseController {

    @Autowired
    private IVisitorAnalysisService analysisService;

    /**
     * 获取情感趋势数据
     * @param scenicId 景区ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 情感趋势数据
     */
    @GetMapping("/emotion-trend")
    public AjaxResult getEmotionTrend(@RequestParam Long scenicId,
                                       @RequestParam String startDate,
                                       @RequestParam String endDate) {
        Map<String, Object> trend = analysisService.getEmotionTrend(scenicId, startDate, endDate);
        return success(trend);
    }

    /**
     * 获取游客关注点TOP榜单
     * @param scenicId 景区ID
     * @param limit 返回数量
     * @return 关注点列表
     */
    @GetMapping("/focus-points")
    public AjaxResult getFocusPoints(@RequestParam Long scenicId,
                                      @RequestParam(defaultValue = "10") Integer limit) {
        Map<String, Object> points = analysisService.getFocusPoints(scenicId, limit);
        return success(points);
    }

    /**
     * 获取满意度趋势
     * @param scenicId 景区ID
     * @param days 统计天数
     * @return 满意度趋势数据
     */
    @GetMapping("/satisfaction-trend")
    public AjaxResult getSatisfactionTrend(@RequestParam Long scenicId,
                                            @RequestParam(defaultValue = "30") Integer days) {
        Map<String, Object> trend = analysisService.getSatisfactionTrend(scenicId, days);
        return success(trend);
    }

    /**
     * 生成日报分析
     * @param scenicId 景区ID
     * @param date 日期
     * @return 分析报告
     */
    @Log(title = "游客分析", businessType = BusinessType.INSERT)
    @PostMapping("/generate-daily-report")
    public AjaxResult generateDailyReport(@RequestParam Long scenicId,
                                           @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        VisitorAnalysis report = analysisService.generateDailyReport(scenicId, date);
        return success(report);
    }

    /** 查看历史分析报告 */
    @GetMapping("/{id}")
    public AjaxResult getReport(@PathVariable Long id) {
        return success(analysisService.getById(id));
    }
}
