package cn.edu.gdou.jingbanyou.manage.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 本周运营数据汇总 VO（用于大屏卡片展示）
 *
 * @author jingbanyou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyStatsSummary implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 总交互次数 */
    private Integer totalInteractions;

    /** 独立游客数 */
    private Integer uniqueVisitors;

    /** 平均响应时间（毫秒） */
    private Long avgResponseTimeMs;

    /** 平均满意度（保留两位小数） */
    private Double avgSatisfaction;

    /** 文本交互次数 */
    private Integer textCount;

    /** 语音交互次数 */
    private Integer voiceCount;

    /** 有数据的天数 */
    private Integer daysWithData;
}
