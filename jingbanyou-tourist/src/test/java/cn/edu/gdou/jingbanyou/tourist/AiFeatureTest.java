//package cn.edu.gdou.jingbanyou.tourist;
//
//import cn.edu.gdou.jingbanyou.tourist.service.TtsService;
//import cn.edu.gdou.jingbanyou.tourist.service.TranscribeService;
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
//        // byte[] audioData = Files.readAllBytes(Paths.get("test.wav"));
//        // String text = transcribeService.transcribe(audioData, "test.wav", "zh");
//
//        log.info("ASR 测试需要音频文件数据，请提供测试音频路径");
//    }
//}
