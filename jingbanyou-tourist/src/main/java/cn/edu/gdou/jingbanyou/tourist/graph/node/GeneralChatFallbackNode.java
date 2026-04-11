package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 闲聊兜底节点（非景区问题 / 投诉处理）
 * 任务：处理游客的闲聊、投诉、天气等非景区问题，要求语气友好、响应快
 *
 * <p>对话历史由 ChatMemoryAdvisor 管理（Redis 热缓存）
 */
@Component
public class GeneralChatFallbackNode implements NodeAction {

    private final ChatClient chatClient;

    public GeneralChatFallbackNode(
            @Qualifier("generalChatChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");
        String sessionId = state.value(SESSION_ID, String.class).orElse(null);

        // Advisor 自动注入历史到 system prompt
        String answer = chatClient.prompt()
                .user(userSpec -> userSpec.params(Map.of(
                        QUESTION, question
                        // HISTORY 已由 Advisor 注入到 system prompt
                )))
                .advisors(ctx -> ctx.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();

        if (answer == null) {
            answer = "抱歉，此问题我无法回答。";
        }

        // Advisor After 自动写入 assistant 消息，无需手动处理
        return state.updateState(Map.of(CHAT_RESPONSE, answer));
    }
}
