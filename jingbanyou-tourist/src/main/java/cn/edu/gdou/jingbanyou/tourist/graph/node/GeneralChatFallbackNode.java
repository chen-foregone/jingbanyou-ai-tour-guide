package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 闲聊兜底节点（非景区问题 / 投诉处理）
 *
 * 只存储用户消息上下文到 RETRIEVED_DOCS，不调用 LLM
 * LLM 流式调用由 StreamingAnswerService 负责
 *
 * @author jingbanyou
 */
@Slf4j
@Component
public class GeneralChatFallbackNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");

        log.info("[闲聊兜底] 输入: question={}", question);
        String userText = "游客消息：" + question;
        log.info("[闲聊兜底] RETRIEVED_DOCS={}", userText);

        // 显式清空路由/答案状态，防止跨请求泄漏
        return state.updateState(Map.of(
                RETRIEVED_DOCS, userText,
                ANSWER, "",
                ROUTE_STATUS, "",
                GUIDE_MESSAGE, ""
        ));
    }
}
