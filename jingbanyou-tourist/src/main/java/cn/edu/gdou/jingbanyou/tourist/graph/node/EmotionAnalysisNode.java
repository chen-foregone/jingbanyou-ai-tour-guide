package cn.edu.gdou.jingbanyou.tourist.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

/**
 * 情感分析节点
 *
 * 异步分析游客问题的情感倾向（positive/neutral/negative）
 * 分析结果写入 state，供 recordSingleTurn 时写入 MySQL
 *
 * @author jingbanyou
 */
@Slf4j
@Component
public class EmotionAnalysisNode implements NodeAction {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EmotionAnalysisNode(
            @Qualifier("emotionAnalysisChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");

        log.info("[情感分析] 输入: question={}", question);

        if (question == null || question.isBlank()) {
            return state.updateState(Map.of(
                    EMOTION_DETECTED, "neutral",
                    EMOTION_CONFIDENCE, 0.0
            ));
        }

        try {
            String modelOutput = chatClient.prompt()
                    .user("当前游客问题：\n" + question)
                    .call()
                    .content();

            log.info("[情感分析] 模型原始输出: {}", modelOutput);

            String cleaned = modelOutput.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode json = objectMapper.readTree(cleaned);

            String emotion = json.has("emotion")
                    ? json.get("emotion").asText()
                    : "neutral";
            double confidence = json.has("confidence")
                    ? json.get("confidence").asDouble()
                    : 0.0;

            // 简单的情感值校验
            if (!emotion.equals("positive") && !emotion.equals("neutral") && !emotion.equals("negative")) {
                emotion = "neutral";
            }

            log.info("[情感分析] 结果: emotion={}, confidence={}", emotion, confidence);

            return state.updateState(Map.of(
                    EMOTION_DETECTED, emotion,
                    EMOTION_CONFIDENCE, confidence
            ));

        } catch (Exception e) {
            log.warn("[情感分析] 失败，使用默认值 neutral，question={}", question, e);
            return state.updateState(Map.of(
                    EMOTION_DETECTED, "neutral",
                    EMOTION_CONFIDENCE, 0.0
            ));
        }
    }
}
