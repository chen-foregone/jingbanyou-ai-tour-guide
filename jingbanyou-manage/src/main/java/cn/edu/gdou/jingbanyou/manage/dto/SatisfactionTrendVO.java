package cn.edu.gdou.jingbanyou.manage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 满意度趋势 VO（单日满意度数据）
 *
 * @author jingbanyou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfactionTrendVO implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 统计日期 */
    private String date;

    /** 当天平均满意度评分 */
    private Double avgScore;
}
