package cn.edu.gdou.jingbanyou.tourist.service.sse;

import cn.edu.gdou.jingbanyou.tourist.dto.SseMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 流桥接服务
 * <p>
 * 将 Redis Pub/Sub + List 中的 SSE 事件桥接为 Flux ServerSentEvent
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SseStreamBridge {

    private static final String EVENTS_KEY_PREFIX = "tourist:sse:events:";
    private static final String CHANNEL_PREFIX = "tourist:sse:";
    private static final String STATUS_KEY_PREFIX = "tourist:sse:status:";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    /**
     * 已完成标记（避免 done/error 之后的事件被处理）
     */
    private final Map<String, AtomicBoolean> completed = new ConcurrentHashMap<>();

    /**
     * 创建 SSE 事件流
     *
     * @param conversationId 会话 ID
     * @return SSE 事件流
     */
    public Flux<ServerSentEvent<String>> createSseFlux(String conversationId) {
        String listKey = EVENTS_KEY_PREFIX + conversationId;
        String channel = CHANNEL_PREFIX + conversationId;
        String statusKey = STATUS_KEY_PREFIX + conversationId;

        completed.put(conversationId, new AtomicBoolean(false));

        return Flux.<ServerSentEvent<String>>create(emitter -> {
            // 1. 先读取 List 中已有的事件（late-joiner 支持）
            List<String> existing = stringRedisTemplate.opsForList().range(listKey, 0, -1);
            if (existing != null) {
                for (String json : existing) {
                    if (emitter.isCancelled()) return;
                    ServerSentEvent<String> sse = parseSse(json);
                    if (sse != null) {
                        emitter.next(sse);
                        String eventName = sse.event();
                        if (eventName != null
                                && ("done".equals(eventName) || "error".equals(eventName))) {
                            completed.get(conversationId).set(true);
                            emitter.complete();
                            return;
                        }
                    }
                }
            }

            // 2. 检查是否已完成（消费者比订阅者先结束的情况）
            String status = stringRedisTemplate.opsForValue().get(statusKey);
            if ("completed".equals(status)) {
                emitter.complete();
                return;
            }

            // 3. 订阅 Redis Pub/Sub，实时接收新事件
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);

            MessageListener listener = (Message msg, byte[] pattern) -> {
                if (emitter.isCancelled()) return;
                AtomicBoolean done = completed.get(conversationId);
                if (done != null && done.get()) return;

                String json = new String(msg.getBody());
                ServerSentEvent<String> sse = parseSse(json);
                if (sse != null) {
                    emitter.next(sse);
                    String eventName = sse.event();
                    if (eventName != null
                            && ("done".equals(eventName) || "error".equals(eventName))) {
                        if (done != null) done.set(true);
                        emitter.complete();
                    }
                }
            };

            container.addMessageListener(listener, new ChannelTopic(channel));
            container.afterPropertiesSet();
            container.start();

            // 4. 断开时清理
            emitter.onDispose(container::stop);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 解析 JSON 为 ServerSentEvent
     */
    private ServerSentEvent<String> parseSse(String json) {
        try {
            SseMessage msg = objectMapper.readValue(json, SseMessage.class);
            return ServerSentEvent.<String>builder()
                    .id(msg.id())
                    .event(msg.event())
                    .data(msg.data())
                    .build();
        } catch (Exception e) {
            log.error("[SSE桥接] 解析失败: {}", json, e);
            return null;
        }
    }
}
