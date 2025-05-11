package cn.edu.gdou.jingbanyou.tourist.service.sse;

import cn.edu.gdou.jingbanyou.common.utils.JsonEscapeUtil;
import cn.edu.gdou.jingbanyou.tourist.util.RouteAttachmentBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

/**
 * SSE 事件工厂
 * 统一构建游客端所有 SSE 事件（answer_fragment, audio, answer, metadata, done, error）
 *
 * @author jingbanyou
 */
@Component
@RequiredArgsConstructor
public class SseEventFactory {

    private final RouteAttachmentBuilder routeAttachmentBuilder;

    /**
     * 构建 answer_fragment 事件（流式文本段落）
     *
     * @param content 段落文本内容
     * @return SSE 事件
     */
    public ServerSentEvent<String> answerFragment(String content) {
        String escaped = JsonEscapeUtil.escape(content);
        String data = "{\"content\":" + escaped + "}";
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .event("answer_fragment")
                .data(data)
                .build();
    }

    /**
     * 构建 audio 事件（TTS 音频块）
     *
     * @param chunk         音频字节块
     * @param seq           序号
     * @param startTimestamp 流开始时间戳
     * @return SSE 事件
     */
    public ServerSentEvent<String> audio(byte[] chunk, int seq, long startTimestamp) {
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

    /**
     * 构建 answer 事件（非流式场景完整答案）
     *
     * @param content 答案内容
     * @return SSE 事件
     */
    public ServerSentEvent<String> answer(String content) {
        String escaped = JsonEscapeUtil.escape(content);
        String data = "{\"content\":" + escaped + "}";
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .event("answer")
                .data(data)
                .build();
    }

    /**
     * 构建 metadata 事件（意图、附件等元数据）
     *
     * @param intent      意图类型
     * @param attachments 路线附件列表（可为 null）
     * @param graphCostMs Graph 执行耗时
     * @param timestamp   时间戳
     * @param sessionId   会话 ID
     * @return SSE 事件
     */
    public ServerSentEvent<String> metadata(String intent, List<?> attachments,
                                             long graphCostMs, long timestamp, String sessionId) {
        String data;
        if (attachments == null || attachments.isEmpty()) {
            data = "{\"intent\":" + JsonEscapeUtil.escape(intent)
                    + ",\"sessionId\":" + JsonEscapeUtil.escape(sessionId != null ? sessionId : "")
                    + ",\"graphCostMs\":" + graphCostMs
                    + ",\"timestamp\":" + timestamp + "}";
        } else {
            String attachmentsJson = escapeJsonString(routeAttachmentBuilder.buildAttachmentsJson(attachments));
            data = "{\"intent\":" + JsonEscapeUtil.escape(intent)
                    + ",\"sessionId\":" + JsonEscapeUtil.escape(sessionId != null ? sessionId : "")
                    + ",\"attachments\":" + attachmentsJson
                    + ",\"graphCostMs\":" + graphCostMs
                    + ",\"timestamp\":" + timestamp + "}";
        }
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(timestamp))
                .event("metadata")
                .data(data)
                .build();
    }

    /**
     * 构建 done 事件（流结束信号）
     *
     * @param startTimestamp 流开始时间戳
     * @return SSE 事件
     */
    public ServerSentEvent<String> done(long startTimestamp) {
        long totalCost = System.currentTimeMillis() - startTimestamp;
        String data = "{\"content\":\"\",\"totalCostMs\":" + totalCost + "}";
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .event("done")
                .data(data)
                .build();
    }

    /**
     * 将原始 JSON 字符串转义为 JSON 字符串值（加外层双引号并转义内部双引号和反斜杠）
     */
    private String escapeJsonString(String raw) {
        if (raw == null) return "\"\"";
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * 构建 error 事件
     *
     * @param message 错误信息
     * @return SSE 事件
     */
    public ServerSentEvent<String> error(String message) {
        String data = "{\"content\":\"\",\"error\":" + JsonEscapeUtil.escape(message)
                + ",\"timestamp\":" + System.currentTimeMillis() + "}";
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .event("error")
                .data(data)
                .build();
    }
}
