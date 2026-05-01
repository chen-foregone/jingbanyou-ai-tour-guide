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
 * 游客 - 数字人交互记录实体对象
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
@ToString
@TableName("manage_visitor_interaction")
public class VisitorInteraction implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 交互 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 景区 ID */
    private Long scenicId;

    /** 使用的数字人 ID */
    private Long humanId;

    /** 会话 ID(用于追踪多轮对话) */
    private String sessionId;

    /** 游客标识 (OpenID/匿名 UUID) */
    private String visitorId;

    /** 交互类型 text/voice/video */
    private String interactionType;

    /** 游客问题 */
    private String userQuestion;

    /** 用户语音 URL(如果是语音交互) */
    private String userVoiceUrl;

    /** 数字人回答 */
    private String aiAnswer;

    /** AI 语音 URL */
    private String aiVoiceUrl;

    /** AI 数字人视频 URL */
    private String aiVideoUrl;

    /** 识别到的用户情感 positive/neutral/negative */
    private String emotionDetected;

    /** 情感置信度 */
    private Double emotionConfidence;

    /** 意图类型 inquiry/complaint/suggestion/praise */
    private String intentType;

    /** 是否使用 RAG 检索 0-否 1-是 */
    private Integer ragUsed;

    /** 引用的知识文档 (JSON 数组) */
    private String ragDocs;

    /** 响应耗时 (毫秒) */
    private Integer responseTimeMs;

    /** 消耗 Token 数量 */
    private Integer tokensUsed;

    /** 使用的 AI 模型 */
    private String modelUsed;

    /** 用户评分 1-5 */
    private Integer feedbackScore;

    /** 用户反馈 */
    private String feedbackText;

    /** 设备类型 mobile/desktop/kiosk */
    private String deviceType;

    /** IP 地址 */
    private String ipAddress;

    /** 地理位置信息 */
    private String locationInfo;

    /**
     * 本轮在会话中的序号（从0开始）
     */
    private Integer turnIndex = 0;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
