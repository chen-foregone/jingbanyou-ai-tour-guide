package cn.edu.gdou.jingbanyou.tourist.controller;

import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.enums.TouristErrorCode;
import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.manage.service.IDigitalHumanConfigService;
import cn.edu.gdou.jingbanyou.tourist.service.ITranscribeService;
import cn.edu.gdou.jingbanyou.tourist.service.ITtsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 语音交互
 *
 * @author jingbanyou
 */
@Slf4j
@RestController
@RequestMapping("/tourist")
@RequiredArgsConstructor
@Tag(name = "游客端-语音", description = "语音转文字、TTS 语音合成")
public class VoiceController extends BaseController {

    private final ITranscribeService transcribeService;
    private final ITtsService ttsService;
    private final IDigitalHumanConfigService digitalHumanConfigService;

    /**
     * 语音转文字
     *
     * @param file 音频文件
     * @param language 语言提示
     * @return 识别文本
     */
    @Operation(summary = "语音转文字", description = "上传音频文件，通过 ASR 识别为文本")
    @PostMapping("/voice/transcribe")
    public AjaxResult transcribe(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "language", required = false, defaultValue = "zh") String language) {
        if (file == null || file.isEmpty()) {
            return error(TouristErrorCode.T007);
        }

        try {
            byte[] audioData = file.getBytes();
            String text = transcribeService.transcribe(audioData, file.getOriginalFilename(), language);

            if (text.isEmpty()) {
                return error(TouristErrorCode.T008);
            }

            return success(Map.of("text", text));

        } catch (Exception e) {
            log.error("ASR 处理失败", e);
            return error(TouristErrorCode.T008, e.getMessage());
        }
    }

    /**
     * TTS 语音合成
     *
     * @param text      合成文本
     * @param scenicId  景区ID（可选，用于获取数字人配置）
     * @return 音频文件访问路径
     */
    @Operation(summary = "语音合成", description = "将文本合成为语音，返回音频文件访问路径")
    @GetMapping("/tts")
    public DeferredResult<AjaxResult> tts(
            @RequestParam String text,
            @RequestParam(required = false) Long scenicId) {

        if (text == null || text.isBlank()) {
            DeferredResult<AjaxResult> result = new DeferredResult<>();
            result.setResult(error(TouristErrorCode.T009));
            return result;
        }

        DigitalHumanConfig digitalHuman = null;
        if (scenicId != null) {
            digitalHuman = digitalHumanConfigService.getDefaultByScenicId(scenicId);
        }
        log.info("[TTS] text长度={}, voice={}",
                text.length(),
                digitalHuman != null ? digitalHuman.getTtsVoiceCode() : "默认");

        DeferredResult<AjaxResult> deferredResult = new DeferredResult<>(30000L);
        ttsService.synthesize(text, digitalHuman)
                .whenComplete((audioUrl, ex) -> {
                    if (ex != null) {
                        log.error("[TTS] 异步合成失败", ex);
                        deferredResult.setResult(error(TouristErrorCode.T010));
                    } else if (audioUrl == null || audioUrl.isBlank()) {
                        deferredResult.setResult(error(TouristErrorCode.T010));
                    } else {
                        deferredResult.setResult(success(Map.of("audioUrl", audioUrl)));
                    }
                });
        return deferredResult;
    }

    /**
     * TTS 音频文件访问
     *
     * @param filename 音频文件名
     * @return 音频文件
     */
    @Operation(summary = "获取 TTS 音频文件", description = "根据文件名返回合成的 TTS 音频文件")
    @GetMapping("/tts/{filename}")
    public ResponseEntity<byte[]> ttsAudio(@PathVariable String filename) throws IOException {
        // 路径遍历防护：文件名必须以 .wav 结尾，不含路径分隔符
        if (filename == null || !filename.toLowerCase().endsWith(".wav")) {
            return ResponseEntity.badRequest().build();
        }
        if (filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        String audioDir = ttsService.getAudioDir();
        Path audioDirPath = Paths.get(audioDir);
        Path audioPath = audioDirPath.resolve(filename).normalize();

        // 确保规范化后仍在允许目录内
        if (!audioPath.startsWith(audioDirPath)) {
            return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(audioPath)) {
            return ResponseEntity.notFound().build();
        }

        byte[] audioBytes = Files.readAllBytes(audioPath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(audioBytes);
    }
}
