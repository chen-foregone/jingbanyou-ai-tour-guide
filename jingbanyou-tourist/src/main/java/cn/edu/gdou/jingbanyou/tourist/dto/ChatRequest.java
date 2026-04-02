package cn.edu.gdou.jingbanyou.tourist.dto;

import lombok.Data;

/**
 * 对话请求 DTO
 */
@Data
public class ChatRequest {

    /** 景区 ID */
    private Long scenicId;

    /** 会话 ID（用于多轮对话） */
    private String sessionId;

    /** 用户问题 */
    private String question;

    /** 交互类型 text/voice */
    private String type = "text";

    /** 用户兴趣偏好 */
    private String preference;

    /** 是否使用 RAG 检索 */
    private Boolean useRag = true;

    /** RAG 检索数量 */
    private Integer ragTopK = 3;
}
