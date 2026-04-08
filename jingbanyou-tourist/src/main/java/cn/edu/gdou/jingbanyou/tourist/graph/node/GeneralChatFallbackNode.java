package cn.edu.gdou.jingbanyou.tourist.graph.node;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 闲聊兜底节点（非景区问题 / 投诉处理）
 * 任务：处理游客的闲聊、投诉、天气等非景区问题，要求语气友好、响应快
 * 注意：暂时禁用，等待配置完善后启用
 */
// @Component  // TODO: 添加 jingbanyou.ai.general-chat 配置后启用
public class GeneralChatFallbackNode implements NodeAction {

    private final ChatClient chatClient;

    public GeneralChatFallbackNode(@Qualifier("generalChatChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String answer = "";
        //1. 获取用户问题
        String question = state.value(GraphStateKey.QUESTION, String.class).orElse("");
        //2. 调用模型回答问题
        while (answer == null || answer.isEmpty()) {
            answer = chatClient.prompt(question).call().content();
        }
        //3. 直接返回结果
        return state.updateState(Map.of(GraphStateKey.ANSWER, answer));
    }
}
