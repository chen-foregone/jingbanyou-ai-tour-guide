package cn.edu.gdou.jingbanyou.manage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 运营统计日汇总对象 manage_operation_stats
 *
 * @author jingbanyou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manage_operation_stats")
public class OperationStats implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 景区ID */
    private Long scenicId;

    /** 统计日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date statDate;

    /** 总交互次数 */
    private Integer totalInteractions;

    /** 独立访客数 */
    private Integer uniqueVisitors;

    /** 平均响应时间(ms) */
    private Integer avgResponseTimeMs;

    /** 平均满意度 */
    private BigDecimal avgSatisfaction;

    /** 文本交互次数 */
    private Integer textCount;

    /** 语音交互次数 */
    private Integer voiceCount;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
