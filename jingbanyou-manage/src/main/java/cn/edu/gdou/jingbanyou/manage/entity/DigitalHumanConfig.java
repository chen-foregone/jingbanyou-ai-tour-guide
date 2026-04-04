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
 * AI 数字人形象配置实体对象
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
@ToString
@TableName("manage_digital_human_config")
public class DigitalHumanConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属景区 ID */
    private Long scenicId;

    /** 数字人名称 */
    private String humanName;

    /** 外观配置 (2D驱动参数JSON，如模型选择、增强开关等) */
    private String appearanceConfig;

    /** 语音合成参数 (JSON) */
    private String voiceConfig;

    /** 口型同步 0-关闭 1-开启 */
    private Integer lipSync;

    /** 默认问候语 */
    private String defaultGreeting;

    /** 人物图片地址（2D驱动源图） */
    private String avatarImage;

    /** 是否默认 0-否 1-是 */
    private Integer isDefault;

    /** 配置人 */
    private Long creator;

    /** 更新人 */
    private Long updater;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
