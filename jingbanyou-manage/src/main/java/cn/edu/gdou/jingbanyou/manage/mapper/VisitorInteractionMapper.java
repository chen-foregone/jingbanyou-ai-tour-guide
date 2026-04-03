package cn.edu.gdou.jingbanyou.manage.mapper;

import cn.edu.gdou.jingbanyou.manage.dto.EmotionTrendVO;
import cn.edu.gdou.jingbanyou.manage.dto.HotQuestionVO;
import cn.edu.gdou.jingbanyou.manage.dto.OperationOverviewVO;
import cn.edu.gdou.jingbanyou.manage.entity.OperationStats;
import cn.edu.gdou.jingbanyou.manage.entity.VisitorInteraction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 游客交互记录 Mapper 接口
 *
 * @author jingbanyou
 */
@Mapper
public interface VisitorInteractionMapper extends BaseMapper<VisitorInteraction>
{
    /**
     * 统计今日概览数据
     *
     * @param scenicId 景区ID
     * @return 概览VO
     */
    OperationOverviewVO selectTodayOverview(@Param("scenicId") Long scenicId);

    /**
     * 查询最近N天的每日统计
     *
     * @param scenicId 景区ID
     * @param days 天数
     * @return 每日统计列表
     */
    List<OperationStats> selectDailyStats(@Param("scenicId") Long scenicId, @Param("days") int days);

    /**
     * 查询热门问题 TOP N
     *
     * @param scenicId 景区ID
     * @param limit 条数
     * @return 热门问题列表
     */
    List<HotQuestionVO> selectHotQuestions(@Param("scenicId") Long scenicId, @Param("limit") int limit);

    /**
     * 查询设备分布
     *
     * @param scenicId 景区ID
     * @return 设备分布列表
     */
    List<Map<String, Object>> selectDeviceDistribution(@Param("scenicId") Long scenicId);

    /**
     * 汇总指定日期的统计数据（用于写入manage_operation_stats）
     *
     * @param scenicId 景区ID
     * @param date 日期字符串 yyyy-MM-dd
     * @return 统计结果
     */
    OperationStats selectDaySummary(@Param("scenicId") Long scenicId, @Param("date") String date);

    /**
     * 查询情感趋势（按日分组）
     *
     * @param scenicId 景区ID
     * @param startDate 起始日期
     * @param endDate 结束日期
     * @return 情感趋势列表
     */
    List<EmotionTrendVO> selectEmotionTrend(@Param("scenicId") Long scenicId,
                                            @Param("startDate") String startDate,
                                            @Param("endDate") String endDate);

    /**
     * 查询意图类型分布（关注点）
     *
     * @param scenicId 景区ID
     * @param limit 条数
     * @return 关注点列表
     */
    List<Map<String, Object>> selectIntentDistribution(@Param("scenicId") Long scenicId, @Param("limit") int limit);

    /**
     * 查询最近N天的满意度趋势
     *
     * @param scenicId 景区ID
     * @param days 天数
     * @return 满意度趋势列表
     */
    List<Map<String, Object>> selectSatisfactionTrend(@Param("scenicId") Long scenicId, @Param("days") int days);

    /**
     * 查询指定日期的交互汇总（用于生成日报）
     *
     * @param scenicId 景区ID
     * @param date 日期字符串 yyyy-MM-dd
     * @return 汇总map
     */
    Map<String, Object> selectDailyReportSummary(@Param("scenicId") Long scenicId, @Param("date") String date);
}
