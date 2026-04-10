package cn.edu.gdou.jingbanyou.tourist.graph.node;

import cn.edu.gdou.jingbanyou.manage.entity.VisitorInteraction;
import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import cn.edu.gdou.jingbanyou.tourist.service.ChatMemoryService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 对话记忆更新节点（Graph 出口节点）
 * <p>
 * 位置：ProfileUpdaterNode → MemoryUpdaterNode → END
 * <p>
 * 职责：
 * 1. 将 AI 回复添加到 Redis ChatMemory
 * 2. 将本轮交互记录写入 MySQL VisitorInteraction
 * <p>
 * 注意：此节点是 Graph 的最终出口，保证每轮对话的记忆都被持久化
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryUpdaterNode implements NodeAction {

    private final ChatMemoryService chatMemoryService;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String sessionId = state.value(GraphStateKey.SESSION_ID, String.class).orElse(null);
        String visitorId = state.value(GraphStateKey.VISITOR_ID, String.class).orElse(null);
        Long scenicId = state.value(GraphStateKey.SCENIC_ID, Long.class).orElse(null);
        String question = state.value(GraphStateKey.QUESTION, String.class).orElse("");
        String answer = state.value(GraphStateKey.ANSWER, String.class)
                .or(() -> state.value(GraphStateKey.CHAT_RESPONSE, String.class))
                .or(() -> state.value(GraphStateKey.ROUTE_DESCRIPTION, String.class))
                .orElse("");

        if (sessionId != null && !sessionId.isBlank()) {
            // 1. 将 AI 回复添加到 Redis 记忆
            if (!answer.isBlank()) {
                chatMemoryService.addAssistantMessage(sessionId, answer);
            }

            // 2. 写入 MySQL 交互记录
            VisitorInteraction record = new VisitorInteraction();
            record.setSessionId(sessionId);
            record.setScenicId(scenicId);
            record.setVisitorId(visitorId);
            record.setUserQuestion(question);
            record.setAiAnswer(answer);
            record.setInteractionType("text");
            record.setIntentType(state.value(GraphStateKey.INTENT, String.class).orElse(null));

            chatMemoryService.persistInteraction(record);
            log.debug("对话记忆已更新并持久化，sessionId={}", sessionId);
        }

        return state.updateState(Map.of()); // 无需写回额外状态
    }
}
