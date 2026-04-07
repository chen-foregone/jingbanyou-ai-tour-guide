package cn.edu.gdou.jingbanyou.tourist.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 闲聊兜底节点（非景区问题 / 投诉处理）
 * 任务：处理游客的闲聊、投诉、天气等非景区问题，要求语气友好、响应快
 */
@Component
public class GeneralChatFallbackNode implements NodeAction {

    private final ChatClient chatClient;

    public GeneralChatFallbackNode(@Qualifier("generalChatChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // TODO: 实现通用聊天回退逻辑
        return new HashMap<>();
    }
}
