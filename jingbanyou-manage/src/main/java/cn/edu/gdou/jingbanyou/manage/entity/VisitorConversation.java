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
 * 游客会话记录
 * 存储会话级元数据，用于历史会话列表和详情展示
 *
 * @author jingbanyou
 */
@Data
@ToString
@TableName("manage_visitor_conversation")
public class VisitorConversation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID（雪花ID）
     */
    @TableId(value = "session_id", type = IdType.INPUT)
    private String sessionId;

    /**
     * 景区ID
     */
    private Long scenicId;

    /**
     * 数字人配置ID
     */
    private Long humanId;

    /**
     * 游客ID
     */
    private String visitorId;

    /**
     * 会话标题（首轮用户消息截取）
     */
    private String title;

    /**
     * 首轮用户消息摘要
     */
    private String firstMessage;

    /**
     * 末轮用户消息摘要
     */
    private String lastMessage;

    /**
     * 对话轮次
     */
    private Integer turnCount;

    /**
     * 主要意图类型
     */
    private String intentType;

    /**
     * 情感检测结果 positive/neutral/negative
     */
    private String emotionDetected;

    /**
     * 情感置信度
     */
    private Double emotionConfidence;

    /**
     * 会话总耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 会话开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /**
     * 会话结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    /**
     * 交互类型 text/voice
     */
    private String interactionType;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
