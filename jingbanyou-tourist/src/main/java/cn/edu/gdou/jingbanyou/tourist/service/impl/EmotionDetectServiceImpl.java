package cn.edu.gdou.jingbanyou.tourist.service.impl;

import cn.edu.gdou.jingbanyou.tourist.service.IEmotionDetectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 情感检测服务实现
 * 异步调用 AI 模型，基于当前消息 + 历史上下文进行情感分析
 * 结果暂存 Redis，会话结束时由 ChatMemoryService 写入 MySQL
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionDetectServiceImpl implements IEmotionDetectService {

    private final ChatClient.Builder chatClientBuilder;
    private final RedisTemplate<String, Object> chatMetadataRedisTemplate;
    private final ObjectMapper objectMapper;
    @Qualifier("emotionDetectionExecutor")
    private final Executor emotionDetectionExecutor;

    private static final String EMOTION_KEY_PREFIX = "chat:emotion:";

    @Value("${jingbanyou.emotion.positive-words:谢谢|感谢|很好|不错|满意|喜欢|棒|厉害|专业|帮了大忙|解决了|推荐|点赞|太好了}")
    private String positiveWordsPattern;

    @Value("${jingbanyou.emotion.negative-words:不行|不好|失望|太差|垃圾|没用|浪费时间|讨厌|不满意|差评|无语|烦|抱怨|投诉}")
    private String negativeWordsPattern;

    @Value("classpath:prompts/emotion-detect-system-prompt.txt")
    private Resource systemPromptResource;

    @Value("classpath:prompts/emotion-detect-user-prompt-template.txt")
    private Resource userPromptTemplateResource;

    private Pattern positiveWords;
    private Pattern negativeWords;
    private String systemPrompt;
    private String userPromptTemplate;

    @PostConstruct
    public void init() {
        this.positiveWords = Pattern.compile(positiveWordsPattern, Pattern.CASE_INSENSITIVE);
        this.negativeWords = Pattern.compile(negativeWordsPattern, Pattern.CASE_INSENSITIVE);
        try {
            this.systemPrompt = StreamUtils.copyToString(systemPromptResource.getInputStream(), StandardCharsets.UTF_8);
            this.userPromptTemplate = StreamUtils.copyToString(userPromptTemplateResource.getInputStream(), StandardCharsets.UTF_8);
            log.info("[情感检测] 已加载 Prompt 模板: system={}chars, user={}chars",
                    systemPrompt.length(), userPromptTemplate.length());
        } catch (Exception e) {
            throw new IllegalStateException("[情感检测] 无法加载 Prompt 模板", e);
        }
    }

    @Override
    public void detectEmotionAsync(String sessionId, String currentMessage, String chatHistory) {
        CompletableFuture.runAsync(() -> {
            try {
                // 如果有历史上下文，用 AI 分析；否则用规则匹配
                String emotion;
                double confidence;

                if (chatHistory != null && !chatHistory.isBlank()) {
                    // AI 辅助分析（携带上下文）
                    EmotionResult result = detectWithAI(currentMessage, chatHistory);
                    emotion = result.emotion;
                    confidence = result.confidence;
                } else {
                    // 规则匹配
                    EmotionResult result = detectWithRules(currentMessage);
                    emotion = result.emotion;
                    confidence = result.confidence;
                }

                // 写入 Redis，endChat 时读取并入库
                String key = EMOTION_KEY_PREFIX + sessionId;
                Map<String, Object> emotionData = Map.of(
                        "emotion", emotion,
                        "confidence", confidence
                );
                chatMetadataRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(emotionData));
                log.info("[情感检测] sessionId={}, emotion={}, confidence={}", sessionId, emotion, confidence);

            } catch (Exception e) {
                log.error("[情感检测] 检测失败 sessionId={}: {}", sessionId, e.getMessage());
            }
        }, emotionDetectionExecutor);
    }

    /**
     * 从 Redis 获取情感结果
     */
    public Map<String, Object> getEmotionResult(String sessionId) {
        try {
            String key = EMOTION_KEY_PREFIX + sessionId;
            Object val = chatMetadataRedisTemplate.opsForValue().get(key);
            if (val != null) {
                return objectMapper.readValue(val.toString(), Map.class);
            }
        } catch (Exception e) {
            log.debug("[情感检测] 读取情感结果失败 sessionId={}: {}", sessionId, e.getMessage());
        }
        return null;
    }

    /**
     * 删除情感结果
     */
    public void deleteEmotionResult(String sessionId) {
        try {
            chatMetadataRedisTemplate.delete(EMOTION_KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("[情感检测] 删除情感结果失败 sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * AI 辅助情感分析（携带上下文）
     */
    private EmotionResult detectWithAI(String currentMessage, String chatHistory) {
        try {
            String prompt = String.format(userPromptTemplate, chatHistory, currentMessage);

            String response = chatClientBuilder.build()
                    .prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .call()
                    .content();

            // 解析 JSON 响应
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            String emotion = String.valueOf(parsed.get("emotion"));
            Object conf = parsed.get("confidence");
            double confidence = conf instanceof Number ? ((Number) conf).doubleValue() : 0.5;
            return new EmotionResult(emotion, confidence);

        } catch (Exception e) {
            log.warn("[情感检测] AI 分析失败，降级为规则匹配: {}", e.getMessage());
            return detectWithRules(currentMessage);
        }
    }

    /**
     * 规则匹配情感分析（无 AI 调用）
     */
    private EmotionResult detectWithRules(String message) {
        if (message == null || message.isBlank()) {
            return new EmotionResult("neutral", 0.5);
        }

        int positiveCount = countMatches(positiveWords, message);
        int negativeCount = countMatches(negativeWords, message);

        if (positiveCount > 0 && negativeCount == 0) {
            return new EmotionResult("positive", 0.6 + positiveCount * 0.1);
        } else if (negativeCount > 0 && positiveCount == 0) {
            return new EmotionResult("negative", 0.6 + negativeCount * 0.1);
        } else if (positiveCount > 0 && negativeCount > 0) {
            return new EmotionResult("neutral", 0.5);
        } else {
            return new EmotionResult("neutral", 0.5);
        }
    }

    private int countMatches(Pattern pattern, String text) {
        return (int) pattern.matcher(text).results().count();
    }

    private record EmotionResult(String emotion, double confidence) {}
}
