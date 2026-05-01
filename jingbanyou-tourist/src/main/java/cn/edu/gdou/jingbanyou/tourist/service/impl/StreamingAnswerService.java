package cn.edu.gdou.jingbanyou.tourist.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.tourist.graph.StreamGraphConfiguration;
import cn.edu.gdou.jingbanyou.tourist.graph.node.GeneralChatFallbackNode;
import cn.edu.gdou.jingbanyou.tourist.graph.node.HybridRetrievalNode;
import cn.edu.gdou.jingbanyou.tourist.service.IChatMemoryService;
import cn.edu.gdou.jingbanyou.tourist.service.IStreamingAnswerService;
import cn.edu.gdou.jingbanyou.tourist.service.ITtsService;
import cn.hutool.extra.emoji.EmojiUtil;
import com.alibaba.cloud.ai.memory.redis.JedisRedisChatMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Base64;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流式答案服务实现
 *
 * <p>核心流程：
 * <ol>
 *   <li>AI token 级流式输出</li>
 *   <li>累积 token 到段落缓冲区</li>
 *   <li>段落结束时：推送 answer_fragment SSE + 触发 TTS</li>
 *   <li>流结束后写入 Redis ChatMemory</li>
 * </ol>
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingAnswerService implements IStreamingAnswerService {

    private static final int MAX_PARAGRAPH_CHARS = 200;
    private static final Set<Character> PARAGRAPH_END_CHARS = Set.of('。', '！', '？', '．');

    private final HybridRetrievalNode hybridRetrievalNode;
    private final GeneralChatFallbackNode generalChatFallbackNode;
    private final ITtsService ttsService;
    private final IChatMemoryService chatMemoryService;
    private final JedisRedisChatMemoryRepository chatMemoryRepository;

    @Override
    public Flux<ServerSentEvent<String>> streamAnswer(
            String sessionId,
            Long scenicId,
            Long humanId,
            String visitorId,
            String intent,
            String userMessage,
            DigitalHumanConfig digitalHuman,
            List<?> rawRoutes,
            String intentType,
            int graphCostMs,
            long startTimestamp) {

        log.info("[流式] 开始, intent={}, sessionId={}", intent, sessionId);

        // route_plan: 从 Graph state 的 POLISHED_ROUTES 生成叙述文本流
        if (StreamGraphConfiguration.INTENT_ROUTE_PLAN.equals(intent)) {
            String narrative = buildRouteNarrative(rawRoutes);
            return streamTextWithParagraphs(narrative, digitalHuman, sessionId, scenicId,
                    humanId, visitorId, userMessage, intentType, graphCostMs, startTimestamp);
        }

        // spot_question / complex_other: 调用节点的 streamAnswer() 方法流式获取 token
        Flux<String> tokenFlux = StreamGraphConfiguration.INTENT_SPOT_QUESTION.equals(intent)
                ? hybridRetrievalNode.streamAnswer(userMessage, scenicId, sessionId)
                : generalChatFallbackNode.streamAnswer(userMessage, sessionId);

        AtomicInteger seq = new AtomicInteger(0);
        StringBuilder buffer = new StringBuilder();
        StringBuilder fullAnswer = new StringBuilder();
        Sinks.Many<String> paragraphSink = Sinks.many().multicast().onBackpressureBuffer(16);

        // AI token 流 → 累积到段落缓冲区 → 检测到段落边界时 emit 到 paragraphSink
        tokenFlux
                .doOnNext(text -> {
                    if (text == null || text.isBlank()) return;
                    fullAnswer.append(text);
                    buffer.append(text);
                    if (shouldFlush(buffer)) {
                        String paragraph = buffer.toString();
                        buffer.setLength(0);
                        Sinks.EmitResult result = paragraphSink.tryEmitNext(paragraph);
                        if (result.isFailure()) {
                            log.warn("[流式] 段落 Sink 发送失败: {}", result);
                        }
                    }
                })
                .doOnError(e -> paragraphSink.tryEmitError(e))
                .doOnComplete(() -> {
                    if (buffer.length() > 0) {
                        paragraphSink.tryEmitNext(buffer.toString());
                    }
                    paragraphSink.tryEmitComplete();
                })
                .subscribe();

        // paragraphSink → 依次处理每个段落：answer_fragment → TTS audio
        return paragraphSink.asFlux()
                .flatMapSequential(paragraph -> processParagraph(paragraph, digitalHuman, seq, startTimestamp))
                .doOnComplete(() -> {
                    writeChatMemory(sessionId, userMessage, fullAnswer.toString());
                    int totalMs = (int) (System.currentTimeMillis() - startTimestamp);
                    chatMemoryService.recordSingleTurn(sessionId, scenicId, humanId, visitorId,
                            userMessage, fullAnswer.toString(), "text_streaming",
                            intentType, totalMs, null, null);
                })
                .doOnError(e -> log.error("[流式] LLM 流异常", e))
                .doOnCancel(() -> writeChatMemory(sessionId, userMessage, fullAnswer.toString()));
    }

    /**
     * 纯文本流（route_plan 路线叙述）
     */
    private Flux<ServerSentEvent<String>> streamTextWithParagraphs(
            String text,
            DigitalHumanConfig digitalHuman,
            String sessionId,
            Long scenicId,
            Long humanId,
            String visitorId,
            String userMessage,
            String intentType,
            int graphCostMs,
            long startTimestamp) {

        List<String> paragraphs = splitIntoParagraphs(text);
        AtomicInteger seq = new AtomicInteger(0);
        String fullAnswer = text;

        return Flux.fromIterable(paragraphs)
                .flatMapSequential(p -> processParagraph(p, digitalHuman, seq, startTimestamp))
                .doOnComplete(() -> {
                    writeChatMemory(sessionId, userMessage, fullAnswer);
                    int totalMs = (int) (System.currentTimeMillis() - startTimestamp);
                    chatMemoryService.recordSingleTurn(sessionId, scenicId, humanId, visitorId,
                            userMessage, fullAnswer, "text_streaming",
                            intentType, totalMs, null, null);
                })
                .doOnError(e -> log.error("[流式] 文本流异常", e));
    }

    /**
     * 处理单个段落：先发 answer_fragment SSE，再发 TTS audio SSE
     */
    private Flux<ServerSentEvent<String>> processParagraph(
            String paragraph,
            DigitalHumanConfig digitalHuman,
            AtomicInteger seq,
            long startTimestamp) {
        String clean = paragraph.trim();
        if (clean.isEmpty()) return Flux.empty();

        String ttsText = EmojiUtil.removeAllEmojis(clean);
        if (ttsText.length() > 500) {
            ttsText = ttsText.substring(0, 500);
        }

        if (ttsText.isBlank()) {
            return Flux.just(answerFragmentSse(clean));
        }
        return Flux.concat(
                Mono.just(answerFragmentSse(clean)),
                ttsService.streamAudio(ttsText, digitalHuman)
                        .map(chunk -> audioSse(chunk, seq.incrementAndGet(), startTimestamp))
                        .onErrorResume(e -> {
                            log.warn("[流式] TTS 段落失败，跳过: {}", e.getMessage());
                            return Flux.empty();
                        })
        );
    }

    /**
     * 检测是否应该 flush 段落缓冲区
     */
    private boolean shouldFlush(StringBuilder buffer) {
        if (buffer.length() == 0) {
            return false;
        }
        char last = buffer.charAt(buffer.length() - 1);
        if (PARAGRAPH_END_CHARS.contains(last)) {
            return true;
        }
        if (buffer.length() >= MAX_PARAGRAPH_CHARS) {
            return true;
        }
        return false;
    }

    /**
     * 将文本按段落拆分
     */
    private List<String> splitIntoParagraphs(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        String[] parts = text.split("(?<=[。！？])\\s*");
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            buffer.append(part.trim());
            if (buffer.length() >= MAX_PARAGRAPH_CHARS) {
                result.add(buffer.toString());
                buffer.setLength(0);
            }
        }
        if (buffer.length() > 0) {
            result.add(buffer.toString());
        }
        return result;
    }

    /**
     * 从路线数据生成叙述文本
     */
    private String buildRouteNarrative(List<?> rawRoutes) {
        if (rawRoutes == null || rawRoutes.isEmpty()) {
            return "抱歉，暂时无法生成路线推荐。";
        }
        StringBuilder sb = new StringBuilder("为您推荐以下路线：");
        for (int i = 0; i < rawRoutes.size(); i++) {
            Map<String, Object> route = (Map<String, Object>) rawRoutes.get(i);
            String strategy = String.valueOf(route.getOrDefault("strategy", "路线" + (i + 1)));
            String desc = String.valueOf(route.getOrDefault("description", ""));
            sb.append(strategy).append("路线：").append(desc).append("。");
        }
        return sb.toString();
    }

    /**
     * 流结束后写入 Redis ChatMemory
     */
    private void writeChatMemory(String sessionId, String userMessage, String aiAnswer) {
        try {
            List<Message> existing = chatMemoryRepository.findByConversationId(sessionId);
            if (existing == null) {
                existing = new ArrayList<>();
            }
            existing.add(new UserMessage(userMessage));
            existing.add(new AssistantMessage(aiAnswer != null ? aiAnswer : ""));
            chatMemoryRepository.saveAll(sessionId, existing);
            log.info("[流式] ChatMemory 已写入, sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("[流式] ChatMemory 写入失败, sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    // ========== SSE 事件构建方法 ==========

    private ServerSentEvent<String> answerFragmentSse(String content) {
        String data = "{\"content\":\"" + escapeJson(content) + "\"}";
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .event("answer_fragment")
                .data(data)
                .build();
    }

    private ServerSentEvent<String> audioSse(byte[] chunk, int seq, long startTimestamp) {
        String base64 = Base64.getEncoder().encodeToString(chunk);
        long audioCost = System.currentTimeMillis() - startTimestamp;
        long serverTime = System.currentTimeMillis();
        String data = "{\"seq\":" + seq + ",\"chunk\":\"" + base64 + "\",\"audioCostMs\":" + audioCost + ",\"serverTime\":" + serverTime + "}";
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(serverTime))
                .event("audio")
                .data(data)
                .build();
    }

    private ServerSentEvent<String> errorSse(String message) {
        String data = "{\"content\":\"\",\"error\":\"" + escapeJson(message) + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .event("error")
                .data(data)
                .build();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
