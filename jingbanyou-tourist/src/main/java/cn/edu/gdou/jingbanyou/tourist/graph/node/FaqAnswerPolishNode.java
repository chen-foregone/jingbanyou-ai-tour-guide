package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import cn.edu.gdou.jingbanyou.common.config.FaqVectorStoreConfig;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * FAQ 润色节点（高频问题答案优化）
 * 任务：把 FAQ 库的固定标准答案，润色得更亲切、更符合 AI 数字人语气
 * 注意：暂时禁用，等待配置完善后启用
 */
@Component  // TODO: 添加 jingbanyou.ai.faq-polish 配置后启用
public class FaqAnswerPolishNode implements NodeAction {

    private final ChatClient chatClient;
    @Autowired
    private FaqVectorStoreConfig faqVectorStoreConfig;

    public FaqAnswerPolishNode(@Qualifier("faqPolishChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String faqAnswer = state.value(FAQ_ANSWER, String.class).orElse("");
        String question = state.value(QUESTION, String.class).orElse("");

        String answer = chatClient.prompt()
                .user(u -> u
                        .text("游客问题：{question}\nFAQ标准答案：{faq_answer}")
                        .param("question", question)
                        .param("faq_answer", faqAnswer))
                .call()
                .content();

        return state.updateState(Map.of(ANSWER, answer));
    }
}
