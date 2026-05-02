package cn.edu.gdou.jingbanyou.tourist.service.impl;

import cn.edu.gdou.jingbanyou.tourist.service.ITranscribeService;
import com.alibaba.cloud.ai.dashscope.audio.transcription.DashScopeAudioTranscriptionOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.alibaba.cloud.ai.dashscope.audio.transcription.AudioTranscriptionModel;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * ASR 语音转文字服务
 *
 * 使用 DashScope Paraformer 模型将语音转换为文字
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscribeService implements ITranscribeService {

    /** DashScope Paraformer V1 模型 ID */
    private static final String MODEL_PARAFORMER_V1 = "paraformer-v1";

    private final AudioTranscriptionModel transcriptionModel;

    /**
     * 将音频转换为文字
     *
     * @param audioData 音频字节数据
     * @param fileName  文件名（用于推断格式，可为任意值）
     * @param language  语言提示，可为 null（默认中文）
     * @return 识别的文字
     */
    public String transcribe(byte[] audioData, String fileName, String language) {
        if (audioData == null || audioData.length == 0) {
            return "";
        }

        try {
            // 构造音频资源，文件名用于 DashScope 自动识别格式
            ByteArrayResource resource = new ByteArrayResource(audioData) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };

            // 构建转写选项
            DashScopeAudioTranscriptionOptions options = DashScopeAudioTranscriptionOptions.builder()
                    .model(MODEL_PARAFORMER_V1)
                    .languageHints(Collections.singletonList(language != null ? language : "zh"))
                    .punctuationPredictionEnabled(true)
                    .disfluencyRemovalEnabled(true)
                    .build();

            AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource, options);
            AudioTranscriptionResponse response = transcriptionModel.call(prompt);

            String text = response.getResult().getOutput();

            log.info("ASR 识别成功, 文件名={}, 音频大小={}KB, 识别文字长度={}",
                    fileName, audioData.length / 1024, text != null ? text.length() : 0);

            return text != null ? text : "";

        } catch (Exception e) {
            log.error("ASR 识别失败, 文件名={}", fileName, e);
            return "";
        }
    }
}
