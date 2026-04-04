package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.dto.HotQuestionVO;
import cn.edu.gdou.jingbanyou.manage.dto.OperationOverviewVO;
import cn.edu.gdou.jingbanyou.manage.entity.OperationStats;
import cn.edu.gdou.jingbanyou.manage.mapper.OperationStatsMapper;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorInteractionMapper;
import cn.edu.gdou.jingbanyou.manage.service.IOperationStatsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    public Map<String, Object> getTodayOverview(Long scenicId)
    {
        Map<String, Object> result = new HashMap<>();
        OperationOverviewVO overview = visitorInteractionMapper.selectTodayOverview(scenicId);
        result.put("overview", overview);
        return result;
    }

    @Override
    public Map<String, Object> getWeeklyStats(Long scenicId)
    {
        Map<String, Object> result = new HashMap<>();
        List<OperationStats> dailyStats = visitorInteractionMapper.selectDailyStats(scenicId, 7);
        
        // 计算本周汇总数据
        Map<String, Object> summary = calculateWeeklySummary(dailyStats);
        
        result.put("dailyStats", dailyStats);  // 每日明细（用于图表展示）
        result.put("summary", summary);         // 本周汇总（用于卡片展示）
        return result;
    }
    
    /**
     * 计算本周汇总数据
     */
    private Map<String, Object> calculateWeeklySummary(List<OperationStats> dailyStats)
    {
        Map<String, Object> summary = new HashMap<>();
        
        if (dailyStats == null || dailyStats.isEmpty())
        {
            summary.put("totalInteractions", 0);
            summary.put("uniqueVisitors", 0);
            summary.put("avgResponseTimeMs", 0);
            summary.put("avgSatisfaction", 0);
            summary.put("textCount", 0);
            summary.put("voiceCount", 0);
            summary.put("daysWithData", 0);
            return summary;
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
        
        summary.put("totalInteractions", totalInteractions);
        summary.put("uniqueVisitors", uniqueVisitors);
        summary.put("avgResponseTimeMs", Math.round(avgResponseTime));
        summary.put("avgSatisfaction", Math.round(avgSatisfaction * 100.0) / 100.0);  // 保留两位小数
        summary.put("textCount", textCount);
        summary.put("voiceCount", voiceCount);
        summary.put("daysWithData", dailyStats.size());
        
        return summary;
    }

    @Override
    public Map<String, Object> getHotQuestions(Long scenicId, Integer limit)
    {
        Map<String, Object> result = new HashMap<>();
        List<HotQuestionVO> hotQuestions = visitorInteractionMapper.selectHotQuestions(scenicId, limit != null ? limit : 10);
        // 设置排名
        for (int i = 0; i < hotQuestions.size(); i++)
        {
            hotQuestions.get(i).setRank(i + 1);
        }
        result.put("hotQuestions", hotQuestions);
        return result;
    }

    @Override
    public void generateStats(Long scenicId, String date, String type)
    {
        log.info("生成统计数据：scenicId={}, date={}, type={}", scenicId, date, type);
        // 查询当日汇总
        OperationStats summary = visitorInteractionMapper.selectDaySummary(scenicId, date);
        if (summary == null)
        {
            log.warn("无交互数据：scenicId={}, date={}", scenicId, date);
            return;
        }
        // 检查是否已存在
        OperationStats existing = getOne(new LambdaQueryWrapper<OperationStats>()
                .eq(OperationStats::getScenicId, scenicId)
                .eq(OperationStats::getStatDate, date));
        if (existing != null)
        {
            // 更新已有记录
            summary.setId(existing.getId());
            updateById(summary);
            log.info("更新统计数据成功：scenicId={}, date={}", scenicId, date);
        }
        else
        {
            // 新增记录
            summary.setCreateTime(new Date());
            save(summary);
            log.info("新增统计数据成功：scenicId={}, date={}", scenicId, date);
        }
    }
}
