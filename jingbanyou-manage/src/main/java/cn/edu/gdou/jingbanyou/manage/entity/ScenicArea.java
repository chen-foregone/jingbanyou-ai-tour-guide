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
 * 景区基础信息实体对象
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
@ToString
@TableName("manage_scenic_area")
public class ScenicArea implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 景区 ID */
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

    /** GPS 纬度 */
    private BigDecimal gpsLatitude;

    /** GPS 经度 */
    private BigDecimal gpsLongitude;

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
