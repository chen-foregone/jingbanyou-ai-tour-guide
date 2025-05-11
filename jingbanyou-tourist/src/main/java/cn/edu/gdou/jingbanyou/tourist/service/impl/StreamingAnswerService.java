package cn.edu.gdou.jingbanyou.tourist.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.tourist.dto.StreamAnswerContext;
import cn.edu.gdou.jingbanyou.tourist.graph.StreamGraphConfiguration;
import cn.edu.gdou.jingbanyou.tourist.service.IStreamingAnswerService;
import cn.edu.gdou.jingbanyou.tourist.service.ITtsService;
import cn.edu.gdou.jingbanyou.tourist.service.sse.SseEventFactory;
import cn.hutool.extra.emoji.EmojiUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流式答案服务实现
 *
 * <p>核心流程：
 * <ol>
 *   <li>直接通过 ChatClient.stream() 获取真实 token 流</li>
 *   <li>累积 token 到段落缓冲区</li>
 *   <li>段落结束时：推送 answer_fragment SSE + 触发 TTS</li>
 *   <li>ChatMemory 由 MessageChatMemoryAdvisor 自动管理</li>
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

    @Qualifier("hybridRetrievalStreamingChatClient")
    private final ChatClient hybridRetrievalStreamingChatClient;

    @Qualifier("generalChatStreamingChatClient")
    private final ChatClient generalChatStreamingChatClient;

    private final ITtsService ttsService;
    private final ObjectMapper objectMapper;
    private final SseEventFactory sseEventFactory;

    @Override
    public Flux<ServerSentEvent<String>> streamAnswer(StreamAnswerContext ctx) {
        String intent = ctx.intent();
        String sessionId = ctx.sessionId();
        log.info("[流式] 开始, intent={}, sessionId={}", intent, sessionId);

        // route_plan: 从 Graph state 的 POLISHED_ROUTES 生成叙述文本流
        if (StreamGraphConfiguration.INTENT_ROUTE_PLAN.equals(intent)) {
            String narrative = buildRouteNarrative(ctx.rawRoutes());
            return streamTextWithParagraphs(narrative, ctx);
        }

        // spot_question / complex_other: 直接通过 ChatClient.stream() 获取真实 token 流
        String retrievedDocs = ctx.retrievedDocs();
        Flux<String> tokenFlux;
        if (retrievedDocs == null || retrievedDocs.isBlank()) {
            tokenFlux = Flux.just("这个问题我暂时还不太清楚，建议您咨询景区工作人员。");
        } else {
            ChatClient streamingChatClient = StreamGraphConfiguration.INTENT_SPOT_QUESTION.equals(intent)
                    ? hybridRetrievalStreamingChatClient
                    : generalChatStreamingChatClient;

            // 用 Prompt 显式构造消息：
            // - UserText：原始用户问题（被 ChatMemory 存储，供历史查看）
            // - SystemText：RAG 检索上下文（不被 ChatMemory 存储，仅作为 LLM 上下文）
            List<Message> promptMessages = new ArrayList<>();
            promptMessages.add(new UserMessage(ctx.originalQuestion()));
            if (retrievedDocs != null && !retrievedDocs.isBlank()
                    && !retrievedDocs.startsWith("游客消息：")) {
                // spot_question：检索到知识文档，注入 RAG 上下文
                promptMessages.add(new SystemMessage("参考资料：\n" + retrievedDocs));
            }

            Prompt ragPrompt = new Prompt(promptMessages);
            tokenFlux = streamingChatClient.prompt(ragPrompt)
                    .advisors(c -> c.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .stream()
                    .content();
        }

        AtomicInteger seq = new AtomicInteger(0);
        AtomicBoolean firstParagraphSent = new AtomicBoolean(false);
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

        // TTS 音频 Sink：独立于文本流，fire-and-forget
        Sinks.Many<ServerSentEvent<String>> audioSink = Sinks.many().multicast().onBackpressureBuffer(128);

        // 文本流：段落到达时立即发送 answer_fragment SSE，TTS 后台发射到 audioSink
        Flux<ServerSentEvent<String>> textFlux = paragraphSink.asFlux()
                .doOnNext(paragraph -> {
                    if (firstParagraphSent.compareAndSet(false, true)) {
                        long firstParagraphMs = System.currentTimeMillis() - ctx.startTimestamp();
                        log.info("[流式] 首段落到达, intent={}, sessionId={}, graphCostMs={}, firstParagraphMs={}, 总耗时={}ms",
                                intent, sessionId, ctx.graphCostMs(), firstParagraphMs, firstParagraphMs);
                    }
                })
                .flatMapSequential(paragraph -> fireTextAndTriggerTts(
                        paragraph, ctx.digitalHuman(), seq, ctx.startTimestamp(), audioSink))
                .doOnComplete(() -> audioSink.tryEmitComplete());

        // 合并文本和音频：两边独立发射，互不阻塞
        return Flux.merge(textFlux, audioSink.asFlux())
                .doOnComplete(() -> log.info("[流式] 答案流完成，sessionId={}", sessionId))
                .doOnError(e -> log.error("[流式] LLM 流异常", e));
    }

    /**
     * 发送文本 SSE 并触发后台 TTS（fire-and-forget）
     *
     * <p>核心设计：文本段落立即返回给前端，TTS 在后台独立执行，
     * 音频块通过 audioSink 异步合并到同一个 SSE 流中。
     * 这样 TTS 的延迟不会阻塞后续段落的文本到达。
     *
     * @param paragraph    段落文本
     * @param digitalHuman 数字人配置（用于选择音色）
     * @param seq          TTS 音频序号
     * @param startTimestamp 流开始时间
     * @param audioSink    TTS 音频输出 Sink
     * @return 立即返回文本 SSE 的 Mono
     */
    private Mono<ServerSentEvent<String>> fireTextAndTriggerTts(
            String paragraph,
            DigitalHumanConfig digitalHuman,
            AtomicInteger seq,
            long startTimestamp,
            Sinks.Many<ServerSentEvent<String>> audioSink) {
        // 解析 JSON，提取 answer 字段（hybrid-retrieval prompt 输出 JSON 格式）
        String cleanText = extractAnswerFromJson(paragraph.trim());
        if (cleanText.isEmpty()) return Mono.empty();

        // 准备 TTS 文本（去 emoji + 截断）
        String ttsText = EmojiUtil.removeAllEmojis(cleanText);
        if (ttsText.length() > 500) {
            ttsText = ttsText.substring(0, 500);
        }

        // Fire-and-forget: 在独立订阅中执行 TTS，不等待结果
        if (!ttsText.isBlank()) {
            ttsService.streamAudio(ttsText, digitalHuman)
                    .map(chunk -> sseEventFactory.audio(chunk, seq.incrementAndGet(), startTimestamp))
                    .subscribe(
                            audioSink::tryEmitNext,
                            error -> log.error("[流式] TTS 段落失败，跳过: {}", error.getMessage())
                    );
        }

        // 立即返回文本 SSE，不等待 TTS
        return Mono.just(sseEventFactory.answerFragment(cleanText));
    }

    /**
     * 从 JSON 字符串中提取 answer 字段
     * <p>当 LLM 输出 JSON 格式时（如 hybrid-retrieval），只取 answer 字段的值，
     * 过滤掉 confidence、reason 等内部元数据
     */
    private String extractAnswerFromJson(String text) {
        if (text.isEmpty() || !text.contains("\"answer\":")) {
            return text;
        }
        try {
            Map<?, ?> json = objectMapper.readValue(text, Map.class);
            Object answer = json.get("answer");
            if (answer != null) {
                return answer.toString();
            }
        } catch (Exception e) {
            log.debug("[流式] JSON 解析失败，使用原始文本: {}", e.getMessage());
        }
        return text;
    }

    /**
     * 纯文本流（route_plan 路线叙述）
     */
    private Flux<ServerSentEvent<String>> streamTextWithParagraphs(
            String text, StreamAnswerContext ctx) {

        List<String> paragraphs = splitIntoParagraphs(text);
        AtomicInteger seq = new AtomicInteger(0);

        Sinks.Many<ServerSentEvent<String>> audioSink = Sinks.many().multicast().onBackpressureBuffer(128);

        Flux<ServerSentEvent<String>> textFlux = Flux.fromIterable(paragraphs)
                .flatMapSequential(p -> fireTextAndTriggerTts(
                        p, ctx.digitalHuman(), seq, ctx.startTimestamp(), audioSink))
                .doOnComplete(() -> audioSink.tryEmitComplete());

        return Flux.merge(textFlux, audioSink.asFlux())
                .doOnComplete(() -> log.info("[流式] 文本流完成，sessionId={}", ctx.sessionId()))
                .doOnError(e -> log.error("[流式] 文本流异常", e));
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

}
