package cn.edu.gdou.jingbanyou.tourist.service.sse;

import cn.edu.gdou.jingbanyou.common.utils.JsonEscapeUtil;
import cn.edu.gdou.jingbanyou.tourist.dto.SseMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * SSE 结果发布服务
 * <p>
 * 将 SSE 事件写入 Redis List（持久化，支持 late-joiner）+ Redis Pub/Sub（实时推送）
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SseResultPublisher {

    private static final String EVENTS_KEY_PREFIX = "tourist:sse:events:";
    private static final String STATUS_KEY_PREFIX = "tourist:sse:status:";
    private static final String CHANNEL_PREFIX = "tourist:sse:";
    private static final long TTL_SECONDS = 300;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 发布 SSE 事件到 Redis
     */
    public void publish(String conversationId, String event, String data, String id) {
        try {
            SseMessage msg = new SseMessage(event, data, id, System.currentTimeMillis());
            String json = objectMapper.writeValueAsString(msg);

            // 1. 写入 List（持久化，支持 late-joiner）
            stringRedisTemplate.opsForList().rightPush(EVENTS_KEY_PREFIX + conversationId, json);
            stringRedisTemplate.expire(EVENTS_KEY_PREFIX + conversationId, TTL_SECONDS, TimeUnit.SECONDS);

            // 2. 推送 Pub/Sub（实时通知订阅者）
            stringRedisTemplate.convertAndSend(CHANNEL_PREFIX + conversationId, json);

        } catch (Exception e) {
            log.error("[SSE发布] 失败 conversationId={}", conversationId, e);
        }
    }

    /**
     * 发布 metadata 事件
     */
    public void publishMetadata(String conversationId, String data) {
        publish(conversationId, "metadata", data, "metadata");
    }

    /**
     * 发布 answer 事件（非流式场景）
     */
    public void publishAnswer(String conversationId, String content) {
        String data = "{\"content\":" + JsonEscapeUtil.escape(content) + "}";
        publish(conversationId, "answer", data, "answer");
    }

    /**
     * 发布 done 事件
     */
    public void publishDone(String conversationId) {
        publish(conversationId, "done",
                "{\"totalCostMs\":" + System.currentTimeMillis() + "}", "done");
        setStatus(conversationId, "completed");
    }

    /**
     * 发布 error 事件
     */
    public void publishError(String conversationId, String errorMsg) {
        String data = "{\"content\":\"\",\"error\":\"" + errorMsg + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
        publish(conversationId, "error", data, "error");
        setStatus(conversationId, "completed");
    }

    private void setStatus(String conversationId, String status) {
        stringRedisTemplate.opsForValue().set(STATUS_KEY_PREFIX + conversationId, status);
        stringRedisTemplate.expire(STATUS_KEY_PREFIX + conversationId, TTL_SECONDS, TimeUnit.SECONDS);
    }
}
