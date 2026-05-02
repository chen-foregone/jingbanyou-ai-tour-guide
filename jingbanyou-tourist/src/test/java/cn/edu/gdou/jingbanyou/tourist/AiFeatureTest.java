//package cn.edu.gdou.jingbanyou.tourist;
//
//import cn.edu.gdou.jingbanyou.tourist.service.impl.TtsService;
//import cn.edu.gdou.jingbanyou.tourist.service.impl.TranscribeService;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//
///**
// * AI 功能集成测试
// *
// * @author JingbanYou Team
// * @date 2026-04-02
// */
//@Slf4j
//@SpringBootTest
//public class AiFeatureTest {
//
//    @Autowired
//    private TtsService ttsService;
//
//    @Autowired
//    private TranscribeService transcribeService;
//
//    /**
//     * 测试语音合成（TTS）
//     */
//    @Test
//    public void testTts() {
//        log.info("开始测试 TTS 语音合成...");
//
//        String text = "欢迎来到景区，我是您的专属 AI 导游。";
//        String sessionId = "test-session-tts";
//
//        String audioUrl = ttsService.synthesize(text, sessionId, null);
//
//        log.info("文本：{}", text);
//        log.info("音频 URL：{}", audioUrl);
//    }
//
//    /**
//     * 测试语音转文字（ASR）
//     */
//    @Test
//    public void testTranscribe() {
//        log.info("开始测试 ASR 语音转文字...");
//
//        // TODO: 需要提供真实的音频文件路径或 byte[] 数据
//        // 测试方法：在集成测试中使用真实音频文件，或使用 @MockBean 模拟 AudioTranscriptionModel
//        // 当前代码（已注释）是 ASR 测试的正确写法示例，启用时替换为真实文件路径
//        // byte[] audioData = Files.readAllBytes(Paths.get("test.wav"));
//        // String text = transcribeService.transcribe(audioData, "test.wav", "zh");
//
//        log.info("ASR 测试需要音频文件数据，请提供测试音频路径");
//    }
//}
