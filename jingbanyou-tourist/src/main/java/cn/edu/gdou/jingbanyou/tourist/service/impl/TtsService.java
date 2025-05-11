package cn.edu.gdou.jingbanyou.tourist.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.tourist.service.ITtsService;
import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechModel;
import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.util.StringUtils;

/**
 * TTS 语音合成服务
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService implements ITtsService {

    private final DashScopeAudioSpeechModel speechModel;
    private final Cache<String, String> audioCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(500)
            .build();

    @Value("${jingbanyou.tts.audio-dir:#{null}}")
    private String audioDir;

    @jakarta.annotation.PostConstruct
    public void init() {
        if (!StringUtils.hasText(audioDir)) {
            audioDir = System.getProperty("java.io.tmpdir") + "/tts";
        }
        validateAndCreateAudioDir();
    }

    /**
     * 验证音频目录是否存在、是否可写，不存在时自动创建
     */
    private void validateAndCreateAudioDir() {
        Path dir = Paths.get(audioDir);
        if (Files.exists(dir)) {
            if (!Files.isDirectory(dir)) {
                throw new IllegalStateException("[TTS] audio-dir 不是目录: " + audioDir);
            }
            if (!Files.isWritable(dir)) {
                throw new IllegalStateException("[TTS] audio-dir 不可写: " + audioDir);
            }
            log.info("[TTS] audio-dir 验证通过: {}", audioDir);
        } else {
            try {
                Files.createDirectories(dir);
                log.info("[TTS] audio-dir 已自动创建: {}", audioDir);
            } catch (IOException e) {
                throw new IllegalStateException("[TTS] 无法创建 audio-dir: " + audioDir, e);
            }
        }
    }

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
     * 合成语音（文件方式，异步）
     *
     * @param text         合成文本
     * @param digitalHuman 数字人配置
     * @return 音频文件访问路径的 CompletableFuture
     */
    @CircuitBreaker(name = "dashscope-tts", fallbackMethod = "synthesizeFallback")
    public CompletableFuture<String> synthesize(String text, DigitalHumanConfig digitalHuman) {
        if (text == null || text.isBlank()) {
            return CompletableFuture.completedFuture("");
        }

        String voice = resolveVoice(digitalHuman);
        String cacheKey = md5(text + ":" + voice);

        String cached = audioCache.getIfPresent(cacheKey);
        if (cached != null && !cached.isBlank()) {
            log.info("[TTS] 缓存命中, cacheKey={}", cacheKey);
            return CompletableFuture.completedFuture(cached);
        }

        log.info("[TTS] 开始合成, text长度={}, voice={}", text.length(), voice);

        return CompletableFuture.supplyAsync(() -> {
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

                Path audioPath = Paths.get(audioDir, UUID.randomUUID() + ".wav");
                Files.createDirectories(audioPath.getParent());
                Files.write(audioPath, audioBytes);

                String audioUrl = "/tts/" + audioPath.getFileName().toString();
                audioCache.put(cacheKey, audioUrl);
                log.info("[TTS] 合成成功, 音频大小={}KB, 路径={}",
                        audioBytes.length / 1024, audioUrl);

                return audioUrl;

            } catch (Exception e) {
                log.error("[TTS] 合成失败: {}", e.getMessage(), e);
                return "";
            }
        });
    }

    /**
     * TTS 熔断降级方法
     */
    private CompletableFuture<String> synthesizeFallback(String text, DigitalHumanConfig digitalHuman, Throwable t) {
        log.warn("[TTS] 熔断降级: {}", t.getMessage());
        return CompletableFuture.completedFuture("");
    }

    /**
     * 解析音色代码，优先使用数字人配置，否则使用默认女声
     *
     * @param digitalHuman 数字人配置
     * @return 音色代码
     */
    private String resolveVoice(DigitalHumanConfig digitalHuman) {
        if (digitalHuman != null && digitalHuman.getTtsVoiceCode() != null
                && !digitalHuman.getTtsVoiceCode().isBlank()) {
            return digitalHuman.getTtsVoiceCode();
        }
        return "longanyang";
    }

    /**
     * 计算 MD5 摘要
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    @Override
    public String getAudioDir() {
        return audioDir;
    }
}
