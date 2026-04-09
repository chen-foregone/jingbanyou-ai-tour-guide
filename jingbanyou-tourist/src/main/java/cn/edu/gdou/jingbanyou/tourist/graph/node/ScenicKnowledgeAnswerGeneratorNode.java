package cn.edu.gdou.jingbanyou.tourist.graph.node;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
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
        String question = state.value(GraphStateKey.QUESTION, String.class).orElse("");
        String retrievedDocs = state.value(GraphStateKey.RETRIEVED_DOCS, String.class).orElse("");
        String history = state.value(GraphStateKey.HISTORY, String.class).orElse("无");

        // 无检索结果时直接返回兜底回答
        if (retrievedDocs.isBlank()) {
            return state.updateState(Map.of(GraphStateKey.ANSWER,
                    "这个问题我暂时还不太清楚，建议您咨询景区工作人员。"));
        }

        // 调用模型
        String content = chatClient.prompt()
                .user(userSpec -> userSpec.params(Map.of(
                        GraphStateKey.QUESTION, question,
                        GraphStateKey.RETRIEVED_DOCS, retrievedDocs,
                        GraphStateKey.HISTORY, history
                )))
                .call()
                .content();

        return state.updateState(Map.of(GraphStateKey.ANSWER, content));
    }
}
