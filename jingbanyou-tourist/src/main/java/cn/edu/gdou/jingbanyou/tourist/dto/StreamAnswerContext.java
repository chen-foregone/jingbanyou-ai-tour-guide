package cn.edu.gdou.jingbanyou.tourist.dto;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;

import java.util.List;

/**
 * 流式答案上下文 DTO
 * 将 streamAnswer 的 12 个参数封装为上下文对象
 *
 * @author jingbanyou
 */
public record StreamAnswerContext(
        String sessionId,
        Long scenicId,
        Long humanId,
        String visitorId,
        String intent,
        /**
         * 原始用户问题（用于存储到 ChatMemory，避免 RAG 检索内容泄漏）
         */
        String originalQuestion,
        /**
         * RAG 检索内容（仅用于 LLM 回答，不写入 ChatMemory）
         */
        String retrievedDocs,
        DigitalHumanConfig digitalHuman,
        List<?> rawRoutes,
        String intentType,
        int graphCostMs,
        long startTimestamp
) {
}
