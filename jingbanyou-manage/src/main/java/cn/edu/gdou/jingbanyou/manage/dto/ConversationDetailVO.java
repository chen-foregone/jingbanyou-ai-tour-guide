package cn.edu.gdou.jingbanyou.manage.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 会话详情 VO
 *
 * @author jingbanyou
 */
@Data
public class ConversationDetailVO {

    private String sessionId;

    private String visitorId;

    private Long scenicId;

    private Long humanId;

    private String title;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    private String intentType;

    private String emotionDetected;

    private Double emotionConfidence;

    private Long durationMs;

    private Integer turnCount;

    private String interactionType;

    /**
     * 对话轮次详情
     */
    private List<ConversationTurn> turns;

    @Data
    public static class ConversationTurn {

        private String role;

        private String content;

        private String time;
    }
}
