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
 * 个性化游览路线实体对象
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
@ToString
@TableName("manage_tour_route")
public class TourRoute implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 路线 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属景区 ID */
    private Long scenicId;

    /** 路线名称 */
    private String routeName;

    /** 路线类型 历史/自然/亲子/全景/小众/无障碍 */
    private String routeType;

    /** 路线主题 */
    private String routeTheme;

    /** 预计耗时 (分钟) */
    private Integer routeTime;

    /** 路线总长度 (公里) */
    private Double routeDistance;

    /** 路线介绍 */
    private String routeDesc;

    /** 适宜人群 */
    private String suitableCrowd;

    /** 最佳游览季节 */
    private String bestSeason;

    /** 难度等级 1-简单 2-中等 3-困难 */
    private Integer difficultyLevel;

    /** 路线封面图 */
    private String coverImage;

    /** 路线地图 */
    private String routeMap;

    /** 排序 */
    private Integer sort;

    /** 浏览次数 */
    private Integer viewCount;

    /** 预订次数 */
    private Integer bookCount;

    /** 评分 (0-5) */
    private Double rating;

    /** 状态 0-禁用 1-启用 */
    private Integer status;

    /** 创建人 */
    private Long creator;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
