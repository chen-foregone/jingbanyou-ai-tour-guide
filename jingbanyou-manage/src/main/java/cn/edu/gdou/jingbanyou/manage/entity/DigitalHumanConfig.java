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
 * AI 数字人形象配置实体对象
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
@ToString
@TableName("manage_digital_human_config")
public class DigitalHumanConfig implements Serializable  {

    private static final long serialVersionUID = 1L;

    /** 配置 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属景区 ID */
    private Long scenicId;

    /** 数字人名称 */
    private String humanName;

    /** 人物头像图片地址 */
    private String avatarImage;

    /** 默认问候语 */
    private String defaultGreeting;

    /** Live2D 模型 .model3.json 可访问地址（前端加载模型必须） */
    private String modelJsonUrl;

    /** 横屏模型显示高度 */
    private BigDecimal landscapeHeight;

    /** 竖屏模型显示高度 */
    private BigDecimal portraitHeight;

    /** X 方向缩放 */
    private BigDecimal scaleX;

    /** X 方向偏移 */
    private BigDecimal offsetX;

    /** Y 方向偏移 */
    private BigDecimal offsetY;

    /** 空闲动作组名 */
    private String idleMotionGroup;

    /** 点击动作组名 */
    private String tapMotionGroup;

    /** TTS 音色代码 */
    private String ttsVoiceCode;

    /** 音色试听音频地址 */
    private String sampleAudioUrl;

    /** 外观扩展配置 (JSON) */
    private String appearanceConfig;

    /** 语音合成扩展参数 (JSON) */
    private String voiceConfig;

    /** 口型同步 0-关闭 1-开启 */
    private Integer lipSync;

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
