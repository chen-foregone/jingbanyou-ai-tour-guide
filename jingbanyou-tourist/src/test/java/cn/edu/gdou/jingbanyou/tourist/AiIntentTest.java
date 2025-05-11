package cn.edu.gdou.jingbanyou.tourist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI intent classification and answer quality integration tests
 * Tests POST /tourist/stream SSE endpoint. Requires running app on port 9091.
 */
@Slf4j
public class AiIntentTest {

    private static final String BASE_URL = "http://localhost:9091";
    private static final String VISITOR_ID = "test-ai-intent";
    private static final Long SCENIC_ID = 1L;

    private final ObjectMapper objectMapper;
    private final RestTemplate sseRestTemplate;

    // ========== Intent accuracy tracking ==========
    private static final Map<String, AtomicInteger> totalByIntent = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> correctByIntent = new ConcurrentHashMap<>();
    private static final AtomicInteger rejectionTotal = new AtomicInteger(0);
    private static final AtomicInteger rejectionCorrect = new AtomicInteger(0);

    // ========== SSE event record ==========
    record SseEvent(String event, String data) {}

    public AiIntentTest() {
        this.objectMapper = new ObjectMapper();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(180_000); // SSE can be slow
        this.sseRestTemplate = new RestTemplate(factory);
    }

    // ==========================================================================
    // SSE Helpers
    // ==========================================================================

    /**
     * POST a message to /tourist/stream and parse the SSE response
     *
     * @return parsed SSE events
     */
    List<SseEvent> postAndParseSse(String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("visitorId", VISITOR_ID);
        body.put("scenicId", SCENIC_ID);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = sseRestTemplate.exchange(
                BASE_URL + "/tourist/stream",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "HTTP 200 expected for message: " + message);

        String rawBody = response.getBody();
        assertNotNull(rawBody, "SSE response body must not be null");
        assertFalse(rawBody.isBlank(), "SSE response body must not be blank");

        List<SseEvent> events = parseSseEvents(rawBody);
        log.debug("Parsed {} SSE events for message='{}'", events.size(), message);

        // Fail fast if an error event was returned
        for (SseEvent event : events) {
            if ("error".equals(event.event)) {
                fail("SSE error event received for message '" + message + "': " + event.data());
            }
        }

        return events;
    }

    /**
     * Parse raw SSE text into structured events
     *
     * SSE format per event block:
     *   id:...
     *   event:metadata|answer|answer_fragment|done|error
     *   data:{...json...}
     *
     * Events are separated by blank lines.
     */
    List<SseEvent> parseSseEvents(String raw) {
        List<SseEvent> events = new ArrayList<>();
        if (raw == null || raw.isBlank()) return events;

        // Normalize line endings
        String normalized = raw.replace("\r\n", "\n").trim();

        // Split by blank lines (double newline)
        String[] blocks = normalized.split("\n\n");
        for (String block : blocks) {
            block = block.trim();
            if (block.isEmpty()) continue;

            String eventType = null;
            StringBuilder dataBuilder = new StringBuilder();

            for (String line : block.split("\n")) {
                line = line.trim();
                if (line.startsWith("event:")) {
                    eventType = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (dataBuilder.length() > 0) {
                        dataBuilder.append("\n");
                    }
                    dataBuilder.append(line.substring(5).trim());
                }
                // "id:" and ":" (SSE comments) lines are ignored
            }

            String data = dataBuilder.toString();
            if (eventType != null && !data.isEmpty()) {
                events.add(new SseEvent(eventType, data));
            }
        }

        return events;
    }

    /**
     * Find the first SSE event of a given type
     */
    Optional<SseEvent> findFirstEvent(List<SseEvent> events, String eventType) {
        return events.stream().filter(e -> e.event.equals(eventType)).findFirst();
    }

    /**
     * Extract the intent string from the metadata SSE event
     */
    String extractIntent(List<SseEvent> events) {
        Optional<SseEvent> metadata = findFirstEvent(events, "metadata");
        if (metadata.isEmpty()) {
            fail("No metadata event in SSE response; total events: " + events.size()
                    + "\nEvents: " + events);
        }
        try {
            JsonNode json = objectMapper.readTree(metadata.get().data());
            return json.path("intent").asText("");
        } catch (Exception e) {
            fail("Failed to parse metadata JSON: " + metadata.get().data(), e);
            return "";
        }
    }

    /**
     * Extract the full answer text by concatenating all answer and answer_fragment events
     */
    String extractAnswer(List<SseEvent> events) {
        StringBuilder content = new StringBuilder();
        for (SseEvent event : events) {
            if ("answer".equals(event.event) || "answer_fragment".equals(event.event)) {
                try {
                    JsonNode json = objectMapper.readTree(event.data());
                    content.append(json.path("content").asText(""));
                } catch (Exception e) {
                    log.warn("Failed to parse {} JSON: {}", event.event(), event.data(), e);
                }
            }
        }
        return content.toString();
    }

