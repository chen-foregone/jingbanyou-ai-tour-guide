package cn.edu.gdou.jingbanyou.tourist.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.util.Map;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

/**
 * 意图分类器 — 多模态路径（音频 + 文字）
 *
 * ChatClient defaultSystem 已配置 multimodalPrompt（来自 distinguish.yml）
 * 历史由 Advisor 通过 system prompt 自动注入
 *
 * @author jingbanyou
 */
@Slf4j
@Component
public class MultimodalDistinguishNode implements NodeAction {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MultimodalDistinguishNode(
            @Qualifier("multimodalDistinguishChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");
        String sessionId = state.value(SESSION_ID, String.class).orElse(null);
        byte[] audioData = state.value(AUDIO_DATA, byte[].class).orElse(null);

        log.info("[多模态意图分类] 输入: question={}, audioData.length={}", question, audioData != null ? audioData.length : 0);
        // defaultSystem 已由 DistinguishChatClientConfig 注入为 multimodalPrompt
        // 此处只需发送音频 + 简短引导文本
        String modelOutput = invokeWithAudio(audioData, question, sessionId);
        log.info("[多模态意图分类] 模型原始输出: {}", modelOutput);

        String intent = "complex_other";
        String extractedQuestion = question;
        try {
            String cleaned = modelOutput.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode json = objectMapper.readTree(cleaned);
            intent = json.get("intent").asText();
            extractedQuestion = json.has("question") ? json.get("question").asText() : question;
        } catch (Exception e) {
            log.warn("[多模态意图分类] JSON 解析失败，使用默认意图，raw={}", modelOutput, e);
        }
        log.info("[多模态意图分类] 解析结果: intent={}, extractedQuestion={}", intent, extractedQuestion);

        return state.updateState(Map.of(
                INTENT, intent,
                QUESTION, extractedQuestion
        ));
    }

    /**
     * 发送音频到多模态模型
     *
     * defaultSystem = multimodalPrompt（描述任务），user = 音频 + 简短引导
     *
     * @param audioData 音频字节数据
     * @param question 文本问题
     * @param sessionId 会话ID
     * @return 模型输出内容
     */
    protected String invokeWithAudio(byte[] audioData, String question, String sessionId) {
        String userText = "请分析以下音频内容，提取意图和问题，按 JSON 格式输出。";
        return chatClient.prompt()
                .user(userSpec -> userSpec
                        .text(userText)
                        .media(Media.builder()
                        .mimeType(MimeType.valueOf("audio/wav"))
                        .data(audioData)
                        .build()))
                .advisors(ctx -> ctx.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
    }
}
