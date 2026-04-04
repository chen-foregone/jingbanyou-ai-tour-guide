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
        result.put("dailyStats", dailyStats);
        return result;
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
