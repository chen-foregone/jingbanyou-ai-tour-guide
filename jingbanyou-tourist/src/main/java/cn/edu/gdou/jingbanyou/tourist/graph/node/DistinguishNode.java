package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.UserSpec;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 问题分类器节点（意图识别 + 语音转文字）
 * 支持纯文本和音频多模态输入
 * - 有音频：多模态模型处理，输出 intent + question
 * - 无音频：纯文本意图分类
 */
@Slf4j
@Component
public class DistinguishNode implements NodeAction {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DistinguishNode(@Qualifier("distinguishChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");
        String history = state.value(HISTORY, String.class).orElse("");

        // 从 State 中获取音频数据（Controller 层注入）
        byte[] audioData = state.value("audioData", byte[].class).orElse(null);

        String modelOutput;
        if (audioData != null) {
            // 多模态路径：音频 + 文字 → intent + question
            log.info("检测到音频输入，使用多模态模型处理");
            String textPrompt = buildMultimodalPrompt(history);

            modelOutput = chatClient.prompt()
                    .user((UserSpec) userSpec -> userSpec
                            .text(textPrompt)
                            .media(new Media(
                                    org.springframework.ai.content.Media.MimeTypeUtils.parseMediaType("audio/wav"),
                                    audioData
                            )))
                    .call()
                    .content();
        } else {
            // 纯文本路径：只做意图分类
            log.info("无音频输入，使用纯文本模型处理");
            modelOutput = chatClient.prompt()
                    .user(userSpec -> userSpec.params(Map.of(
                            QUESTION, question,
                            HISTORY, history
                    )))
                    .call()
                    .content();
        }

        // 解析结果
        String cleaned = modelOutput.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        JsonNode json = objectMapper.readTree(cleaned);
        String intent = json.get("intent").asText();
        String extractedQuestion = json.has("question") ? json.get("question").asText() : question;

        log.info("意图识别结果: intent={}, question={}", intent, extractedQuestion);

        return state.updateState(Map.of(
                INTENT, intent,
                QUESTION, extractedQuestion
        ));
    }

    /**
     * 构建多模态 prompt：指导模型从音频中提取意图和问题
     */
    private String buildMultimodalPrompt(String history) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个景区智能导游助手的意图分类器。\n\n");
        if (history != null && !history.isBlank()) {
            sb.append("历史对话（最近3轮）：\n").append(history).append("\n\n");
        }
        sb.append("请分析音频内容，输出以下JSON格式（不要输出任何其他内容）：\n");
        sb.append("{ \"intent\": \"意图标签\", \"question\": \"从音频中提取的问题文本\" }\n\n");
        sb.append("意图分类说明：\n");
        sb.append("- route_plan：游客想要规划游览路线（包含"怎么走"、"路线"、"从A到B"、"导航"等）\n");
        sb.append("- spot_question：游客询问景区内景点、自然景观、动植物、历史背景、开放时间、门票等\n");
        sb.append("- complex_other：闲聊、投诉、建议、与景区无关的问题\n\n");
        sb.append("只输出JSON，不要输出任何解释。");
        return sb.toString();
    }
}
