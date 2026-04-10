package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 知识库问答节点（景区详细介绍生成）
 * 任务：基于景区知识库内容，生成详细、准确、有吸引力的景点介绍 / 导览词
 */
@Component
public class ScenicKnowledgeAnswerGeneratorNode implements NodeAction {

    private final ChatClient chatClient;

    public ScenicKnowledgeAnswerGeneratorNode(@Qualifier("scenicKnowledgeChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");
        String retrievedDocs = state.value(RETRIEVED_DOCS, String.class).orElse("");
        String history = state.value(HISTORY, String.class).orElse("无");

        if (retrievedDocs.isBlank()) {
            return state.updateState(Map.of(ANSWER,
                    "这个问题我暂时还不太清楚，建议您咨询景区工作人员。"));
        }

        String content = chatClient.prompt()
                .user(userSpec -> userSpec.params(Map.of(
                        QUESTION, question,
                        RETRIEVED_DOCS, retrievedDocs,
                        HISTORY, history
                )))
                .call()
                .content();

        return state.updateState(Map.of(ANSWER, content));
    }
}
