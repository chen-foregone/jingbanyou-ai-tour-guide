package cn.edu.gdou.jingbanyou.tourist.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

/**
 * 意图分类器基类
 * <p>模板方法：apply() 固定流程：调用模型 → 解析 JSON → 写状态
 * <p>注意：意图分类只接收当前用户问题，不注入对话历史（避免 AI 问候语干扰分类结果）
 */
@Slf4j
public abstract class BaseDistinguishNode implements NodeAction {

    protected final ChatClient chatClient;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected BaseDistinguishNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 子类实现：返回 system prompt
     * <p>纯文本：prompt 包含任务描述 + {question} 占位符（ChatClient defaultSystem 已注入）
     * <p>多模态：prompt 包含任务描述（不含 question，音频内容即问题）
     */
    protected abstract String getSystemPrompt();

    /**
     * 模板方法：apply()
     * 固定流程：读状态 → 调用模型 → 解析 JSON → 写状态
     */
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value("question", String.class).orElse("");
        log.info("[意图分类] 输入: question={}", question);

        String userText = "当前游客问题：\n" + question;
        // 打印字节十六进制，确认编码
        StringBuilder hex = new StringBuilder();
        for (byte b : userText.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            hex.append(String.format("%02X ", b));
        }
        log.info("[意图分类] userText UTF-8 bytes: {}", hex);

        String modelOutput = chatClient.prompt()
                .user(userText)
                .call()
                .content();
        log.info("[意图分类] 模型原始输出: {}", modelOutput);

        // 解析 JSON 结果
        String cleaned = modelOutput.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        JsonNode json = objectMapper.readTree(cleaned);
        String intent = json.get("intent").asText();
        String extractedQuestion = json.has("question") ? json.get("question").asText() : question;

        log.info("[意图分类] 解析结果: intent={}, extractedQuestion={}", intent, extractedQuestion);

        return state.updateState(Map.of(
                "intent", intent,
                "question", extractedQuestion
        ));
    }
}
