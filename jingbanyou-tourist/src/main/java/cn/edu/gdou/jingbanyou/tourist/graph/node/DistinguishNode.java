package cn.edu.gdou.jingbanyou.tourist.graph.node;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 问题分类器节点（意图识别）
 * 任务：仅做 3 分类，输出 JSON，要求极度稳定、零随机、速度快
 */
@Slf4j
@Component
public class DistinguishNode implements NodeAction {

    private final ChatClient chatClient;

    public DistinguishNode(@Qualifier("distinguishChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 从 state 中获取用户问题和历史对话
        String question = state.value(GraphStateKey.QUESTION.getKey(), String.class)
                .orElseThrow(() -> new IllegalArgumentException("用户问题不能为空"));
        
        String history = state.value(GraphStateKey.HISTORY.getKey(), String.class)
                .orElse("");  // 历史对话可选，默认为空
        
        log.info("开始意图识别 - 问题: {}", question);
        
        // 2. 调用 ChatClient 进行意图分类
        String classification = chatClient.prompt()
                .user(userSpec -> userSpec
                        .text("{history}\n\n{question}")
                        .param("history", history.isEmpty() ? "无历史对话" : history)
                        .param("question", question)
                )
                .call()
                .content();
        
        // 3. 解析分类结果（去除可能的空白字符）
        String intent = classification != null ? classification.trim().toLowerCase() : "complex_other";
        
        // 验证分类结果是否有效
        if (!isValidIntent(intent)) {
            log.warn("无效的分类结果: {}, 使用默认值 complex_other", intent);
            intent = "complex_other";
        }
        
        log.info("意图识别完成 - 分类结果: {}", intent);
        
        // 4. 将结果存入 state
        Map<String, Object> result = new HashMap<>();
        result.put(GraphStateKey.INTENT.getKey(), intent);
        
        return result;
    }
    
    /**
     * 验证意图分类是否有效
     */
    private boolean isValidIntent(String intent) {
        return "route_plan".equals(intent) 
            || "spot_question".equals(intent) 
            || "complex_other".equals(intent);
    }
}
