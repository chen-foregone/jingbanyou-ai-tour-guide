package cn.edu.gdou.jingbanyou.tourist.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 参数缺失引导节点
 * 任务：用自然、友好的语气引导游客补充缺失的路线参数
 */
@Component
public class MissingParamGuideNode implements NodeAction {

    private final ChatClient chatClient;

    public MissingParamGuideNode(@Qualifier("missingParamGuideChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // TODO: 实现参数缺失引导逻辑
        return new HashMap<>();
    }
}
