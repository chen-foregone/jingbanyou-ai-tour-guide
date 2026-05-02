package cn.edu.gdou.jingbanyou.tourist.config.chatclient;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 情感分析 ChatClient 配置
 *
 * @author jingbanyou
 */
@Configuration
public class EmotionAnalysisChatClientConfig {

    @Bean
    public ChatClient emotionAnalysisChatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("""
                你是一个景区智能导游的情感分析助手。
                请根据游客的问题，分析其情感倾向。

                输出格式（仅输出 JSON，不要有任何其他内容）：
                {
                    "emotion": "positive" | "neutral" | "negative",
                    "confidence": 0.0 ~ 1.0,
                    "reason": "简要分析理由"
                }

                情感分类标准：
                - positive（正面）：表达满意、感谢、赞美、期待、愉悦等积极情绪
                - neutral（中性）：普通咨询、询问事实、不带情绪色彩的普通提问
                - negative（负面）：表达不满、抱怨、投诉、焦虑、失望、批评等消极情绪
                """).build();
    }
}
