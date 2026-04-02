package cn.edu.gdou.jingbanyou.tourist.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 对话响应 DTO
 */
@Data
@Builder
public class ChatResponse {

    /** AI 回答内容 */
    private String answer;

    /** 语音 URL（TTS 生成） */
    private String audioUrl;

    /** 数字人视频 URL */
    private String videoUrl;

    /** 引用的知识文档 */
    private String[] referencedDocs;

    /** 情感分析结果 */
    private String emotion;

    /** 推荐景点 ID 列表 */
    private Long[] recommendedSpots;

    /** 响应耗时 (ms) */
    private Long responseTimeMs;

    /** 使用的模型 */
    private String modelUsed;

    /** 是否使用了 RAG */
    private Boolean ragUsed;
}
