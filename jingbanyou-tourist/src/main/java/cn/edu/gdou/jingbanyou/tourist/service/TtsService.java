package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.stereotype.Service;

/**
 * TTS 语音合成服务
 * <p>
 * 使用 DashScope 通义语音合成（TTS），生成的音频文件上传至阿里云 OSS
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService {

    private final TextToSpeechModel textToSpeechModel;
    private final FileStorageService fileStorageService;

    /**
     * 将文本合成为语音，返回 OSS 访问 URL
     *
     * @param text          要合成的文本
     * @param sessionId     会话 ID（用于生成文件名）
     * @param digitalHuman  数字人配置（可从中读取音色等参数，为 null 时使用默认音色）
     * @return OSS 上的音频文件访问 URL
     */
    public String synthesize(String text, String sessionId, DigitalHumanConfig digitalHuman) {
        if (text == null || text.isBlank()) {
            return "";
        }

        try {
            // 1. 构建 TTS 选项，优先使用数字人配置中的音色
            DashScopeAudioSpeechOptions options = buildOptions(digitalHuman);

            // 2. 调用 TTS 生成音频
            TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, options);
            TextToSpeechResponse response = textToSpeechModel.call(prompt);

            byte[] audioBytes = response.getResult().getOutput();

            if (audioBytes == null || audioBytes.length == 0) {
                log.warn("TTS 返回空音频数据, sessionId={}", sessionId);
                return "";
            }

            // 3. 上传至 OSS
            String fileName = String.format("tts/%s/%d.mp3", sessionId, System.currentTimeMillis());
            String ossUrl = uploadToOss(audioBytes, fileName, "audio/mpeg");

            log.info("TTS 合成成功, sessionId={}, 文本长度={}, 音频大小={}KB, OSS URL={}",
                    sessionId, text.length(), audioBytes.length / 1024, ossUrl);

            return ossUrl;

        } catch (Exception e) {
            log.error("TTS 合成失败, sessionId={}", sessionId, e);
            return "";
        }
    }

    /**
     * 根据数字人配置构建 DashScope TTS 选项
     */
    private DashScopeAudioSpeechOptions buildOptions(DigitalHumanConfig digitalHuman) {
        DashScopeAudioSpeechOptions.Builder builder = DashScopeAudioSpeechOptions.builder()
                .model("cosyvoice-v1")
                .format("mp3")
                .speed(1.0)
                .sampleRate(22050);

        // 如果有数字人音色代码，使用它覆盖默认值
        if (digitalHuman != null && digitalHuman.getTtsVoiceCode() != null
                && !digitalHuman.getTtsVoiceCode().isBlank()) {
            builder.voice(digitalHuman.getTtsVoiceCode());
        }

        return builder.build();
    }

    /**
     * 上传字节数组到 OSS
     *
     * @param data     文件字节数据
     * @param fileName OSS 上的文件路径名（不含 bucket 名）
     * @param mimeType MIME 类型
     * @return OSS 访问 URL
     */
    private String uploadToOss(byte[] data, String fileName, String mimeType) {
        // x-file-storage 2.1.0: FileStorageService.of() returns UploadPretreatment
        FileInfo fileInfo = fileStorageService.of(data)
                .setPath(fileName)
                .setContentType(mimeType)
                .upload();
        return fileInfo != null ? fileInfo.getUrl() : "";
    }
}
