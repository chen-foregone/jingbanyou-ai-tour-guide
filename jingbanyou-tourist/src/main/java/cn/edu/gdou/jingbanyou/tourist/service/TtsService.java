package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechModel;
import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * TTS 语音合成服务
 * <p>
 * 使用 DashScope CosyVoice 模型将文字转换为语音
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService {

    private final DashScopeAudioSpeechModel speechModel;

    @Value("${jingbanyou.tts.audio-dir:/tmp/tts}")
    private String audioDir;

    /**
     * 合成语音，返回音频文件访问路径
     *
     * @param text          合成文本
     * @param digitalHuman  数字人配置（可从中读取音色）
     * @return 音频文件访问路径
     */
    public String synthesize(String text, DigitalHumanConfig digitalHuman) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String voice = "longxiaocheng";  // 默认音色
        if (digitalHuman != null && digitalHuman.getTtsVoiceCode() != null
                && !digitalHuman.getTtsVoiceCode().isBlank()) {
            voice = digitalHuman.getTtsVoiceCode();
        }

        log.info("[TTS] 开始合成, text长度={}, voice={}", text.length(), voice);

        try {
            DashScopeAudioSpeechOptions options = DashScopeAudioSpeechOptions.builder()
                    .model("cosyvoice-v1")
                    .voice(voice)
                    .build();

            TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, options);

            // CosyVoice 不支持同步调用，必须用 stream()
            Flux<TextToSpeechResponse> responseFlux = speechModel.stream(prompt);

            // 收集所有音频数据
            byte[] audioBytes = responseFlux
                    .map(response -> {
                        if (response != null && response.getResult() != null) {
                            return response.getResult().getOutput();
                        }
                        return new byte[0];
                    })
                    .reduce(new byte[0], this::concatenateArrays)
                    .block();

            if (audioBytes == null || audioBytes.length == 0) {
                log.error("[TTS] 合成返回空数据");
                return "";
            }

            // 保存到本地
            Path audioPath = Paths.get(audioDir, UUID.randomUUID() + ".wav");
            Files.createDirectories(audioPath.getParent());
            Files.write(audioPath, audioBytes);

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
     * 合并两个字节数组
     */
    private byte[] concatenateArrays(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
