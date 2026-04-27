package cn.edu.gdou.jingbanyou.tourist;

import cn.edu.gdou.jingbanyou.manage.entity.Faq;
import cn.edu.gdou.jingbanyou.manage.mapper.FaqMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 问答接口集成测试
 * <p>
 * 测试 /api/tourist/chat 接口的 4 类核心场景：
 * 1. FAQ 命中测试（RAG 预检短路）
 * 2. 路线规划测试
 * 3. 景点知识问答测试
 * 4. 兜底闲聊测试
 * <p>
 * 前置条件：MySQL、Redis、Redis Stack、DashScope API Key 已就绪
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TouristChatIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired(required = false)
    private FaqMapper faqMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;

    // ========== 准确率统计 ==========
    private static final AtomicInteger faqTotal = new AtomicInteger(0);
    private static final AtomicInteger faqCorrect = new AtomicInteger(0);
    private static final AtomicInteger routeTotal = new AtomicInteger(0);
    private static final AtomicInteger routeCorrect = new AtomicInteger(0);
    private static final AtomicInteger spotTotal = new AtomicInteger(0);
    private static final AtomicInteger spotCorrect = new AtomicInteger(0);
    private static final AtomicInteger fallbackTotal = new AtomicInteger(0);
    private static final AtomicInteger fallbackCorrect = new AtomicInteger(0);

    private static final List<Faq> allFaqs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    @BeforeAll
    static void loadFaqs(@Autowired(required = false) FaqMapper mapper) {
        if (mapper == null) {
            log.warn("FaqMapper 未注入，跳过 FAQ 数据加载");
            return;
        }
        try {
            List<Faq> faqs = mapper.selectList(null);
            if (faqs == null || faqs.isEmpty()) {
                log.warn("数据库中没有 FAQ 数据，跳过 FAQ 测试");
                return;
            }
            allFaqs.addAll(faqs.stream()
                    .filter(f -> f.getStatus() != null && f.getStatus() == 1)
                    .toList());
            log.info("已加载 {} 条 FAQ（status=1）", allFaqs.size());
        } catch (Exception e) {
            log.warn("加载 FAQ 数据失败，跳过 FAQ 测试: {}", e.getMessage());
        }
    }

    // ==========================================================================
    // 1. FAQ 命中测试（RAG 预检短路）
    // ==========================================================================

    @TestFactory
    @DisplayName("[FAQ] 全量 FAQ 命中测试")
    Stream<DynamicTest> testFaqMatch() {
        if (allFaqs.isEmpty()) {
            return Stream.of(DynamicTest.dynamicTest("[跳过] 无 FAQ 数据",
                    () -> log.info("跳过 FAQ 测试：数据库中无 status=1 的 FAQ 记录")));
        }
        return allFaqs.stream()
                .map(faq -> DynamicTest.dynamicTest(
                        "[FAQ] " + truncate(faq.getQuestion(), 60),
                        () -> doTestFaqMatch(faq)
                ));
    }

    private void doTestFaqMatch(Faq faq) {
        faqTotal.incrementAndGet();

        Long scenicId = faq.getScenicId();
        String question = faq.getQuestion();
        String expectedAnswer = faq.getAnswer();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", question);
        body.put("sessionId", "test-faq-" + UUID.randomUUID());
        body.put("scenicId", scenicId);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, new HttpHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/tourist/chat",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode(), "HTTP 状态码应为 200");

        JsonNode root = parseJson(response.getBody());
        assertNotNull(root);
        assertEquals(200, root.path("code").asInt(), "业务 code 应为 200");

        JsonNode data = root.path("data");
        String intent = data.path("intent").asText("");
        String replyContent = data.path("reply").path("content").asText("");

        assertEquals("rag_prematch", intent,
                "FAQ 问题应命中 rag_prematch intent，question=" + question);

        boolean matched = answerContainsKeywords(replyContent, expectedAnswer);
        if (matched) {
            faqCorrect.incrementAndGet();
            log.debug("[FAQ 命中] question={} -> content={}", question, replyContent);
        } else {
            log.warn("[FAQ 未命中] question={}, expected={}, actual={}",
                    question, expectedAnswer, replyContent);
        }
        assertTrue(matched,
                "回复内容应包含 FAQ answer 的核心关键词。\n" +
                        "FAQ question=" + question + "\n" +
                        "expected keywords from answer=" + expectedAnswer + "\n" +
                        "actual reply=" + replyContent);
    }

    // ==========================================================================
    // 2. 路线规划测试
    // ==========================================================================

    private static final List<NamedCase> ROUTE_CASES = List.of(
            new NamedCase("从景区入口到大佛怎么走", "route_plan"),
            new NamedCase("帮我规划一条路线", "route_plan"),
            new NamedCase("从入口到博物馆怎么走", "route_plan"),
            new NamedCase("我想游览整个景区怎么走最快", "route_plan"),
            new NamedCase("推荐一条游览路线", "route_plan")
    );

    @TestFactory
    @DisplayName("[路线] 路线规划 intent 测试")
    Stream<DynamicTest> testRoutePlan() {
        return ROUTE_CASES.stream()
                .map(tc -> DynamicTest.dynamicTest("[路线] " + tc.question,
                        () -> doTestIntent(tc.question, tc.expectedIntent, routeTotal, routeCorrect,
                                (data) -> {
                                    JsonNode attachments = data.path("attachments");
                                    // pending 场景（缺参数）也视为正常响应
                                    String content = data.path("reply").path("content").asText().trim();
                                    if ("pending".equals(content)) {
                                        assertFalse(data.path("intent").asText().isEmpty(),
                                                "pending 场景下 intent 不应为空");
                                        return;
                                    }
                                    assertEquals(tc.expectedIntent, data.path("intent").asText(),
                                            "intent 应为 route_plan: " + tc.question);
                                    assertTrue(attachments.isArray() && attachments.size() > 0,
                                            "路线规划成功时 attachments 不应为空");
                                })));
    }

    // ==========================================================================
    // 3. 景点知识问答测试（HybridRetrieval）
    // ==========================================================================

    private static final List<NamedCase> SPOT_CASES = List.of(
            new NamedCase("这个景点的历史是什么", "spot_question"),
            new NamedCase("博物馆里有什么展品", "spot_question"),
            new NamedCase("大佛是什么时候建造的", "spot_question"),
            new NamedCase("景区开放时间是几点", "spot_question"),
            new NamedCase("有哪些值得看的景点", "spot_question")
    );

    @TestFactory
    @DisplayName("[景点] 景点知识问答 intent 测试")
    Stream<DynamicTest> testSpotQuestion() {
        return SPOT_CASES.stream()
                .map(tc -> DynamicTest.dynamicTest("[景点] " + tc.question,
                        () -> doTestIntent(tc.question, tc.expectedIntent, spotTotal, spotCorrect,
                                (data) -> {
                                    String intent = data.path("intent").asText();
                                    String replyContent = data.path("reply").path("content").asText("");
                                    assertEquals(tc.expectedIntent, intent,
                                            "intent 应为 spot_question: " + tc.question);
                                    assertFalse(replyContent.isBlank(), "回复内容不应为空");
                                })));
    }

    // ==========================================================================
    // 4. 兜底闲聊测试
    // ==========================================================================

    private static final List<NamedCase> CHAT_CASES = List.of(
            new NamedCase("今天天气怎么样", "complex_other"),
            new NamedCase("你是谁", "complex_other"),
            new NamedCase("给我讲个笑话", "complex_other"),
            new NamedCase("你好啊", "complex_other"),
            new NamedCase("你觉得我今天穿什么颜色好", "complex_other")
    );

    @TestFactory
    @DisplayName("[闲聊] 兜底闲聊 intent 测试")
    Stream<DynamicTest> testGeneralChat() {
        return CHAT_CASES.stream()
                .map(tc -> DynamicTest.dynamicTest("[闲聊] " + tc.question,
                        () -> doTestIntent(tc.question, tc.expectedIntent, fallbackTotal, fallbackCorrect,
                                (data) -> {
                                    String intent = data.path("intent").asText();
                                    String replyContent = data.path("reply").path("content").asText("");
                                    assertEquals(tc.expectedIntent, intent,
                                            "intent 应为 complex_other: " + tc.question);
                                    assertFalse(replyContent.isBlank(), "回复内容不应为空");
                                })));
    }

    // ==========================================================================
    // 通用 intent 测试执行器
    // ==========================================================================

    private void doTestIntent(String question, String expectedIntent,
                              AtomicInteger total, AtomicInteger correct,
                              IntentAssertion assertion) {
        total.incrementAndGet();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", question);
        body.put("sessionId", "test-" + UUID.randomUUID());
        body.put("scenicId", 1L);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, new HttpHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/tourist/chat",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode root = parseJson(response.getBody());
        assertNotNull(root);
        assertEquals(200, root.path("code").asInt());

        JsonNode data = root.path("data");
        String intent = data.path("intent").asText();
        String replyContent = data.path("reply").path("content").asText("");

        boolean intentCorrect = expectedIntent.equals(intent);
        if (intentCorrect) {
            correct.incrementAndGet();
        }

        log.info("[{}] question={}, intent={}, correct={}",
                expectedIntent, question, intent, intentCorrect);

        // 执行具体断言
        assertion.accept(data);
    }

    // ==========================================================================
    // 辅助方法
    // ==========================================================================

    /**
     * 判断 AI 回复是否包含 FAQ answer 的核心关键词
     * 策略一：reply 包含 answer 全文
     * 策略二：answer 中长度 >= 6 的连续子串出现在 reply 中
     * 策略三：分词后关键词重叠度 >= 50%
     */
    private boolean answerContainsKeywords(String reply, String answer) {
        if (reply == null || answer == null) return false;
        if (reply.isBlank() || answer.isBlank()) return false;

        String replyNorm = normalize(reply);
        String answerNorm = normalize(answer);

        // 策略一：直接包含
        if (replyNorm.contains(answerNorm)) return true;

        // 策略二：answer 中 >= 6 字符的连续片段出现在 reply 即命中
        for (String chunk : extractChunks(answerNorm, 6)) {
            if (replyNorm.contains(chunk)) {
                log.debug("[关键词命中] chunk={}", chunk);
                return true;
            }
        }

        // 策略三：关键词重叠度 >= 50%
        Set<String> replyWords = tokenize(replyNorm);
        Set<String> answerWords = tokenize(answerNorm);
        if (answerWords.isEmpty()) return false;

        Set<String> intersection = new HashSet<>(replyWords);
        intersection.retainAll(answerWords);
        double overlap = (double) intersection.size() / answerWords.size();
        if (overlap >= 0.5) {
            log.debug("[重叠度命中] overlap={}, intersection={}", overlap, intersection);
            return true;
        }

        return false;
    }

    /**
     * 提取文本中长度 >= minLen 的连续非空白子串
     */
    private List<String> extractChunks(String text, int minLen) {
        List<String> chunks = new ArrayList<>();
        for (String part : text.split("\\s+")) {
            if (part.length() >= minLen) {
                chunks.add(part);
            }
        }
        return chunks;
    }

    /**
     * 简体中文分词（按空格和标点切分，去除单字）
     */
    private Set<String> tokenize(String text) {
        Set<String> words = new HashSet<>();
        if (text == null || text.isBlank()) return words;
        for (String t : text.split("[\\s\\p{Punct}]+")) {
            if (t.length() >= 2) {
                words.add(t);
            }
        }
        return words;
    }

    /**
     * 归一化：去除空白、换行、转小写
     */
    private String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * 截断字符串用于测试名称
     */
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            fail("JSON 解析失败: " + e.getMessage() + "\n原始内容: " + json);
            return null;
        }
    }

    // ==========================================================================
    // 准确率报告
    // ==========================================================================

    @AfterAll
    static void printAccuracyReport(TestInfo info) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("                    AI 问答接口准确率测试报告");
        System.out.println("=".repeat(70));
        System.out.printf("%-20s %10s %10s %10s%n", "测试类别", "总数", "正确", "准确率");
        System.out.println("-".repeat(70));

        printRow("FAQ 命中测试", faqTotal.get(), faqCorrect.get());
        printRow("路线规划测试", routeTotal.get(), routeCorrect.get());
        printRow("景点知识问答测试", spotTotal.get(), spotCorrect.get());
        printRow("兜底闲聊测试", fallbackTotal.get(), fallbackCorrect.get());

        System.out.println("-".repeat(70));
        int totalSum = faqTotal.get() + routeTotal.get() + spotTotal.get() + fallbackTotal.get();
        int correctSum = faqCorrect.get() + routeCorrect.get() + spotCorrect.get() + fallbackCorrect.get();
        printRow("【总计】", totalSum, correctSum);

        System.out.println("=".repeat(70));
        System.out.println("说明：");
        System.out.println("  - FAQ 命中：intent=rag_prematch 且回复包含 answer 核心关键词");
        System.out.println("  - 路线规划：intent=route_plan 且 attachments 非空（或 pending 引导语）");
        System.out.println("  - 景点问答：intent=spot_question 且回复非空");
        System.out.println("  - 兜底闲聊：intent=complex_other 且回复非空");
        System.out.println("  - 前置条件：MySQL、Redis、Redis Stack、DashScope API Key 已就绪");
        System.out.println("=".repeat(70));
    }

    private static void printRow(String label, int total, int correct) {
        if (total == 0) {
            System.out.printf("%-20s %10s %10s %10s%n", label, "-", "-", "N/A");
        } else {
            double rate = (double) correct / total * 100;
            System.out.printf("%-20s %10d %10d %9.1f%%%n", label, total, correct, rate);
        }
    }

    // ==========================================================================
    // 内部类型
    // ==========================================================================

    /**
     * 命名前缀测试用例
     */
    record NamedCase(String question, String expectedIntent) {}

    /**
     * intent 测试断言接口
     */
    @FunctionalInterface
    interface IntentAssertion {
        void accept(JsonNode data);
    }
}
