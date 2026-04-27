package cn.edu.gdou.jingbanyou.tourist.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

/**
 * 意图分类器 — 纯文本路径
 *
 * system prompt 由 textDistinguishChatClient 的 defaultSystem 注入（来自 distinguish.yml）
 * 历史由 ChatMemoryAdvisor 自动注入
 *
 * @author jingbanyou
 */
@Slf4j
@Component
public class TextDistinguishNode implements NodeAction {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TextDistinguishNode(
            @Qualifier("textDistinguishChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");
        String sessionId = state.value(SESSION_ID, String.class).orElse(null);

        log.info("[意图分类-文本] 输入: question={}, sessionId={}", question, sessionId);

        String userText = "当前游客问题：\n" + question;
        StringBuilder hex = new StringBuilder();
        for (byte b : userText.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            hex.append(String.format("%02X ", b));
        }
        log.info("[意图分类-文本] userText UTF-8 bytes: {}", hex);

        String modelOutput = chatClient.prompt()
                .user(userText)
                .advisors(ctx -> ctx.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
        log.info("[意图分类-文本] 模型原始输出: {}", modelOutput);

        // 解析 JSON 结果
        String cleaned = modelOutput.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        JsonNode json = objectMapper.readTree(cleaned);
        String intent = json.get("intent").asText();
        String extractedQuestion = json.has("question") ? json.get("question").asText() : question;

        log.info("[意图分类-文本] 解析结果: intent={}, extractedQuestion={}", intent, extractedQuestion);

        return state.updateState(Map.of(
                INTENT, intent,
                QUESTION, extractedQuestion
        ));
    }
}
