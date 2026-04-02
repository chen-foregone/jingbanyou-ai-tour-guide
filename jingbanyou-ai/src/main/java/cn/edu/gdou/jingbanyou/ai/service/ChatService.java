package cn.edu.gdou.jingbanyou.ai.service;

import cn.edu.gdou.jingbanyou.tourist.dto.ChatRequest;
import cn.edu.gdou.jingbanyou.tourist.dto.ChatResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 智能对话 Service（AI 中台核心能力）
 * 
 * 功能：整合 RAG 检索、大模型、TTS 等 AI 能力
 */
public interface ChatService {

    /**
     * 文本对话
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 语音对话（ASR + LLM + TTS）
     */
    ChatResponse voiceChat(Long scenicId, String sessionId, MultipartFile audioFile);

    /**
     * 多轮对话（带上下文）
     */
    ChatResponse chatWithContext(ChatRequest request);
}
