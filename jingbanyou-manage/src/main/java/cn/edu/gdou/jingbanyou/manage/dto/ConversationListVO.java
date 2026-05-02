package cn.edu.gdou.jingbanyou.manage.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 会话列表项 VO
 *
 * @author jingbanyou
 */
@Data
public class ConversationListVO {

    private String sessionId;

    private Long scenicId;

    private String title;

    private String firstMessage;

    private String lastMessage;

    private Integer turnCount;

    private String intentType;

    private String emotionDetected;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    private Long durationMs;
}
