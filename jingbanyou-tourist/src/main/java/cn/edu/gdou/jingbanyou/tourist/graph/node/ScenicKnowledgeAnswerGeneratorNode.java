package cn.edu.gdou.jingbanyou.tourist.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 知识库问答节点（景区详细介绍生成）
 * 任务：基于景区知识库内容，生成详细、准确、有吸引力的景点介绍 / 导览词
 * 注意：暂时禁用，等待配置完善后启用
 */
// @Component  // TODO: 添加 jingbanyou.ai.scenic-knowledge 配置后启用
public class ScenicKnowledgeAnswerGeneratorNode implements NodeAction {

    private final ChatClient chatClient;

    public ScenicKnowledgeAnswerGeneratorNode(@Qualifier("scenicKnowledgeChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // TODO: 实现景区知识答案生成逻辑
        return new HashMap<>();
    }
}
