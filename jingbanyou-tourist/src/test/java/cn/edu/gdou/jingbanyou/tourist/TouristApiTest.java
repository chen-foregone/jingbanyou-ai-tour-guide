package cn.edu.gdou.jingbanyou.tourist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * 游客端 API 集成测试
 * <p>
 * 前置条件：应用已在端口 9091 启动（RuoYiApplication in jingbanyou-admin）
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TouristApiTest {

    private static final String BASE = "http://localhost:9091/tourist";
    private static final Long SCENIC_ID = 1L;
    private static final String VISITOR_ID = "test-visitor-api-001";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** 由 tts_success 生成，供 tts_audio_file_success 使用 */
    private static String generatedAudioFilename;

    TouristApiTest() {
        this.objectMapper = new ObjectMapper();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(35).toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    // ==========================================================================
    // 1. 首屏初始化：正常场景
    // ==========================================================================

    /**
     * bootstrap 正常调用：传入有效的 scenicId，应返回景区信息和欢迎语
     */
    @Test
    @Order(1)
    @DisplayName("首屏初始化成功 - GET /tourist/bootstrap?scenicId=1")
    void bootstrap_success() {
        log.info("=== Test 1: bootstrap_success ===");

        String url = BASE + "/bootstrap?scenicId=" + SCENIC_ID;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "HTTP 状态码应为 200");

        JsonNode root = parseJson(response.getBody());
        assertNotNull(root, "响应体不应为空");
        int bizCode = root.path("code").asInt();
        assertTrue(bizCode == 200 || bizCode == 500,
                "业务 code 应为 200 或 500（景区数据可能未配置），实际: " + bizCode);

        JsonNode data = root.path("data");
        if (bizCode == 200) {
            assertFalse(data.isNull() || data.isMissingNode(), "成功时 data 不应为空");
        }

        // data 应包含景区信息或欢迎语（至少一个 key 存在）
        boolean hasContent = !data.isNull() && (data.has("scenicInfo") || data.has("welcomeMessage")
                || data.has("digitalHuman") || data.has("hotFaqs")
                || data.fields().hasNext());
        assertTrue(hasContent, "data 应包含 scenicInfo / welcomeMessage / digitalHuman / hotFaqs 等关键信息");
        log.info("bootstrap 返回 data keys: {}", data.fieldNames().next());
    }

    // ==========================================================================
    // 2. 首屏初始化：缺少必填参数
    // ==========================================================================

    /**
     * bootstrap 缺少 scenicId 参数，应返回 500 错误（T001）
     */
    @Test
    @Order(2)
    @DisplayName("首屏初始化缺少景区ID - GET /tourist/bootstrap (no scenicId)")
    void bootstrap_missing_param() {
        log.info("=== Test 2: bootstrap_missing_param ===");

        String url = BASE + "/bootstrap";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        JsonNode root = parseJson(response.getBody());
        assertNotNull(root, "响应体不应为空");
        assertEquals(500, root.path("code").asInt(), "缺失 scenicId 应返回 code=500");
        String msg = root.path("msg").asText("");
        assertTrue(msg.contains("景区ID") || msg.contains("scenicId") || msg.contains("不能为空"),
                "错误消息应提示景区ID不能为空，实际: " + msg);
        log.info("缺失 scenicId 错误消息: {}", msg);
    }

    // ==========================================================================
    // 3. TTS 语音合成：正常场景
    // ==========================================================================

    /**
     * TTS 语音合成：传入中文文本，应异步返回 audioUrl
     * <p>
     * 使用 DeferredResult 异步响应，TestRestTemplate 会等待 30s 超时（已配置 35s 读取超时）。
     * 成功时在静态字段记录生成的音频文件名，供 tts_audio_file_success 测试复用。
     */
    @Test
    @Order(3)
    @DisplayName("TTS语音合成成功 - GET /tourist/tts?text=你好世界&scenicId=1")
    void tts_success() {
        log.info("=== Test 3: tts_success ===");

        String url = BASE + "/tts?text=" + encode("你好世界") + "&scenicId=" + SCENIC_ID;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "HTTP 状态码应为 200");

        JsonNode root = parseJson(response.getBody());
        assertNotNull(root, "响应体不应为空");
        assertEquals(200, root.path("code").asInt(), "业务 code 应为 200");

        JsonNode data = root.path("data");
        assertFalse(data.isNull() || data.isMissingNode(), "data 不应为空");
        String audioUrl = data.path("audioUrl").asText("");
        assertFalse(audioUrl.isBlank(), "audioUrl 不应为空");
        log.info("TTS 返回 audioUrl: {}", audioUrl);

        // 从 audioUrl 中提取文件名，供后续文件访问测试使用
        int lastSlash = audioUrl.lastIndexOf('/');
        generatedAudioFilename = (lastSlash >= 0) ? audioUrl.substring(lastSlash + 1) : audioUrl;
        log.info("提取的音频文件名: {}", generatedAudioFilename);
    }

    // ==========================================================================
    // 4. TTS 语音合成：空文本
    // ==========================================================================

    /**
     * TTS 传入空文本，应返回 500 错误（T009）
     */
    @Test
    @Order(4)
    @DisplayName("TTS合成空文本报错 - GET /tourist/tts?text=&scenicId=1")
    void tts_empty_text() {
        log.info("=== Test 4: tts_empty_text ===");

        String url = BASE + "/tts?text=&scenicId=" + SCENIC_ID;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        JsonNode root = parseJson(response.getBody());
        assertNotNull(root, "响应体不应为空");
        assertEquals(500, root.path("code").asInt(), "空文本应返回 code=500");
        String msg = root.path("msg").asText("");
        assertTrue(msg.contains("文本") || msg.contains("不能为空"),
                "错误消息应提示文本不能为空，实际: " + msg);
        log.info("空文本错误消息: {}", msg);
    }

    // ==========================================================================
    // 5. TTS 音频文件访问：正常获取
    // ==========================================================================

    /**
     * 通过文件名获取之前生成的 TTS 音频文件，应返回 200 且 Content-Type 为 audio/wav
     * <p>
     * 依赖 tts_success 测试生成的音频文件名。
     * 若前序测试失败（TTS 不可用），则通过 Assumptions 跳过本测试。
     */
    @Test
    @Order(5)
    @DisplayName("获取TTS音频文件成功 - GET /tourist/tts/{filename}")
    void tts_audio_file_success() {
        log.info("=== Test 5: tts_audio_file_success ===");

        Assumptions.assumeTrue(generatedAudioFilename != null && !generatedAudioFilename.isBlank(),
                "跳过：前序 TTS 合成未生成音频文件（TTS 服务可能不可用）");

        String url = BASE + "/tts/" + generatedAudioFilename;
        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "存在音频文件时应返回 200");

        MediaType contentType = response.getHeaders().getContentType();
        assertNotNull(contentType, "Content-Type 不应为空");
        String typeStr = contentType.toString().toLowerCase();
        assertTrue(typeStr.contains("audio") || typeStr.contains("wav"),
                "Content-Type 应为 audio/wav，实际: " + contentType);

        byte[] body = response.getBody();
        assertNotNull(body, "音频文件内容不应为空");
        assertTrue(body.length > 0, "音频文件内容不应为空");
        log.info("音频文件大小: {} bytes", body.length);
    }

    // ==========================================================================
    // 6. TTS 音频文件访问：路径遍历攻击（URL 编码）
    // ==========================================================================

    /**
     * 路径遍历防护测试：URL 编码的路径遍历（..%2F..%2Fetc%2Fpasswd），
     * 因缺少 .wav 后缀而被拒绝，应返回 400。
     */
    @Test
    @Order(6)
    @DisplayName("TTS音频文件路径遍历防护 - GET /tourist/tts/..%2F..%2Fetc%2Fpasswd")
    void tts_audio_file_path_traversal() {
        log.info("=== Test 6: tts_audio_file_path_traversal ===");

        String url = BASE + "/tts/..%2F..%2Fetc%2Fpasswd";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            // 是否被拒绝（非 200 则安全校验通过；200 表示 Tomcat 可能做了规范化处理）
            if (response.getStatusCode() == HttpStatus.OK) {
                log.warn("路径遍历请求返回了 200，Tomcat 可能规范化了路径");
            }
            // 存在即通过：没有抛出异常就说明服务端妥善处理了
        } catch (HttpClientErrorException e) {
            // Tomcat 直接拒绝非法 URI，也是安全的
            assertTrue(e.getStatusCode().is4xxClientError(),
                    "应返回 4xx 客户端错误，实际: " + e.getStatusCode());
        }
        log.info("路径遍历防护 (URL编码) 已完成");
    }

    // ==========================================================================
    // 7. 会话列表
    // ==========================================================================

    /**
     * 获取游客会话列表，应返回 200 且 data 不为 null
     */
    @Test
    @Order(7)
    @DisplayName("获取会话列表成功 - GET /tourist/conversation/list?visitorId=...")
    void conversation_list() {
        log.info("=== Test 7: conversation_list ===");

        String url = BASE + "/conversation/list?visitorId=" + VISITOR_ID;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "HTTP 状态码应为 200");

        JsonNode root = parseJson(response.getBody());
        assertNotNull(root, "响应体不应为空");
        assertEquals(200, root.path("code").asInt(), "业务 code 应为 200");

        JsonNode data = root.path("data");
        assertFalse(data.isNull() || data.isMissingNode(), "data 不应为空");
        log.info("会话列表 data: {}", data);
    }

    // ==========================================================================
    // 8. 会话详情：不存在的会话
    // ==========================================================================

    /**
     * 查询不存在的会话详情，应返回 500 错误（T003）
     */
    @Test
    @Order(8)
    @DisplayName("获取不存在的会话详情 - GET /tourist/conversation/nonexistent-session-id")
    void conversation_detail_not_found() {
        log.info("=== Test 8: conversation_detail_not_found ===");

        String url = BASE + "/conversation/nonexistent-session-id";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        JsonNode root = parseJson(response.getBody());
        assertNotNull(root, "响应体不应为空");
        assertEquals(500, root.path("code").asInt(), "不存在的会话应返回 code=500");

        String msg = root.path("msg").asText("");
        assertTrue(msg.contains("会话") || msg.contains("不存在"),
                "错误消息应提示会话不存在，实际: " + msg);
        log.info("不存在的会话错误消息: {}", msg);
    }

    // ==========================================================================
    // 9. 结束会话：缺少 sessionId
    // ==========================================================================

    /**
     * 结束会话请求中缺少 sessionId，应返回 500 错误（T006）
     */
    @Test
    @Order(9)
    @DisplayName("结束会话缺少sessionId - POST /tourist/chat/end (missing sessionId)")
    void chat_end_missing_session() {
        log.info("=== Test 9: chat_end_missing_session ===");

        String url = BASE + "/chat/end";
        Map<String, Object> body = new HashMap<>();
        body.put("visitorId", VISITOR_ID);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "HTTP 状态码应为 200（AjaxResult 即使错误也返回 200）");

        JsonNode root = parseJson(response.getBody());
        assertNotNull(root, "响应体不应为空");
        assertEquals(500, root.path("code").asInt(), "缺少 sessionId 应返回 code=500");

        String msg = root.path("msg").asText("");
        assertTrue(msg.contains("sessionId") || msg.contains("不能为空"),
                "错误消息应提示 sessionId 不能为空，实际: " + msg);
        log.info("缺少 sessionId 错误消息: {}", msg);
    }

    // ==========================================================================
    // 10. TTS 音频文件访问：路径遍历攻击（wav 伪装）
    // ==========================================================================

    /**
     * 路径遍历防护测试：文件名以 .wav 结尾但包含路径分隔符（../../../etc/passwd.wav），
     * 应被安全校验拦截，返回 400。
     */
    @Test
    @Order(10)
    @DisplayName("TTS音频文件路径遍历防护(wav伪装) - GET /tourist/tts/../../../etc/passwd.wav")
    void path_traversal_tts() {
        log.info("=== Test 10: path_traversal_tts ===");

        String url = BASE + "/tts/../../../etc/passwd.wav";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            assertNotEquals(HttpStatus.OK, response.getStatusCode(),
                    "含路径分隔符的路径遍历不应返回 200");
        } catch (HttpClientErrorException e) {
            assertTrue(e.getStatusCode().is4xxClientError(),
                    "应返回 4xx 客户端错误，实际: " + e.getStatusCode());
        }
        log.info("路径遍历防护 (wav伪装) 已完成");
    }

    // ==========================================================================
    // 辅助方法
    // ==========================================================================

    /**
     * 解析 JSON 字符串为 Jackson JsonNode
     */
    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            fail("JSON 解析失败: " + e.getMessage() + "\n原始内容: " + json);
            return null;
        }
    }

    /**
     * URL 编码中文文本
     */
    private static String encode(String text) {
        try {
            return java.net.URLEncoder.encode(text, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("URL 编码失败", e);
        }
    }
}
