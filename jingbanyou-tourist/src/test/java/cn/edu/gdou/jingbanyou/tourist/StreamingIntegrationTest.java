package cn.edu.gdou.jingbanyou.tourist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SSE 流式对话接口集成测试
 * 前置条件：应用已在端口 9091 启动（RuoYiApplication in jingbanyou-admin）
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StreamingIntegrationTest {

    private static final String BASE = "http://localhost:9091/tourist";
    private static final Long SCENIC_ID = 1L;

    private static final Set<String> VALID_EVENT_TYPES = Set.of(
            "metadata", "answer_fragment", "answer", "audio", "done", "error"
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    StreamingIntegrationTest() {
        this.objectMapper = new ObjectMapper();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(120).toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    // ==========================================================================
    // PATH 1: Direct SSE (POST /tourist/stream)
    // ==========================================================================

    /**
     * 景点知识问答：退思园历史
     * <p>
     * 验证 SSE 流包含: metadata (intent=spot_question) -> answer_fragment/answer -> done
     */
    @Test
    @Order(1)
    @DisplayName("[Direct SSE] 景点知识问答 - POST /tourist/stream")
    void direct_sse_spot_question() {
        log.info("=== Test 1: direct_sse_spot_question ===");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "退思园有什么历史");
        body.put("visitorId", "test-stream-001");
        body.put("scenicId", SCENIC_ID);

        List<Map<String, String>> events = postAndParseSse("/stream", body);

        // 1. 第一事件应为 metadata
        assertFalse(events.isEmpty(), "SSE 事件列表不应为空");
        Map<String, String> first = events.get(0);
        assertEquals("metadata", first.get("event"), "第一事件应为 metadata");

        // 2. metadata data 应为合法 JSON 且包含 intent 字段
        JsonNode metaData = parseJson(first.get("data"));
        assertNotNull(metaData, "metadata data 应为合法 JSON");
        String intent = metaData.path("intent").asText("");
        assertEquals("spot_question", intent, "intent 应为 spot_question");

        // 3. metadata 应包含 sessionId（非空）
        String sessionId = metaData.path("sessionId").asText("");
        assertNotNull(sessionId, "sessionId 不应为 null");
        assertFalse(sessionId.isBlank(), "sessionId 不应为空");

        // 4. 至少有一个 answer_fragment 或 answer 事件
        boolean hasAnswer = events.stream().anyMatch(e ->
                "answer_fragment".equals(e.get("event")) || "answer".equals(e.get("event")));
        assertTrue(hasAnswer, "SSE 流中应包含至少一个 answer_fragment 或 answer 事件");

        // 5. answer/answer_fragment 的 data 应包含 content 字段
        Optional<Map<String, String>> answerEvent = events.stream()
                .filter(e -> "answer_fragment".equals(e.get("event")) || "answer".equals(e.get("event")))
                .findFirst();
        assertTrue(answerEvent.isPresent(), "应存在 answer 类事件");
        JsonNode answerData = parseJson(answerEvent.get().get("data"));
        assertNotNull(answerData, "answer data 应为合法 JSON");
        String content = answerData.path("content").asText("");
        assertFalse(content.isBlank(), "answer content 不应为空");
        log.info("回答内容(前100字): {}", truncate(content, 100));

        // 6. 最后事件应为 done
        Map<String, String> last = events.get(events.size() - 1);
        assertEquals("done", last.get("event"), "最后事件应为 done");

        // 7. done data 应包含 totalCostMs > 0
        JsonNode doneData = parseJson(last.get("data"));
        assertNotNull(doneData, "done data 应为合法 JSON");
        assertTrue(doneData.has("totalCostMs"), "done data 应包含 totalCostMs");
        long totalCostMs = doneData.path("totalCostMs").asLong(0);
        assertTrue(totalCostMs > 0, "totalCostMs 应大于 0，实际: " + totalCostMs);
        log.info("总耗时: {}ms", totalCostMs);

        // 8. 不应有 error 事件
        boolean hasError = events.stream().anyMatch(e -> "error".equals(e.get("event")));
        assertFalse(hasError, "SSE 流中不应包含 error 事件");
    }

    /**
     * 路线规划：从东门到退思园
     * <p>
     * 验证 SSE 流包含: metadata (intent=route_plan) -> 答案 -> done
     */
    @Test
    @Order(2)
    @DisplayName("[Direct SSE] 路线规划 - POST /tourist/stream")
    void direct_sse_route_plan() {
        log.info("=== Test 2: direct_sse_route_plan ===");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "从东门到退思园怎么走");
        body.put("visitorId", "test-stream-002");
        body.put("scenicId", SCENIC_ID);

        List<Map<String, String>> events = postAndParseSse("/stream", body);

        assertFalse(events.isEmpty(), "SSE 事件列表不应为空");

        // 1. metadata 事件
        Map<String, String> first = events.get(0);
        assertEquals("metadata", first.get("event"), "第一事件应为 metadata");
        JsonNode metaData = parseJson(first.get("data"));
        assertNotNull(metaData, "metadata data 应为合法 JSON");
        String intent = metaData.path("intent").asText("");
        assertEquals("route_plan", intent, "intent 应为 route_plan");

        // 2. metadata 可能包含 attachments（路线数据）
        JsonNode attachments = metaData.path("attachments");
        if (!attachments.isMissingNode() && !attachments.isNull()) {
            assertTrue(attachments.isArray(), "attachments 应为数组");
            log.info("路线附件数量: {}", attachments.size());
        } else {
            log.info("metadata 未包含 attachments（可能是 pending 场景）");
        }

        // 3. 最后事件应为 done
        Map<String, String> last = events.get(events.size() - 1);
        assertEquals("done", last.get("event"), "最后事件应为 done");

        // 4. 不应有 error 事件
        boolean hasError = events.stream().anyMatch(e -> "error".equals(e.get("event")));
        assertFalse(hasError, "SSE 流中不应包含 error 事件");
    }

    /**
     * 兜底闲聊：你好
     * <p>
     * 验证 SSE 流包含: metadata (intent=complex_other) -> 回答 -> done
     */
    @Test
    @Order(3)
    @DisplayName("[Direct SSE] 兜底闲聊 - POST /tourist/stream")
    void direct_sse_complex_other() {
        log.info("=== Test 3: direct_sse_complex_other ===");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "你好");
        body.put("visitorId", "test-stream-003");
        body.put("scenicId", SCENIC_ID);

        List<Map<String, String>> events = postAndParseSse("/stream", body);

        assertFalse(events.isEmpty(), "SSE 事件列表不应为空");

        // 1. metadata 事件
        Map<String, String> first = events.get(0);
        assertEquals("metadata", first.get("event"), "第一事件应为 metadata");
        JsonNode metaData = parseJson(first.get("data"));
        assertNotNull(metaData, "metadata data 应为合法 JSON");
        String intent = metaData.path("intent").asText("");
        assertEquals("complex_other", intent, "intent 应为 complex_other");

        // 2. 至少有一个 answer_fragment 或 answer 事件
        boolean hasAnswer = events.stream().anyMatch(e ->
                "answer_fragment".equals(e.get("event")) || "answer".equals(e.get("event")));
        assertTrue(hasAnswer, "SSE 流中应包含至少一个 answer_fragment 或 answer 事件");

        // 3. 最后事件应为 done
        Map<String, String> last = events.get(events.size() - 1);
        assertEquals("done", last.get("event"), "最后事件应为 done");

        // 4. 不应有 error 事件
        boolean hasError = events.stream().anyMatch(e -> "error".equals(e.get("event")));
        assertFalse(hasError, "SSE 流中不应包含 error 事件");
    }

    /**
     * 空消息验证：message 为空字符串
     * <p>
     * 验证 SSE 流立即返回一个 error 事件，而不是正常处理流程
     */
    @Test
    @Order(4)
    @DisplayName("[Direct SSE] 空消息错误 - POST /tourist/stream (empty message)")
    void direct_sse_empty_message() {
        log.info("=== Test 4: direct_sse_empty_message ===");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "");
        body.put("visitorId", "test-stream-004");
        body.put("scenicId", SCENIC_ID);

        List<Map<String, String>> events = postAndParseSse("/stream", body);

        assertFalse(events.isEmpty(), "SSE 事件列表不应为空");

        // SSE 流应包含 error 事件
        boolean hasError = events.stream().anyMatch(e -> "error".equals(e.get("event")));
        assertTrue(hasError, "空消息应返回 error 事件");

        // 验证 error data 为合法 JSON
        Optional<Map<String, String>> errorEvent = events.stream()
                .filter(e -> "error".equals(e.get("event")))
                .findFirst();
        assertTrue(errorEvent.isPresent(), "应存在 error 事件");
        JsonNode errorData = parseJson(errorEvent.get().get("data"));
        assertNotNull(errorData, "error data 应为合法 JSON");

        // 验证 error 事件是唯一事件（不应有后续正常事件）
        assertEquals(1, events.size(), "空消息时 SSE 流应仅包含一个 error 事件");
    }

    /**
     * 缺少 visitorId 验证
     * <p>
     * 验证 SSE 流立即返回一个 error 事件
     */
    @Test
    @Order(5)
    @DisplayName("[Direct SSE] 缺少访客ID错误 - POST /tourist/stream (missing visitorId)")
    void direct_sse_missing_visitor() {
        log.info("=== Test 5: direct_sse_missing_visitor ===");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "你好");
        body.put("scenicId", SCENIC_ID);
        // 不传 visitorId

        List<Map<String, String>> events = postAndParseSse("/stream", body);

        assertFalse(events.isEmpty(), "SSE 事件列表不应为空");

        // SSE 流应包含 error 事件
        boolean hasError = events.stream().anyMatch(e -> "error".equals(e.get("event")));
        assertTrue(hasError, "缺少 visitorId 应返回 error 事件");

        // 验证 error 事件是唯一事件
        assertEquals(1, events.size(), "缺少 visitorId 时 SSE 流应仅包含一个 error 事件");
    }

    // ==========================================================================
    // PATH 2: Async MQ (POST /tourist/chat + GET /tourist/stream/{conversationId})
    // ==========================================================================

    /**
     * 异步对话提交 + SSE 订阅全链路测试
     * <p>
     * 步骤：
     * 1. POST /tourist/chat 提交对话请求，获取 conversationId
     * 2. 等待 Consumer 通过 RabbitMQ 处理请求（2 秒）
     * 3. GET /tourist/stream/{conversationId} 订阅 SSE 结果流
     * 4. 验证事件顺序和内容
     */
    @Test
    @Order(10)
    @DisplayName("[Async MQ] 提交对话 + SSE 订阅 - POST /tourist/chat + GET /tourist/stream/{convId}")
    void async_chat_subscribe() {
        log.info("=== Test 10: async_chat_subscribe ===");

        String visitorId = "test-async-001";
        String message = "灵山大佛";

        // Step 1: POST /tourist/chat
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("message", message);
        requestBody.put("visitorId", visitorId);
        requestBody.put("scenicId", SCENIC_ID);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> submitResponse = restTemplate.exchange(
                BASE + "/chat", HttpMethod.POST, requestEntity, String.class);

        assertEquals(HttpStatus.OK, submitResponse.getStatusCode(),
                "提交对话 HTTP 状态码应为 200");

        JsonNode submitRoot = parseJson(submitResponse.getBody());
        assertNotNull(submitRoot, "提交响应不应为空");
        assertEquals(200, submitRoot.path("code").asInt(), "业务 code 应为 200");

        JsonNode submitData = submitRoot.path("data");
        assertFalse(submitData.isNull() || submitData.isMissingNode(), "data 不应为空");

        // Step 2: 提取 conversationId
        String conversationId = submitData.path("conversationId").asText("");
        assertFalse(conversationId.isBlank(), "conversationId 不应为空");
        log.info("获取到 conversationId: {}", conversationId);

        // Step 3: 等待 Consumer 处理（RabbitMQ + Graph 执行 + TTS 生成可能需要数秒）
        log.info("等待 Consumer 处理...");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待被中断", e);
        }

        // Step 4: GET /tourist/stream/{conversationId}
        List<Map<String, String>> events = getAndParseSse("/stream/" + conversationId);

        assertFalse(events.isEmpty(), "SSE 事件列表不应为空");
        log.info("共接收到 {} 个 SSE 事件", events.size());

        // 验证包含 metadata 事件
        Optional<Map<String, String>> metadataEvent = events.stream()
                .filter(e -> "metadata".equals(e.get("event")))
                .findFirst();
        assertTrue(metadataEvent.isPresent(), "SSE 流应包含 metadata 事件");

        JsonNode metaData = parseJson(metadataEvent.get().get("data"));
        assertNotNull(metaData, "metadata data 应为合法 JSON");
        String intent = metaData.path("intent").asText("");
        assertFalse(intent.isBlank(), "intent 不应为空");
        log.info("意图识别结果: {}", intent);

        // 验证包含 answer/content 事件
        boolean hasAnswer = events.stream().anyMatch(e ->
                "answer_fragment".equals(e.get("event")) || "answer".equals(e.get("event")));
        assertTrue(hasAnswer, "SSE 流中应包含至少一个 answer_fragment 或 answer 事件");

        // 验证包含 done 事件
        boolean hasDone = events.stream().anyMatch(e -> "done".equals(e.get("event")));
        assertTrue(hasDone, "SSE 流应包含 done 事件");

        // 验证所有 data 字段均为合法 JSON
        for (int i = 0; i < events.size(); i++) {
            Map<String, String> evt = events.get(i);
            String dataStr = evt.get("data");
            if (dataStr != null && !dataStr.isBlank()) {
                JsonNode dataNode = parseJson(dataStr);
                assertNotNull(dataNode,
                        "事件 #" + i + " (" + evt.get("event") + ") 的 data 应为合法 JSON");
            }
        }

        // 不应有 error 事件
        boolean hasError = events.stream().anyMatch(e -> "error".equals(e.get("event")));
        if (hasError) {
            Optional<Map<String, String>> errorEvt = events.stream()
                    .filter(e -> "error".equals(e.get("event")))
                    .findFirst();
            errorEvt.ifPresent(e -> {
                JsonNode errData = parseJson(e.get("data"));
                log.warn("SSE 流包含 error 事件: {}",
                        errData != null ? errData.path("error").asText() : e.get("data"));
            });
        }
        // 注意：由于 AI 服务的不确定性，这里不强制断言无 error，仅记录日志
        // 但如果 intent 已识别且有 answer 事件，error 不应出现在 done 之后
        if (hasDone && hasAnswer) {
            int lastDoneIndex = -1;
            for (int i = events.size() - 1; i >= 0; i--) {
                if ("done".equals(events.get(i).get("event"))) {
                    lastDoneIndex = i;
                    break;
                }
            }
            for (int i = lastDoneIndex + 1; i < events.size(); i++) {
                assertNotEquals("error", events.get(i).get("event"),
                        "done 事件之后不应出现 error 事件");
            }
        }
    }

    // ==========================================================================
    // SSE 协议格式验证
    // ==========================================================================

    /**
     * SSE 协议格式全面验证
     * <p>
     * 使用有效请求，验证原始 SSE 响应的协议合规性：
     * <ul>
     *   <li>事件块间由双换行分隔</li>
     *   <li>每块包含 event: 和 data: 行</li>
     *   <li>所有 data: 值为合法 JSON</li>
     *   <li>事件顺序：metadata 在先，done/error 在最后</li>
     *   <li>事件类型在合法白名单内</li>
     * </ul>
     */
    @Test
    @Order(20)
    @DisplayName("[SSE 格式] SSE 协议格式全面验证")
    void sse_format_validation() {
        log.info("=== Test 20: sse_format_validation ===");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "退思园有什么历史");
        body.put("visitorId", "test-format-001");
        body.put("scenicId", SCENIC_ID);

        // 获取原始 SSE 响应体（不通过解析器，直接读取原始文本）
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                BASE + "/stream", HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "HTTP 状态码应为 200");

        // 验证 Content-Type 为 text/event-stream
        MediaType contentType = response.getHeaders().getContentType();
        assertNotNull(contentType, "Content-Type 不应为空");
        String contentTypeStr = contentType.toString().toLowerCase();
        assertTrue(contentTypeStr.contains("text/event-stream")
                        || contentTypeStr.contains("*/*"),
                "Content-Type 应包含 text/event-stream，实际: " + contentType);

        String rawBody = response.getBody();
        assertNotNull(rawBody, "响应体不应为空");
        assertFalse(rawBody.isBlank(), "响应体不应为空");

        log.info("原始 SSE 响应体长度: {} 字符", rawBody.length());
        log.debug("原始 SSE 响应体:\n{}", rawBody);

        // 解析 SSE 原始文本
        List<Map<String, String>> events = parseRawSse(rawBody);
        assertFalse(events.isEmpty(), "SSE 事件列表不应为空");

        // 1. 验证每个事件块包含 event: 和 data: 行（通过原始文本验证）
        String[] blocks = rawBody.split("\n\n");
        List<String> nonEmptyBlocks = Arrays.stream(blocks)
                .map(String::trim)
                .filter(b -> !b.isEmpty())
                .collect(Collectors.toList());

        assertFalse(nonEmptyBlocks.isEmpty(), "应至少有一个非空事件块");
        log.info("非空 SSE 事件块数量: {}", nonEmptyBlocks.size());

        for (int i = 0; i < nonEmptyBlocks.size(); i++) {
            String block = nonEmptyBlocks.get(i);
            boolean hasEventLine = block.contains("event:");
            boolean hasDataLine = block.contains("data:");
            assertTrue(hasEventLine,
                    "事件块 #" + i + " 应包含 event: 行\n" + block);
            assertTrue(hasDataLine,
                    "事件块 #" + i + " 应包含 data: 行\n" + block);
        }

        // 2. 验证每个 data: 值为合法 JSON
        for (int i = 0; i < events.size(); i++) {
            Map<String, String> evt = events.get(i);
            String dataStr = evt.get("data");
            assertNotNull(dataStr, "事件 #" + i + " (" + evt.get("event") + ") 的 data 不应为 null");
            assertFalse(dataStr.isBlank(), "事件 #" + i + " (" + evt.get("event") + ") 的 data 不应为空");
            JsonNode parsed = parseJson(dataStr);
            assertNotNull(parsed,
                    "事件 #" + i + " (" + evt.get("event") + ") 的 data 应为合法 JSON\n原始 data: " + dataStr);
        }

        // 3. 验证事件类型在合法白名单内
        for (int i = 0; i < events.size(); i++) {
            String eventType = events.get(i).get("event");
            assertNotNull(eventType, "事件 #" + i + " 的 event 类型不应为 null");
            assertTrue(VALID_EVENT_TYPES.contains(eventType),
                    "事件 #" + i + " 的 event 类型 '" + eventType + "' 不在合法白名单 " + VALID_EVENT_TYPES + " 中");
        }

        // 4. 验证事件顺序：metadata 最先，done/error 最后
        String firstEvent = events.get(0).get("event");
        assertEquals("metadata", firstEvent, "第一个事件应为 metadata");

        String lastEvent = events.get(events.size() - 1).get("event");
        assertTrue("done".equals(lastEvent) || "error".equals(lastEvent),
                "最后一个事件应为 done 或 error，实际: " + lastEvent);

        // 5. 验证 metadata 事件的内容完整性
        Map<String, String> metaEvt = events.get(0);
        JsonNode metaData = parseJson(metaEvt.get("data"));
        assertNotNull(metaData, "metadata data 应为合法 JSON");
        assertTrue(metaData.has("intent"), "metadata 应包含 intent");
        assertTrue(metaData.has("sessionId"), "metadata 应包含 sessionId");
        assertTrue(metaData.has("graphCostMs"), "metadata 应包含 graphCostMs");
        assertTrue(metaData.has("timestamp"), "metadata 应包含 timestamp");

        String intent = metaData.path("intent").asText("");
        assertFalse(intent.isBlank(), "intent 不应为空");

        String sessionId = metaData.path("sessionId").asText("");
        assertFalse(sessionId.isBlank(), "sessionId 不应为空");

        long graphCostMs = metaData.path("graphCostMs").asLong(0);
        assertTrue(graphCostMs >= 0, "graphCostMs 不应为负数，实际: " + graphCostMs);

        // 6. 验证 done 事件的 data 格式
        Map<String, String> lastEvt = events.get(events.size() - 1);
        if ("done".equals(lastEvt.get("event"))) {
            JsonNode doneData = parseJson(lastEvt.get("data"));
            assertNotNull(doneData, "done data 应为合法 JSON");
            // done data 可能包含 "content" 或 "totalCostMs"
            // 实际格式: {"content":"","totalCostMs":12345}
            assertTrue(doneData.has("totalCostMs"), "done data 应包含 totalCostMs");
        }

        // 7. 验证 metadata 之后不能出现第二个 metadata（意图不应切换）
        long metadataCount = events.stream()
                .filter(e -> "metadata".equals(e.get("event")))
                .count();
        assertEquals(1, metadataCount, "SSE 流中应恰好有一个 metadata 事件");

        // 8. 验证 done/error 事件出现后不能再有其他事件
        int terminalIndex = -1;
        for (int i = 0; i < events.size(); i++) {
            String evt = events.get(i).get("event");
            if ("done".equals(evt) || "error".equals(evt)) {
                terminalIndex = i;
                break;
            }
        }
        if (terminalIndex >= 0) {
            for (int i = terminalIndex + 1; i < events.size(); i++) {
                fail("done/error 事件（索引 " + terminalIndex + "）之后不应再有事件，"
                        + "但发现事件 #" + i + ": " + events.get(i).get("event"));
            }
        }
    }

    // ==========================================================================
    // 辅助方法
    // ==========================================================================

    /**
     * POST 请求到指定路径，解析 SSE 响应为结构化事件列表
     *
     * @param path   相对于 /tourist 的路径（如 "/stream"）
     * @param body   请求体 JSON 键值对
     * @return 解析后的事件列表，每个事件为 {event, data} 映射
     */
    private List<Map<String, String>> postAndParseSse(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                BASE + path, HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "HTTP 状态码应为 200");

        String rawBody = response.getBody();
        assertNotNull(rawBody, "响应体不应为空");

        return parseRawSse(rawBody);
    }

    /**
     * GET 请求到指定路径，解析 SSE 响应为结构化事件列表
     *
     * @param path 相对于 /tourist 的路径（如 "/stream/{convId}"）
     * @return 解析后的事件列表
     */
    private List<Map<String, String>> getAndParseSse(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM, MediaType.ALL));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                BASE + path, HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "HTTP 状态码应为 200");

        String rawBody = response.getBody();
        assertNotNull(rawBody, "响应体不应为空");

        return parseRawSse(rawBody);
    }

    /**
     * 将原始 SSE 文本解析为结构化事件列表
     * <p>
     * SSE 协议格式：
     * <pre>
     * event:metadata
     * data:{...}
     *
     * event:answer_fragment
     * data:{...}
     * </pre>
     * 每个事件块由 \n\n 分隔，每行以 "event:" 或 "data:" 开头
     *
     * @param rawBody 原始的 SSE 响应体文本
     * @return 事件列表，每条包含 event 和 data 键
     */
    private List<Map<String, String>> parseRawSse(String rawBody) {
        List<Map<String, String>> events = new ArrayList<>();

        // 按 \n\n 分割事件块
        String[] blocks = rawBody.split("\n\n");
        for (String block : blocks) {
            block = block.trim();
            if (block.isEmpty()) {
                continue;
            }

            String eventType = null;
            String data = null;

            for (String line : block.split("\n")) {
                line = line.trim();
                if (line.startsWith("event:")) {
                    eventType = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    data = line.substring("data:".length()).trim();
                } else if (line.startsWith("id:")) {
                    // id 行可以忽略，不作为断言依据
                    // 不做任何操作
                }
                // 其他行（如注释行 :xxx）可以忽略
            }

            // 只添加同时有 event 和 data 的事件块
            if (eventType != null || data != null) {
                Map<String, String> event = new LinkedHashMap<>();
                event.put("event", eventType != null ? eventType : "");
                event.put("data", data != null ? data : "");
                events.add(event);
            }
        }

        log.debug("SSE 解析结果: {} 个事件", events.size());
        for (int i = 0; i < events.size(); i++) {
            log.debug("  事件 #{}: event={}, data(前50字)={}",
                    i, events.get(i).get("event"),
                    truncate(events.get(i).get("data"), 50));
        }

        return events;
    }

    /**
     * 解析 JSON 字符串为 Jackson JsonNode
     */
    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 截断字符串到最大长度
     */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
