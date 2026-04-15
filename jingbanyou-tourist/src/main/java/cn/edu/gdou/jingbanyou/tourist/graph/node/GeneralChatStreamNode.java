package cn.edu.gdou.jingbanyou.tourist.graph.node;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.function.Function;

import reactor.core.publisher.Flux;

/**
 * 闲聊兜底流式节点
 */
@Component
public class GeneralChatStreamNode extends AnswerStreamNode {

    private final ChatClient chatClient;

    public GeneralChatStreamNode(
            @Qualifier("generalChatChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    protected ChatClient getChatClient(Function<String, Object> stateGetter) {
        return chatClient;
    }

    @Override
    public String buildUserText(Function<String, Object> stateGetter) {
        String question = (String) stateGetter.apply(GraphStateKey.QUESTION);
        return "游客消息：" + question;
    }

    /**
     * 执行闲聊流式生成
     */
    public Flux<String> chatAndStream(Function<String, Object> stateGetter) {
        String userText = buildUserText(stateGetter);
        String sessionId = getSessionId(stateGetter);
        return streamAnswer(chatClient, userText, sessionId);
    }
}
