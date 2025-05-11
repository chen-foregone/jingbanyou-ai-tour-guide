package cn.edu.gdou.jingbanyou.tourist.service.impl;

import cn.edu.gdou.jingbanyou.common.utils.SnowflakeIdUtil;
import cn.edu.gdou.jingbanyou.manage.entity.VisitorConversation;
import cn.edu.gdou.jingbanyou.manage.entity.VisitorInteraction;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorConversationMapper;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorInteractionMapper;
import cn.edu.gdou.jingbanyou.tourist.service.IConversationPersistenceService;
import com.alibaba.cloud.ai.memory.redis.JedisRedisChatMemoryRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话持久化服务实现
 * 负责对话结束时将 Redis 中的聊天记录和元数据同步到 MySQL
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationPersistenceServiceImpl implements IConversationPersistenceService {

    private final JedisRedisChatMemoryRepository chatMemoryRepository;
    private final VisitorInteractionMapper visitorInteractionMapper;
    private final VisitorConversationMapper visitorConversationMapper;
    private final RedisTemplate<String, Object> chatMetadataRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Async
    public void syncToMySQL(String sessionId, Long scenicId, Long humanId, String visitorId,
                            String interactionType, String intentType,
                            Integer responseTimeMs, Integer tokensUsed, String modelUsed,
                            String ragDocs) {
        try {
            List<Message> msgs = loadMessages(sessionId);
            if (msgs == null || msgs.isEmpty()) {
                return;
            }

            int turnCount = persistInteractions(msgs, sessionId, scenicId, humanId, visitorId,
                    interactionType, intentType, responseTimeMs, tokensUsed, modelUsed, ragDocs);

            String firstMessage = extractFirstMessage(msgs);
            String lastMessage = extractLastMessage(msgs);

            Map<String, Object> emotionData = loadEmotionData(sessionId);
            String emotion = (String) emotionData.get("emotion");
            Double confidence = (Double) emotionData.get("confidence");

            Map<String, Object> durationData = calculateDuration(sessionId);
            Long durationMs = (Long) durationData.get("durationMs");
            Long startTime = (Long) durationData.get("startTime");
            Long endTime = (Long) durationData.get("endTime");

            persistConversation(sessionId, scenicId, humanId, visitorId, interactionType,
                    intentType, turnCount, firstMessage, lastMessage,
                    emotion, confidence, durationMs, startTime, endTime);

            clearRedisData(sessionId);

            log.info("[同步] 对话已同步到 MySQL，sessionId={}，turnCount={}，emotion={}",
                    sessionId, turnCount, emotion);
        } catch (Exception e) {
            log.error("[同步] 同步对话到 MySQL 失败，sessionId={}", sessionId, e);
        }
    }

    @Override
    public void updateLastInteractionEmotion(String sessionId, String emotionDetected,
                                              Double emotionConfidence) {
        try {
            VisitorInteraction interaction = visitorInteractionMapper
                    .selectList(new LambdaQueryWrapper<VisitorInteraction>()
                            .eq(VisitorInteraction::getSessionId, sessionId)
                            .orderByDesc(VisitorInteraction::getId)
                            .last("LIMIT 1"))
                    .stream().findFirst().orElse(null);
            if (interaction != null) {
                interaction.setEmotionDetected(emotionDetected);
                interaction.setEmotionConfidence(emotionConfidence);
                visitorInteractionMapper.updateById(interaction);
                log.info("已更新交互情感，sessionId={}, emotion={}", sessionId, emotionDetected);
            }
        } catch (Exception e) {
            log.error("更新交互情感失败，sessionId={}", sessionId, e);
        }
    }

    /**
     * 从 Redis 加载对话消息
     */
    private List<Message> loadMessages(String sessionId) {
        return chatMemoryRepository.findByConversationId(sessionId);
    }

    /**
     * 持久化交互记录到 MySQL
     *
     * @return 交互轮数
     */
    private int persistInteractions(List<Message> msgs, String sessionId, Long scenicId,
                                     Long humanId, String visitorId, String interactionType,
                                     String intentType, Integer responseTimeMs,
                                     Integer tokensUsed, String modelUsed, String ragDocs) {
        int turnCount = 0;
        for (int i = 0; i + 1 < msgs.size(); i += 2) {
            if (msgs.get(i).getText() != null) {
                VisitorInteraction record = new VisitorInteraction();
                record.setSessionId(sessionId);
                record.setScenicId(scenicId);
                record.setHumanId(humanId);
                record.setVisitorId(visitorId);
                record.setUserQuestion(msgs.get(i).getText());
                record.setAiAnswer(msgs.get(i + 1).getText());
                record.setInteractionType(interactionType);
                record.setIntentType(intentType);
                record.setResponseTimeMs(responseTimeMs);
                record.setTokensUsed(tokensUsed);
                record.setModelUsed(modelUsed);
                record.setTurnIndex(turnCount);
                record.setRagDocs(toJson(ragDocs));
                visitorInteractionMapper.insert(record);
                turnCount++;
            }
        }
        return turnCount;
    }

    /**
     * 从消息列表提取首条用户消息
     */
    private String extractFirstMessage(List<Message> msgs) {
        for (Message msg : msgs) {
            if (msg.getText() != null) {
                return msg.getText();
            }
        }
        return null;
    }

    /**
     * 从消息列表提取最后一条用户消息
     */
    private String extractLastMessage(List<Message> msgs) {
        String last = null;
        for (int i = 0; i < msgs.size(); i += 2) {
            if (msgs.get(i).getText() != null) {
                last = msgs.get(i).getText();
            }
        }
        return last;
    }

    /**
     * 从 Redis 加载情感检测结果
     *
     * @return Map 包含 emotion(String) 和 confidence(Double)
     */
    private Map<String, Object> loadEmotionData(String sessionId) {
        Map<String, Object> result = new HashMap<>();
        result.put("emotion", null);
        result.put("confidence", null);
        try {
            String emotionKey = "chat:emotion:" + sessionId;
            Object raw = chatMetadataRedisTemplate.opsForValue().get(emotionKey);
            if (raw != null) {
                Map<String, Object> emotionMap = objectMapper.readValue(raw.toString(), Map.class);
                result.put("emotion", emotionMap.get("emotion"));
                Object conf = emotionMap.get("confidence");
                result.put("confidence", conf instanceof Number ? ((Number) conf).doubleValue() : null);
            }
        } catch (Exception e) {
            log.debug("[同步] 读取情感结果失败 sessionId={}: {}", sessionId, e.getMessage());
        }
        return result;
    }

    /**
     * 计算会话时长
     *
     * @return Map 包含 durationMs(Long), startTime(Long), endTime(Long)
     */
    private Map<String, Object> calculateDuration(String sessionId) {
        Map<String, Object> result = new HashMap<>();
        result.put("durationMs", null);
        result.put("startTime", null);
        result.put("endTime", null);
        try {
            long now = System.currentTimeMillis();
            long timestamp = SnowflakeIdUtil.extractTimestamp(sessionId);
            if (timestamp > 0) {
                result.put("startTime", timestamp);
                result.put("endTime", now);
                result.put("durationMs", now - timestamp);
            }
        } catch (Exception e) {
            log.debug("[同步] 计算会话时长失败 sessionId={}: {}", sessionId, e.getMessage());
        }
        return result;
    }

    /**
     * 写入会话元数据到 MySQL
     */
    private void persistConversation(String sessionId, Long scenicId, Long humanId,
                                      String visitorId, String interactionType, String intentType,
                                      int turnCount, String firstMessage, String lastMessage,
                                      String emotion, Double confidence, Long durationMs,
                                      Long startTime, Long endTime) {
        VisitorConversation conversation = new VisitorConversation();
        conversation.setSessionId(sessionId);
        conversation.setScenicId(scenicId);
        conversation.setHumanId(humanId);
        conversation.setVisitorId(visitorId);
        conversation.setTitle(truncate(firstMessage, 50));
        conversation.setFirstMessage(truncate(firstMessage, 500));
        conversation.setLastMessage(truncate(lastMessage, 500));
        conversation.setTurnCount(turnCount);
        conversation.setIntentType(intentType);
        conversation.setEmotionDetected(emotion);
        conversation.setEmotionConfidence(confidence);
        conversation.setDurationMs(durationMs);
        conversation.setStartTime(startTime != null ? new Date(startTime) : null);
        conversation.setEndTime(endTime != null ? new Date(endTime) : null);
        conversation.setInteractionType(interactionType);
        visitorConversationMapper.insert(conversation);
    }

    /**
     * 清除 Redis 中的对话数据
     */
    private void clearRedisData(String sessionId) {
        chatMemoryRepository.deleteByConversationId(sessionId);
        try {
            chatMetadataRedisTemplate.delete("chat:emotion:" + sessionId);
            chatMetadataRedisTemplate.delete("chat:meta:" + sessionId);
        } catch (Exception e) {
            log.debug("[同步] 清除 Redis 元数据失败 sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 截断字符串到指定长度
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    /**
     * 将 ragDocs 转为合法 JSON 字符串。
     * MySQL rag_docs 列类型为 JSON，不接受纯文本字符串。
     */
    private String toJson(String ragDocs) {
        if (ragDocs == null || ragDocs.isBlank()) {
            return "{}";
        }
        try {
            // 尝试直接解析，如果已经是合法 JSON 就原样返回
            objectMapper.readTree(ragDocs);
            return ragDocs;
        } catch (Exception e) {
            // 不是合法 JSON，包装为 {"content": "..."}
            try {
                return objectMapper.writeValueAsString(Map.of("content", ragDocs));
            } catch (Exception ex) {
                return "{}";
            }
        }
    }
}
