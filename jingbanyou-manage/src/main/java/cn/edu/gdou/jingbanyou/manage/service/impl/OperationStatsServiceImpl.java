package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.common.core.service.TouristSessionService;
import cn.edu.gdou.jingbanyou.manage.dto.HotQuestionVO;
import cn.edu.gdou.jingbanyou.manage.dto.OperationOverviewVO;
import cn.edu.gdou.jingbanyou.manage.dto.response.HotQuestionsResponse;
import cn.edu.gdou.jingbanyou.manage.dto.response.OperationOverviewResponse;
import cn.edu.gdou.jingbanyou.manage.dto.response.WeeklyStatsResponse;
import cn.edu.gdou.jingbanyou.manage.dto.response.WeeklyStatsSummary;
import cn.edu.gdou.jingbanyou.manage.entity.OperationStats;
import cn.edu.gdou.jingbanyou.manage.mapper.OperationStatsMapper;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorInteractionMapper;
import cn.edu.gdou.jingbanyou.manage.service.IOperationStatsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 景区运营数据统计 Service 实现类（数据大屏核心功能）
 *
 * @author jingbanyou
 */
@Slf4j
@Service
public class OperationStatsServiceImpl extends ServiceImpl<OperationStatsMapper, OperationStats> implements IOperationStatsService
{
    @Autowired
    private VisitorInteractionMapper visitorInteractionMapper;

    @Autowired
    private TouristSessionService touristSessionService;

    /**
     * 获取今日实时概览
     *
     * @param scenicId 景区ID
     * @return 今日运营数据概览响应
     */
    @Override
    public OperationOverviewResponse getTodayOverview(Long scenicId) {
        OperationOverviewVO overview = visitorInteractionMapper.selectTodayOverview(scenicId);
        if (overview == null) {
            overview = OperationOverviewVO.builder().build();
        }
        // 注入 Redis ZSet 实时在线人数
        overview.setOnlineVisitors((int) touristSessionService.getOnlineCount(scenicId));
        return OperationOverviewResponse.builder()
                .overview(overview)
                .build();
    }

    /**
     * 获取本周运营数据
     *
     * @param scenicId 景区ID
     * @return 本周统计数据响应
     */
    @Override
    public WeeklyStatsResponse getWeeklyStats(Long scenicId) {
        List<OperationStats> dailyStats = visitorInteractionMapper.selectDailyStats(scenicId, 7);

        // 计算本周汇总数据
        WeeklyStatsSummary summary = calculateWeeklySummary(dailyStats);

        return WeeklyStatsResponse.builder()
                .dailyStats(dailyStats)
                .summary(summary)
                .build();
    }

    /**
     * 计算本周汇总数据
     *
     * @param dailyStats 每日统计数据
     * @return 汇总结果（总交互次数、独立游客数、平均响应时间等）
     */
    private WeeklyStatsSummary calculateWeeklySummary(List<OperationStats> dailyStats) {
        if (dailyStats == null || dailyStats.isEmpty())
        {
            return WeeklyStatsSummary.builder()
                    .totalInteractions(0)
                    .uniqueVisitors(0)
                    .avgResponseTimeMs(0L)
                    .avgSatisfaction(0.0)
                    .textCount(0)
                    .voiceCount(0)
                    .daysWithData(0)
                    .build();
        }

        int totalInteractions = 0;
        int uniqueVisitors = 0;
        long totalResponseTime = 0;
        double totalSatisfaction = 0;
        int satisfactionDays = 0;
        int textCount = 0;
        int voiceCount = 0;

        for (OperationStats stats : dailyStats)
        {
            totalInteractions += stats.getTotalInteractions() != null ? stats.getTotalInteractions() : 0;
            uniqueVisitors += stats.getUniqueVisitors() != null ? stats.getUniqueVisitors() : 0;

            // 加权平均：总响应时间 = 平均值 × 当天交互次数
            if (stats.getAvgResponseTimeMs() != null && stats.getTotalInteractions() != null)
            {
                totalResponseTime += (long) stats.getAvgResponseTimeMs() * stats.getTotalInteractions();
            }

            // 加权平均：总满意度 = 平均值 × 当天交互次数
            if (stats.getAvgSatisfaction() != null && stats.getTotalInteractions() != null)
            {
                totalSatisfaction += stats.getAvgSatisfaction().doubleValue() * stats.getTotalInteractions();
                satisfactionDays += stats.getTotalInteractions();
            }

            textCount += stats.getTextCount() != null ? stats.getTextCount() : 0;
            voiceCount += stats.getVoiceCount() != null ? stats.getVoiceCount() : 0;
        }

        // 计算加权平均值
        double avgResponseTime = totalInteractions > 0 ? (double) totalResponseTime / totalInteractions : 0;
        double avgSatisfaction = satisfactionDays > 0 ? totalSatisfaction / satisfactionDays : 0;

        return WeeklyStatsSummary.builder()
                .totalInteractions(totalInteractions)
                .uniqueVisitors(uniqueVisitors)
                .avgResponseTimeMs(Math.round(avgResponseTime))
                .avgSatisfaction(Math.round(avgSatisfaction * 100.0) / 100.0)  // 保留两位小数
                .textCount(textCount)
                .voiceCount(voiceCount)
                .daysWithData(dailyStats.size())
                .build();
    }

    /**
     * 获取热门问答 TOP N
     *
     * @param scenicId 景区ID
     * @param limit 条数限制
     * @return 热门问答列表响应
     */
    @Override
    public HotQuestionsResponse getHotQuestions(Long scenicId, Integer limit) {
        List<HotQuestionVO> hotQuestions = visitorInteractionMapper.selectHotQuestions(scenicId, limit != null ? limit : 10);
        // 设置排名
        for (int i = 0; i < hotQuestions.size(); i++)
        {
            hotQuestions.get(i).setRank(i + 1);
        }
        return HotQuestionsResponse.builder()
                .hotQuestions(hotQuestions)
                .build();
    }

    /**
     * 生成统计数据
     *
     * @param scenicId 景区ID
     * @param date 统计日期
     * @param type 统计类型
     */
    @Override
    public void generateStats(Long scenicId, LocalDate date, String type) {
        String dateStr = date.toString();
        log.info("生成统计数据：scenicId={}, date={}, type={}", scenicId, dateStr, type);
        // 查询当日汇总
        OperationStats summary = visitorInteractionMapper.selectDaySummary(scenicId, dateStr);
        if (summary == null)
        {
            log.warn("无交互数据：scenicId={}, date={}", scenicId, dateStr);
            return;
        }
        // 检查是否已存在
        OperationStats existing = getOne(new LambdaQueryWrapper<OperationStats>()
                .eq(OperationStats::getScenicId, scenicId)
                .eq(OperationStats::getStatDate, java.sql.Date.valueOf(date)));
        if (existing != null)
        {
            // 更新已有记录
            summary.setId(existing.getId());
            updateById(summary);
            log.info("更新统计数据成功：scenicId={}, date={}", scenicId, dateStr);
        }
        else
        {
            // 新增记录
            summary.setCreateTime(new Date());
            save(summary);
            log.info("新增统计数据成功：scenicId={}, date={}", scenicId, dateStr);
        }
    }
}
