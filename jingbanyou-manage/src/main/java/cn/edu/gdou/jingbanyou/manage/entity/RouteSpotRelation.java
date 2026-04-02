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
 * 路线 - 景点关联实体对象（多对多）
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
@ToString
@TableName("manage_route_spot_relation")
public class RouteSpotRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 路线 ID */
    private Long routeId;

    /** 景点 ID */
    private Long spotId;

    /** 游览顺序 (从 1 开始) */
    private Integer visitOrder;

    /** 建议停留时间 (分钟) */
    private Integer stayDuration;

    /** 交通方式 walking/shuttle/bicycle */
    private String transportMethod;

    /** 到下一景点距离 (公里) */
    private Double distanceToNext;

    /** 该景点讲解词 */
    private String explanationText;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
