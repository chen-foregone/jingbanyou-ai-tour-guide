package cn.edu.gdou.jingbanyou.manage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.Date;

/**
 * 景区景点信息实体对象
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
@ToString
@TableName("scenic_spot")
public class ScenicSpot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 景点 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属景区 ID */
    private Long scenicId;

    /** 景点名称 */
    private String spotName;

    /** 景点类型 历史/自然/人文/娱乐 */
    private String spotType;

    /** 景点位置 */
    private String spotLocation;

    /** 景点详细介绍 (文史/特色) */
    private String spotDesc;

    /** 语音讲解文件地址 */
    private String audioGuide;

    /** 视频讲解文件地址 */
    private String videoGuide;

    /** 景点封面图 */
    private String coverImage;

    /** 图片集 (JSON 数组) */
    private String galleryImages;

    /** 建议游览时长 (分钟) */
    private Integer visitDuration;

    /** 适宜游览季节 */
    private String suitableSeason;

    /** 排序权重 */
    private Integer sort;

    /** 状态 0-禁用 1-启用 */
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
