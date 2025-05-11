package cn.edu.gdou.jingbanyou.tourist.dto;

import java.io.Serializable;

/**
 * SSE 事件传输 DTO
 * <p>
 * 在 Consumer 和 SSE 端点之间通过 Redis Pub/Sub + List 传递
 *
 * @author jingbanyou
 */
public record SseMessage(
        /**
         * 事件类型：metadata, answer_fragment, audio, answer, done, error
         */
        String event,
        /**
         * 事件数据（JSON 字符串，与 SseEventFactory 格式一致）
         */
        String data,
        /**
         * 事件 ID（用于去重）
         */
        String id,
        /**
         * 时间戳
         */
        long timestamp
) implements Serializable {
}
