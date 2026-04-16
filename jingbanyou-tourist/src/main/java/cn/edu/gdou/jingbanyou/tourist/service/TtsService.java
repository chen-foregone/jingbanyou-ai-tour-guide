package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechModel;
import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TTS 语音合成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService {

    private final DashScopeAudioSpeechModel speechModel;

    @Value("${jingbanyou.tts.audio-dir:/tmp/tts}")
    private String audioDir;

    /**
     * 流式合成语音，返回音频 chunk 流
     *
     * @param text         合成文本
     * @param digitalHuman 数字人配置
     * @return 音频字节流
     */
    public Flux<byte[]> streamAudio(String text, DigitalHumanConfig digitalHuman) {
        if (text == null || text.isBlank()) {
            return Flux.empty();
        }

        String voice = resolveVoice(digitalHuman);

        log.info("[TTS-流式] 开始合成, text长度={}, voice={}", text.length(), voice);

        DashScopeAudioSpeechOptions options = DashScopeAudioSpeechOptions.builder()
                .model("cosyvoice-v3-flash")
                .voice(voice)
                .build();

        TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, options);

        return speechModel.stream(prompt)
                .filter(response -> response != null && response.getResult() != null)
                .map(response -> response.getResult().getOutput())
                .filter(chunk -> chunk != null && chunk.length > 0)
                .doOnComplete(() -> log.info("[TTS-流式] 合成完成"))
                .doOnError(e -> log.error("[TTS-流式] 合成失败", e))
                .doOnRequest(count -> log.info("[TTS-流式] 开始推送，当前请求数={}", count));
    }

    /**
     * 合成语音（文件方式，供非流式接口使用）
     *
     * @param text         合成文本
     * @param digitalHuman 数字人配置
     * @return 音频文件访问路径
     */
    public String synthesize(String text, DigitalHumanConfig digitalHuman) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String voice = resolveVoice(digitalHuman);

        log.info("[TTS] 开始合成, text长度={}, voice={}", text.length(), voice);

        try {
            DashScopeAudioSpeechOptions options = DashScopeAudioSpeechOptions.builder()
                    .model("cosyvoice-v3-flash")
                    .voice(voice)
                    .build();

            TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, options);

            ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorHolder = new AtomicReference<>();

            speechModel.stream(prompt)
                    .filter(response -> response != null && response.getResult() != null)
                    .map(response -> response.getResult().getOutput())
                    .filter(chunk -> chunk != null && chunk.length > 0)
                    .subscribe(
                            chunk -> {
                                try {
                                    baos.write(chunk);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            },
                            e -> {
                                errorHolder.set(e);
                                latch.countDown();
                            },
                            latch::countDown
                    );

            latch.await();

            if (errorHolder.get() != null) {
                log.error("[TTS] 合成失败: {}", errorHolder.get().getMessage(), errorHolder.get());
                return "";
            }

            byte[] audioBytes = baos.toByteArray();
            if (audioBytes.length == 0) {
                log.error("[TTS] 合成返回空数据");
                return "";
            }

            java.nio.file.Path audioPath = java.nio.file.Paths.get(audioDir,
                    java.util.UUID.randomUUID() + ".wav");
            java.nio.file.Files.createDirectories(audioPath.getParent());
            java.nio.file.Files.write(audioPath, audioBytes);

            String audioUrl = "/tts/" + audioPath.getFileName().toString();
            log.info("[TTS] 合成成功, 音频大小={}KB, 路径={}",
                    audioBytes.length / 1024, audioUrl);

            return audioUrl;

        } catch (Exception e) {
            log.error("[TTS] 合成失败: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * 解析音色代码，优先使用数字人配置，否则使用默认女声
     */
    private String resolveVoice(DigitalHumanConfig digitalHuman) {
        if (digitalHuman != null && digitalHuman.getTtsVoiceCode() != null
                && !digitalHuman.getTtsVoiceCode().isBlank()) {
            return digitalHuman.getTtsVoiceCode();
        }
        return "longanyang";
    }
}
