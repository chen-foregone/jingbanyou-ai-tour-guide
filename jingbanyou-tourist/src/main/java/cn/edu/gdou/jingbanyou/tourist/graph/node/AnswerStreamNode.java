package cn.edu.gdou.jingbanyou.tourist.graph.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * 流式答案节点基类
 * 封装 Flux<String> 的通用流式 LLM 调用逻辑
 * 子类只需定义 prompt 生成逻辑
 */
public abstract class AnswerStreamNode {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 调用流式 LLM，返回字符/词片段流
     *
     * @param chatClient  ChatClient 实例
     * @param userText    用户输入文本
     * @param sessionId   会话 ID（可选，用于 ChatMemory）
     * @return LLM 生成的字符流
     */
    protected Flux<String> streamAnswer(ChatClient chatClient, String userText, String sessionId) {
        log.info("[流式生成] 开始: userText={}", userText);
        try {
            Flux<String> flux;
            if (sessionId != null) {
                flux = chatClient.prompt()
                        .user(userText)
                        .advisors(ctx -> ctx.param("conversation_id", sessionId))
                        .stream()
                        .content();
            } else {
                flux = chatClient.prompt()
                        .user(userText)
                        .stream()
                        .content();
            }

            return flux.doOnComplete(() -> log.info("[流式生成] 完成"))
                    .doOnError(e -> log.error("[流式生成] 失败", e));
        } catch (Exception e) {
            log.error("[流式生成] 异常", e);
            return Flux.just("抱歉，生成答案时出现错误。");
        }
    }

    /**
     * 子类实现：构建用户输入文本
     */
    public abstract String buildUserText(Function<String, Object> stateGetter);

    /**
     * 获取使用的 ChatClient
     */
    protected abstract ChatClient getChatClient(Function<String, Object> stateGetter);

    /**
     * 获取会话 ID
     */
    protected String getSessionId(Function<String, Object> stateGetter) {
        Object sid = stateGetter.apply("sessionId");
        return sid != null ? sid.toString() : null;
    }
}
