package cn.edu.gdou.jingbanyou.tourist.graph.node;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * FAQ 润色节点（高频问题答案优化）
 * 任务：把 FAQ 库的固定标准答案，润色得更亲切、更符合 AI 数字人语气
 * 注意：暂时禁用，等待配置完善后启用
 */
// @Component  // TODO: 添加 jingbanyou.ai.faq-polish 配置后启用
public class FaqAnswerPolishNode implements NodeAction {

    private final ChatClient chatClient;

    public FaqAnswerPolishNode(@Qualifier("faqPolishChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        //1. 获取FAQ答案
        String faqAnswer = state.value(GraphStateKey.FAQ_ANSWER, String.class).orElse("");
        //2. 调用模型
        chatClient.prompt()
                .user(userSpec -> userSpec.param(GraphStateKey.FAQ_ANSWER, faqAnswer));
        return new HashMap<>();
    }
}
