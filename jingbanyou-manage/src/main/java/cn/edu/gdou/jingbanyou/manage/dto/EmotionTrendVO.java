package cn.edu.gdou.jingbanyou.manage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 情感趋势 VO
 *
 * @author jingbanyou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionTrendVO implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 日期 */
    private String date;

    /** 正面情感数 */
    private Integer positiveCount;

    /** 中性情感数 */
    private Integer neutralCount;

    /** 负面情感数 */
    private Integer negativeCount;
}
