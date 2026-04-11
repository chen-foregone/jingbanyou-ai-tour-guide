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
 * 知识库问答节点（景区详细介绍生成）
 * 任务：基于景区知识库内容，生成详细、准确、有吸引力的景点介绍 / 导览词
 *
 * <p>对话历史由 ChatMemoryAdvisor 管理（Redis 热缓存）
 */
@Component
public class ScenicKnowledgeAnswerGeneratorNode implements NodeAction {

    private final ChatClient chatClient;

    public ScenicKnowledgeAnswerGeneratorNode(
            @Qualifier("scenicKnowledgeChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");
        String retrievedDocs = state.value(RETRIEVED_DOCS, String.class).orElse("");
        String sessionId = state.value(SESSION_ID, String.class).orElse(null);

        // Advisor 自动注入历史到 system prompt
        String content;
        if (retrievedDocs.isBlank()) {
            content = "这个问题我暂时还不太清楚，建议您咨询景区工作人员。";
        } else {
            content = chatClient.prompt()
                    .user(userSpec -> userSpec.params(Map.of(
                            QUESTION, question,
                            RETRIEVED_DOCS, retrievedDocs
                            // HISTORY 已由 Advisor 注入到 system prompt
                    )))
                    .advisors(ctx -> ctx.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();
        }

        // Advisor After 自动写入 assistant 消息，无需手动处理
        return state.updateState(Map.of(ANSWER, content));
    }
}
