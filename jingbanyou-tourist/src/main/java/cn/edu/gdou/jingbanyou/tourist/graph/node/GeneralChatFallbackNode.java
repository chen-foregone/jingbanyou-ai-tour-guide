package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 闲聊兜底节点（非景区问题 / 投诉处理）
 * 任务：处理游客的闲聊、投诉、天气等非景区问题，要求语气友好、响应快
 * 注意：暂时禁用，等待配置完善后启用
 */
@Component
public class GeneralChatFallbackNode implements NodeAction {

    private final ChatClient chatClient;

    public GeneralChatFallbackNode(@Qualifier("generalChatChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");
        String answer = chatClient.prompt()
                .user(userSpec -> userSpec.params(Map.of("question", question)))
                .call()
                .content();
        if (answer == null) {
            answer = "抱歉，此问题我无法回答。";
        }
        return state.updateState(Map.of(ANSWER, answer));
    }
}
