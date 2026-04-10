package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 问题分类器节点（意图识别）
 * 任务：仅做 3 分类，输出 JSON，要求极度稳定、零随机、速度快
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
        String intentJSON = chatClient
                .prompt()
                .user(userSpec -> userSpec.params(Map.of(QUESTION, question)))
                .call()
                .content();
        String cleaned = intentJSON.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        String intent = objectMapper.readTree(cleaned).get(INTENT).asText();
        return state.updateState(Map.of(INTENT, intent));
    }
}
