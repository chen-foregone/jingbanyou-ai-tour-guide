package cn.edu.gdou.jingbanyou.manage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 数据大屏概览 VO
 *
 * @author jingbanyou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationOverviewVO implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 今日总交互次数 */
    private Integer totalInteractions;

    /** 今日独立访客数 */
    private Integer uniqueVisitors;

    /** 平均响应时间(ms) */
    private Integer avgResponseTimeMs;

    /** 平均满意度 */
    private BigDecimal avgSatisfaction;

    /** 文本交互次数 */
    private Integer textCount;

    /** 语音交互次数 */
    private Integer voiceCount;
}
