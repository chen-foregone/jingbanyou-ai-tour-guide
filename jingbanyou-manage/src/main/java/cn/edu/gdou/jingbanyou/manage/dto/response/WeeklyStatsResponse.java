package cn.edu.gdou.jingbanyou.manage.dto.response;

import cn.edu.gdou.jingbanyou.manage.entity.OperationStats;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 本周运营数据统计响应 VO
 *
 * @author jingbanyou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyStatsResponse implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 每日明细（用于图表展示）
     */
    private List<OperationStats> dailyStats;

    /**
     * 本周汇总（用于卡片展示）
     */
    private WeeklyStatsSummary summary;
}
