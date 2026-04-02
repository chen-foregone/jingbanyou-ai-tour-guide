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

    /** 数字人类型 2D/3D */
    private String humanType;

    /** 外观配置 (模型地址/参数/材质，JSON) */
    private String appearanceConfig;

    /** 服装配置 (多套服装，JSON) */
    private String costumeConfig;

    /** 语音合成参数 (JSON) */
    private String voiceConfig;

    /** 发型描述 */
    private String hairstyle;

    /** 配饰描述 */
    private String accessory;

    /** 口型同步 0-关闭 1-开启 */
    private Integer lipSync;

    /** 情感表情 0-关闭 1-开启 */
    private Integer emotionExpr;

    /** 手势动作 0-关闭 1-开启 */
    private Integer gestureAction;

    /** 默认问候语 */
    private String defaultGreeting;

    /** 头像预览图 */
    private String avatarImage;

    /** 演示视频 */
    private String previewVideo;

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
