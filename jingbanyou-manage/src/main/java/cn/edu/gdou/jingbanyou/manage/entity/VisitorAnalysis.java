package cn.edu.gdou.jingbanyou.manage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 游客感受度分析报告实体对象
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
@ToString
@TableName("manage_visitor_analysis")
public class VisitorAnalysis implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分析 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 景区 ID */
    private Long scenicId;

    /** 统计日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date statsDate;

    /** 统计类型 daily/weekly/monthly */
    private String statsType;

    /** 总交互次数 */
    private Integer totalInteractions;

    /** 独立访客数 */
    private Integer uniqueVisitors;

    /** 平均会话时长 (秒) */
    private Integer avgSessionDuration;

    /** 游客关注点 TOP10(JSON 数组) */
    private String focusPoints;

    /** 情感分布 JSON */
    private String emotionDistribution;

    /** 满意度 (0-100) */
    private BigDecimal satisfactionRate;

    /** 满意度趋势 rising/stable/falling */
    private String satisfactionTrend;

    /** 热门问题 TOP10(JSON) */
    private String hotQuestions;

    /** 投诉话题统计 JSON */
    private String complaintTopics;

    /** 建议汇总 */
    private String suggestionSummary;

    /** 服务优化建议 */
    private String serviceSuggest;

    /** 是否 AI 生成报告 0-人工 1-AI */
    private Integer aiGenerated;

    /** 报告文件地址 */
    private String reportFile;

    /** 创建人 */
    private Long creator;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
