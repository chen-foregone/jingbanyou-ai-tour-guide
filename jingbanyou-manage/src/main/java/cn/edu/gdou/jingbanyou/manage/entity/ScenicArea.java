package cn.edu.gdou.jingbanyou.manage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 景区基础信息实体对象
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
@ToString
@TableName(value = "manage_scenic_area", autoResultMap = true)
public class ScenicArea implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 景区 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 景区名称 */
    private String scenicName;

    /** 景区地址 */
    private String scenicAddress;

    /** 景区总体介绍 */
    private String scenicDesc;

    /** 开放时间 */
    private String openTime;

    /** 门票信息 */
    private String ticketInfo;

    /** 联系电话 */
    private String contactPhone;

    /** 官方网站 */
    private String officialWebsite;

    /** 景区封面图 */
    private String coverImage;

    /** 首屏功能亮点文案数组，如 ["智能导览","路线规划","语音问答"] */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> topFeatures;

    /** 快捷提问文案数组，如 ["第一次来怎么安排路线？","几点开门？"] */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> quickPrompts;

    /** 景区等级 (如 5A、4A) */
    private String starLevel;

    /** 状态 0-禁用 1-启用 */
    private Integer status;

    /** 排序权重 */
    private Integer sort;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
