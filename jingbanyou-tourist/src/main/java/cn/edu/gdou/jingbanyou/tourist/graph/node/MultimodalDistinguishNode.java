package cn.edu.gdou.jingbanyou.tourist.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

/**
 * 意图分类器 — 多模态路径（音频 + 文字）
 * <p>ChatClient defaultSystem 已配置 multimodalPrompt（来自 distinguish.yml）
 * <p>历史由 Advisor 通过 system prompt 自动注入
 */
@Slf4j
@Component
public class MultimodalDistinguishNode extends BaseDistinguishNode {

    public MultimodalDistinguishNode(
            @Qualifier("multimodalDistinguishChatClient") ChatClient chatClient) {
        super(chatClient);
    }

    /**
     * 不使用父类的 apply()，所以此方法不会被调用
     * 但抽象方法要求必须实现
     */
    @Override
    protected String getSystemPrompt() {
        return "";
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");
        String sessionId = state.value(SESSION_ID, String.class).orElse(null);
        byte[] audioData = state.value(AUDIO_DATA, byte[].class).orElse(null);

        // defaultSystem 已由 DistinguishChatClientConfig 注入为 multimodalPrompt
        // 此处只需发送音频 + 简短引导文本
        String modelOutput = invokeWithAudio(audioData, question, sessionId);

        // 解析 JSON 结果
        String cleaned = modelOutput.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        JsonNode json = objectMapper.readTree(cleaned);
        String intent = json.get("intent").asText();
        String extractedQuestion = json.has("question") ? json.get("question").asText() : question;

        log.info("多模态意图识别结果: intent={}, question={}", intent, extractedQuestion);

        return state.updateState(Map.of(
                INTENT, intent,
                QUESTION, extractedQuestion
        ));
    }

    /**
     * 发送音频到多模态模型
     * defaultSystem = multimodalPrompt（描述任务），user = 音频 + 简短引导
     */
    protected String invokeWithAudio(byte[] audioData, String question, String sessionId) {
        String userText = "请分析以下音频内容，提取意图和问题，按 JSON 格式输出。";
        return chatClient.prompt()
                .user(userSpec -> userSpec
                        .text(userText)
                        .media(new org.springframework.ai.content.Media(
                                org.springframework.ai.content.Media.MimeTypeUtils.parseMediaType("audio/wav"),
                                audioData
                        )))
                .advisors(ctx -> ctx.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
    }
}
