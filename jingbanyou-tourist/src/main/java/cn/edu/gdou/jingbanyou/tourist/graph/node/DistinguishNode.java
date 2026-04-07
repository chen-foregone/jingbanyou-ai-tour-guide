package cn.edu.gdou.jingbanyou.tourist.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 问题分类器节点（意图识别）
 * 任务：仅做 3 分类，输出 JSON，要求极度稳定、零随机、速度快
 */
@Component
public class DistinguishNode implements NodeAction {

    private final ChatClient chatClient;

    public DistinguishNode(@Qualifier("distinguishChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // TODO: 实现意图分类逻辑
        // 从state中获取question和history
        // 调用chatClient进行分类
        // 将分类结果放回state
        return new HashMap<>();
    }
}
