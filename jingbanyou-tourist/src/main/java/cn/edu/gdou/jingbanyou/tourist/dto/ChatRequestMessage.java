package cn.edu.gdou.jingbanyou.tourist.dto;

import java.io.Serializable;

/**
 * RabbitMQ 消息 DTO — 对话请求
 * <p>
 * 包含 Graph 执行所需的最小参数集，通过 RabbitMQ 异步传递
 *
 * @author jingbanyou
 */
public record ChatRequestMessage(
        /**
         * 唯一请求 ID，用于关联 SSE 结果
         */
        String conversationId,
        /**
         * 用户消息
         */
        String message,
        /**
         * 对话上下文 ID（用于 ChatMemory）
         */
        String sessionId,
        /**
         * 景区 ID
         */
        Long scenicId,
        /**
         * 访客 ID
         */
        String visitorId
) implements Serializable {
}
