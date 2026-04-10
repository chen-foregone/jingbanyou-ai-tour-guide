package cn.edu.gdou.jingbanyou.tourist.graph.node;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import cn.edu.gdou.jingbanyou.tourist.service.ChatMemoryService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 对话记忆加载节点
 * <p>
 * 位置：START → MemoryLoader → ProfileLoader → Distinguish
 * <p>
 * 职责：
 * 1. 从 Redis 加载最近 N 轮对话历史
 * 2. 注入到 OverAllState（HISTORY key）
 * 3. 将本轮用户消息添加到记忆
 * <p>
 * 注意：此节点放在 ProfileLoader 之前，保证 DistinguishNode 等后续节点
 * 都能通过 state.value(HISTORY) 获取到历史上下文
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryLoaderNode implements NodeAction {

    /** 从 Redis 加载的最近 N 轮，null=全部 */
    private static final Integer MAX_HISTORY_TURNS = 5;

    private final ChatMemoryService chatMemoryService;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String sessionId = state.value(GraphStateKey.SESSION_ID, String.class).orElse(null);
        String question = state.value(GraphStateKey.QUESTION, String.class).orElse("");

        String history = "";
        if (sessionId != null && !sessionId.isBlank()) {
            // 加载 Redis 中的历史记忆
            history = chatMemoryService.loadHistory(sessionId, MAX_HISTORY_TURNS);
            log.debug("加载对话记忆，sessionId={}, history长度={}", sessionId, history.length());

            // 将本轮用户消息先添加到记忆（等 AI 回复后通过 MemoryUpdaterNode 完成）
            if (!question.isBlank()) {
                chatMemoryService.addUserMessage(sessionId, question);
            }
        } else {
            log.debug("无 sessionId，跳过记忆加载");
        }

        return state.updateState(Map.of(
                GraphStateKey.HISTORY, history
        ));
    }
}
