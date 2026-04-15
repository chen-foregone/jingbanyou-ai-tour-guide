package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.xuyanwu.spring.file.storage.FileInfo;
import cn.xuyanwu.spring.file.storage.FileStorageService;
import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import reactor.core.publisher.Flux;
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
            DashScopeAudioSpeechOptions options = buildOptions(digitalHuman);
            log.info("[TTS] 开始合成, sessionId={}, text长度={}, digitalHuman={}", 
                    sessionId, text.length(), 
                    digitalHuman != null ? "ID=" + digitalHuman.getId() : "null");

            TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, options);
            Flux<TextToSpeechResponse> responseFlux = textToSpeechModel.stream(prompt);

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
                log.warn("[TTS] 返回空音频数据, sessionId={}", sessionId);
                return "";
            }

            String fileName = String.format("tts/%s/%d.mp3", sessionId, System.currentTimeMillis());
            String ossUrl = uploadToOss(audioBytes, fileName, "audio/mpeg");

            log.info("[TTS] 合成成功, sessionId={}, 文本长度={}, 音频大小={}KB, OSS URL={}",
                    sessionId, text.length(), audioBytes.length / 1024, ossUrl);

            return ossUrl;

        } catch (Exception e) {
            log.error("[TTS] 合成失败, sessionId={}, text={}", sessionId, text, e);
            
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("418")) {
                log.error("[TTS] 错误码418 - model与voice版本不匹配！");
                log.error("[TTS] cosyvoice-v3-flash 需要使用对应的音色代码");
                log.error("[TTS] 当前配置: model=cosyvoice-v3-flash, voice={}",
                        digitalHuman != null && digitalHuman.getTtsVoiceCode() != null
                            ? digitalHuman.getTtsVoiceCode() : "longxiaocheng");
            }
            
            return "";
        }
    }

    /**
     * 根据数字人配置构建 DashScope TTS 选项
     */
    private DashScopeAudioSpeechOptions buildOptions(DigitalHumanConfig digitalHuman) {
        String voice = "longxiaocheng";
        
        if (digitalHuman != null && digitalHuman.getTtsVoiceCode() != null
                && !digitalHuman.getTtsVoiceCode().isBlank()) {
            voice = digitalHuman.getTtsVoiceCode();
            log.info("[TTS] 使用数字人配置的音色: {}", voice);
        } else {
            log.warn("[TTS] 数字人配置或音色代码为空，使用默认音色: longxiaocheng (cosyvoice-v3-flash)");
        }

        DashScopeAudioSpeechOptions options = DashScopeAudioSpeechOptions.builder()
                .model("cosyvoice-v3-flash")
                .voice(voice)
                .build();

        log.info("[TTS] 最终配置: model=cosyvoice-v3-flash, voice={}", voice);
        
        return options;
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

    /**
     * 上传字节数组到 OSS
     *
     * @param data     文件字节数据
     * @param fileName OSS 上的文件路径名（不含 bucket 名）
     * @param mimeType MIME 类型
     * @return OSS 上的音频文件访问 URL
     */
    private String uploadToOss(byte[] data, String fileName, String mimeType) {
        log.info("[OSS] 开始上传, fileName={}, dataSize={}KB", fileName, data.length / 1024);
        try {
            FileInfo fileInfo = fileStorageService.of(data)
                    .setPath(fileName)
                    .setContentType(mimeType)
                    .upload();
            String url = fileInfo != null ? fileInfo.getUrl() : "";
            log.info("[OSS] 上传成功, URL={}", url);
            return url;
        } catch (Exception e) {
            log.error("[OSS] 上传失败, fileName={}, error={}", fileName, e.getMessage(), e);
            log.error("[OSS] 异常类型: {}", e.getClass().getName());
            if (e.getCause() != null) {
                log.error("[OSS] 根本原因: {}", e.getCause().getMessage());
            }
            throw e;
        }
    }
    }