    /**
     * Basic emoji detection via Unicode range checks
     */
    boolean containsEmoji(String source) {
        if (source == null) return false;
        for (int i = 0; i < source.length(); ) {
            int cp = source.codePointAt(i);
            if ((cp >= 0x1F300 && cp <= 0x1FAFF)
                    || (cp >= 0x2600 && cp <= 0x27BF)
                    || (cp >= 0xFE00 && cp <= 0xFE0F)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    /**
     * Normalize text for identity leak checking:
     * strip whitespace, punctuation, lowercase
     */
    String normalizeForLeakCheck(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\s\\p{Punct}]+", "").toLowerCase();
    }

    /**
     * Track intent classification accuracy
     */
    private void trackAccuracy(String expectedIntent, String actualIntent) {
        totalByIntent.computeIfAbsent(expectedIntent, k -> new AtomicInteger()).incrementAndGet();
        if (expectedIntent.equals(actualIntent)) {
            correctByIntent.computeIfAbsent(expectedIntent, k -> new AtomicInteger()).incrementAndGet();
        }
    }

    // ==========================================================================
    // A. Route Plan Intent (5 cases)
    // ==========================================================================

    static Stream<Arguments> routePlanCases() {
        return Stream.of(
                Arguments.of("从东门到大佛怎么走", "route_plan"),
                Arguments.of("帮我规划一条游览路线", "route_plan"),
                Arguments.of("我在停车场，想去退思园", "route_plan"),
                Arguments.of("从南门出发", "route_plan"),
                Arguments.of("怎么去珍珠塔", "route_plan")
        );
    }

    @ParameterizedTest(name = "[route_plan] \"{0}\"")
    @MethodSource("routePlanCases")
    void testRoutePlan(String message, String expectedIntent) {
        List<SseEvent> events = postAndParseSse(message);
        String actualIntent = extractIntent(events);
        String answer = extractAnswer(events);

        trackAccuracy(expectedIntent, actualIntent);

        log.info("[route_plan] msg={}, intent={}, correct={}, answerLen={}",
                message, actualIntent, expectedIntent.equals(actualIntent), answer.length());

        assertEquals(expectedIntent, actualIntent,
                "Intent should be " + expectedIntent + " for route_plan query: " + message);

        // No emoji in answer
        assertFalse(containsEmoji(answer),
                "Answer should not contain emoji for: " + message);
    }

    // ==========================================================================
    // B. Spot Question Intent (5 cases)
    // ==========================================================================

    static Stream<Arguments> spotQuestionCases() {
        return Stream.of(
                Arguments.of("灵山大佛有多高", "spot_question"),
                Arguments.of("梵宫有什么好玩的", "spot_question"),
                Arguments.of("门票多少钱", "spot_question"),
                Arguments.of("景区有哪些景点", "spot_question"),
                Arguments.of("退思园的历史背景", "spot_question")
        );
    }

    @ParameterizedTest(name = "[spot_question] \"{0}\"")
    @MethodSource("spotQuestionCases")
    void testSpotQuestion(String message, String expectedIntent) {
        List<SseEvent> events = postAndParseSse(message);
        String actualIntent = extractIntent(events);
        String answer = extractAnswer(events);

        trackAccuracy(expectedIntent, actualIntent);

        log.info("[spot_question] msg={}, intent={}, correct={}, answerLen={}",
                message, actualIntent, expectedIntent.equals(actualIntent), answer.length());

        assertEquals(expectedIntent, actualIntent,
                "Intent should be " + expectedIntent + " for spot_question query: " + message);

        // Answer must be non-empty and substantive
        assertFalse(answer.isBlank(), "Answer should not be blank for: " + message);
        assertTrue(answer.length() > 10,
                "Answer should be > 10 characters for spot_question, was: " + answer.length());

        // No emoji in answer
        assertFalse(containsEmoji(answer),
                "Answer should not contain emoji for: " + message);
    }

    // ==========================================================================
    // C. Complex Other Intent (5 cases)
    // ==========================================================================

    static Stream<Arguments> complexOtherCases() {
        return Stream.of(
                Arguments.of("你好", "complex_other"),
                Arguments.of("今天天气怎么样", "complex_other"),
                Arguments.of("今天星期几", "complex_other"),
                Arguments.of("你会做什么", "complex_other"),
                Arguments.of("讲个笑话", "complex_other")
        );
    }

    @ParameterizedTest(name = "[complex_other] \"{0}\"")
    @MethodSource("complexOtherCases")
    void testComplexOther(String message, String expectedIntent) {
        List<SseEvent> events = postAndParseSse(message);
        String actualIntent = extractIntent(events);
        String answer = extractAnswer(events);

        trackAccuracy(expectedIntent, actualIntent);

        log.info("[complex_other] msg={}, intent={}, correct={}, answerLen={}",
                message, actualIntent, expectedIntent.equals(actualIntent), answer.length());

        assertEquals(expectedIntent, actualIntent,
                "Intent should be " + expectedIntent + " for complex_other query: " + message);

        // E. No identity leak: must not leak internal implementation details
        String normalized = normalizeForLeakCheck(answer);
        for (String keyword : List.of("网页", "设计", "开发", "编写", "代码",
                "景区知识", "参考资料", "retrieved_docs", "retrievedocs")) {
            assertFalse(normalized.contains(keyword.toLowerCase()),
                    "Answer should not contain leak keyword '" + keyword
                            + "' for complex_other query: " + message
                            + "\nAnswer: " + answer);
        }

        // F. Answer quality: length <= 150 characters (per system prompt)
        assertTrue(answer.length() <= 150,
                "Answer should be <= 150 chars for complex_other, was " + answer.length()
                        + " chars.\nMessage: " + message + "\nAnswer: " + answer);

        // No emoji in answer
        assertFalse(containsEmoji(answer),
                "Answer should not contain emoji for: " + message);
    }

    // ==========================================================================
    // D. Out-of-scope rejection (3 cases)
    // ==========================================================================

    @Test
    void testReject_designWebsite() {
        rejectionTotal.incrementAndGet();
        List<SseEvent> events = postAndParseSse("帮我设计一个网站");
        String answer = extractAnswer(events);

        log.info("[reject] msg=帮我设计一个网站, answer={}", answer);
        assertFalse(answer.isBlank(), "Rejection answer should not be blank");

        boolean rejected = answer.contains("抱歉") || answer.contains("无法") || answer.contains("景区");
        if (rejected) rejectionCorrect.incrementAndGet();
        assertTrue(rejected, "Out-of-scope query '帮我设计一个网站' should be rejected"
                + "\nActual answer: " + answer);
        assertFalse(containsEmoji(answer), "Answer should not contain emoji");
    }

    @Test
    void testReject_writeMarketingCopy() {
        rejectionTotal.incrementAndGet();
        List<SseEvent> events = postAndParseSse("帮我写一段营销文案");
        String answer = extractAnswer(events);

        log.info("[reject] msg=帮我写一段营销文案, answer={}", answer);
        assertFalse(answer.isBlank(), "Rejection answer should not be blank");

        boolean rejected = answer.contains("抱歉") || answer.contains("无法") || answer.contains("景区");
        if (rejected) rejectionCorrect.incrementAndGet();
        assertTrue(rejected, "Out-of-scope query '帮我写一段营销文案' should be rejected"
                + "\nActual answer: " + answer);
        assertFalse(containsEmoji(answer), "Answer should not contain emoji");
    }

    @Test
    void testReject_teachCode() {
        rejectionTotal.incrementAndGet();
        List<SseEvent> events = postAndParseSse("教我写Python代码");
        String answer = extractAnswer(events);

        log.info("[reject] msg=教我写Python代码, answer={}", answer);
        assertFalse(answer.isBlank(), "Rejection answer should not be blank");

        boolean rejected = answer.contains("抱歉") || answer.contains("无法") || answer.contains("景区");
        if (rejected) rejectionCorrect.incrementAndGet();
        assertTrue(rejected, "Out-of-scope query '教我写Python代码' should be rejected"
                + "\nActual answer: " + answer);
        assertFalse(containsEmoji(answer), "Answer should not contain emoji");
    }

    // ==========================================================================
    // Accuracy Report
    // ==========================================================================

    @AfterAll
    static void printAccuracyReport() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("            AI Intent Classification Accuracy Report");
        System.out.println("=".repeat(70));
        System.out.printf("%-22s %10s %10s %10s%n", "Intent Type", "Total", "Correct", "Accuracy");
        System.out.println("-".repeat(70));

        int grandTotal = 0;
        int grandCorrect = 0;

        for (String intent : List.of("route_plan", "spot_question", "complex_other")) {
            int total = totalByIntent.getOrDefault(intent, new AtomicInteger()).get();
            int correct = correctByIntent.getOrDefault(intent, new AtomicInteger()).get();
            grandTotal += total;
            grandCorrect += correct;
            printRow(intent, total, correct);
        }
        printRow("out-of-scope", rejectionTotal.get(), rejectionCorrect.get());

        System.out.println("-".repeat(70));
        printRow("GRAND TOTAL", grandTotal + rejectionTotal.get(),
                grandCorrect + rejectionCorrect.get());

        System.out.println("=".repeat(70));
        System.out.println("Conditions:");
        System.out.println("  - MySQL, Redis, Redis Stack, DashScope API Key required");
        System.out.println("  - route_plan: intent=route_plan");
        System.out.println("  - spot_question: intent=spot_question, answer >10 chars");
        System.out.println("  - complex_other: intent=complex_other, answer <=150 chars, no leak");
        System.out.println("  - out-of-scope: answer contains rejection keywords");
        System.out.println("  - All answers must not contain emoji");
        System.out.println("=".repeat(70));
    }

    private static void printRow(String label, int total, int correct) {
        if (total == 0) {
            System.out.printf("%-22s %10s %10s %10s%n", label, "-", "-", "N/A");
        } else {
            double rate = (double) correct / total * 100;
            System.out.printf("%-22s %10d %10d %9.1f%%%n", label, total, correct, rate);
        }
    }
}
