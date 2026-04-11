package cn.edu.gdou.jingbanyou.tourist.graph.node;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 意图分类器 — 纯文本路径
 * <p>使用 BaseDistinguishNode 的模板方法 apply()
 * <p>system prompt 由 textDistinguishChatClient 的 defaultSystem 注入（来自 distinguish.yml）
 * <p>历史由 Advisor 自动管理
 */
@Component
public class TextDistinguishNode extends BaseDistinguishNode {

    public TextDistinguishNode(
            @Qualifier("textDistinguishChatClient") ChatClient chatClient) {
        super(chatClient);
    }

    /**
     * 不使用父类的模板方法 apply()，所以此方法不会被调用
     * 但抽象方法要求必须实现
     */
    @Override
    protected String getSystemPrompt() {
        return "";
    }
}
