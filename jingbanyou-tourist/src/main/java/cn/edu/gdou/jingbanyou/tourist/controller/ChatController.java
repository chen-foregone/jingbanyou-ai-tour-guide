package cn.edu.gdou.jingbanyou.tourist.controller;

import cn.edu.gdou.jingbanyou.common.core.domain.R;
import cn.edu.gdou.jingbanyou.ai.service.ChatService;
import cn.edu.gdou.jingbanyou.tourist.dto.ChatRequest;
import cn.edu.gdou.jingbanyou.tourist.dto.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 智能对话 Controller（游客交互侧核心功能）
 * 
 * 功能：支持语音/文本输入，数字人以语音/表情/口型同步方式回答
 */
@Slf4j
@RestController
@RequestMapping("/tourist/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    /**
     * 文本对话（基础功能）
     */
    @PostMapping("/text")
    public R<ChatResponse> textChat(@RequestBody ChatRequest request) {
        log.info("游客文本提问：{}", request.getQuestion());
        ChatResponse response = chatService.chat(request);
        return R.ok(response);
    }

    /**
     * 语音对话（多模态交互）
     */
    @PostMapping("/voice")
    public R<ChatResponse> voiceChat(
            @RequestParam("scenicId") Long scenicId,
            @RequestParam("sessionId") String sessionId,
            @RequestPart("audio") MultipartFile audioFile) {
        
        log.info("游客语音提问：scenicId={}, sessionId={}", scenicId, sessionId);
        // TODO: 调用语音识别 + 大模型 + TTS
        ChatResponse response = chatService.voiceChat(scenicId, sessionId, audioFile);
        return R.ok(response);
    }

    /**
     * 多轮对话上下文查询
     */
    @GetMapping("/history/{sessionId}")
    public R<ChatResponse> getHistory(@PathVariable String sessionId) {
        // TODO: 获取会话历史
        return R.ok();
    }
}
