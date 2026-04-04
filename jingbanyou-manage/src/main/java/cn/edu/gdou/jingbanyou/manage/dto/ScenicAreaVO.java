package cn.edu.gdou.jingbanyou.manage.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 景区信息返回 VO
 *
 * @author jingbanyou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenicAreaVO implements Serializable
{
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

    /** 景区封面图 */
    private String coverImage;

    /** GPS 纬度 */
    private BigDecimal gpsLatitude;

    /** GPS 经度 */
    private BigDecimal gpsLongitude;

    /** 景区等级 */
    private String starLevel;

    /** 状态 0-禁用 1-启用 */
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
