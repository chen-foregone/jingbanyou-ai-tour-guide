package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.manage.entity.VisitorConversation;
import cn.edu.gdou.jingbanyou.manage.entity.VisitorInteraction;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorConversationMapper;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorInteractionMapper;
import com.alibaba.cloud.ai.memory.redis.JedisRedisChatMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 对话记忆服务
 *
 * 仅负责对话结束时的 MySQL 持久化（Redis 读写已由 MessageChatMemoryAdvisor 自动处理）
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryService implements IChatMemoryService {

    private final JedisRedisChatMemoryRepository chatMemoryRepository;
    private final VisitorInteractionMapper visitorInteractionMapper;
    private final VisitorConversationMapper visitorConversationMapper;
    private final IEmotionDetectService emotionDetectService;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> chatMetadataRedisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 对话结束时，异步全量同步到 MySQL
     * 调用时机：前端页面离开时调用 /tourist/chat/end
     *
     * @param sessionId        会话ID
     * @param scenicId         景区ID
     * @param visitorId        游客ID
     * @param interactionType   交互类型（如 text、voice）
     */
    @Override
    @Async
    public void syncToMySQL(String sessionId, Long scenicId, String visitorId, String interactionType) {
        syncToMySQL(sessionId, scenicId, visitorId, interactionType, null, null, null, null);
    }

    /**
     * 对话结束时，同步到 MySQL 并携带完整元数据
     * 调用时机：前端页面离开时调用 /tourist/chat/end
     */
    @Override
    @Async
    public void syncToMySQL(String sessionId, Long scenicId, String visitorId,
                           String interactionType, String intentType,
                           Integer responseTimeMs, Integer tokensUsed, String modelUsed) {
        try {
            List<Message> msgs = chatMemoryRepository.findByConversationId(sessionId);
            if (msgs == null || msgs.isEmpty()) return;

            int turnCount = 0;
            String firstMessage = null;
            String lastMessage = null;
            Long humanId = null;

            for (int i = 0; i + 1 < msgs.size(); i += 2) {
                if (msgs.get(i).getText() != null) {
                    // 记录首轮消息
                    if (firstMessage == null) {
                        firstMessage = msgs.get(i).getText();
                    }
                    lastMessage = msgs.get(i).getText();

                    VisitorInteraction record = new VisitorInteraction();
                    record.setSessionId(sessionId);
                    record.setScenicId(scenicId);
                    record.setVisitorId(visitorId);
                    record.setUserQuestion(msgs.get(i).getText());
                    record.setAiAnswer(msgs.get(i + 1).getText());
                    record.setInteractionType(interactionType);
                    record.setIntentType(intentType);
                    record.setResponseTimeMs(responseTimeMs);
                    record.setTokensUsed(tokensUsed);
                    record.setModelUsed(modelUsed);
                    record.setTurnIndex(turnCount);
                    visitorInteractionMapper.insert(record);
                    turnCount++;
                }
            }

            // 读取情感检测结果
            String emotion = null;
            Double confidence = null;
            try {
                String emotionKey = "chat:emotion:" + sessionId;
                Object emotionData = chatMetadataRedisTemplate.opsForValue().get(emotionKey);
                if (emotionData != null) {
                    Map<String, Object> emotionMap = objectMapper.readValue(emotionData.toString(), Map.class);
                    emotion = (String) emotionMap.get("emotion");
                    Object conf = emotionMap.get("confidence");
                    if (conf != null) {
                        confidence = conf instanceof Number ? ((Number) conf).doubleValue() : null;
                    }
                }
            } catch (Exception e) {
                log.warn("[同步] 读取情感结果失败 sessionId={}: {}", sessionId, e.getMessage());
            }

            // 计算会话时长
            Long durationMs = null;
            Long startTime = null;
            Long endTime = null;
            try {
                long now = System.currentTimeMillis();
                // 从雪花 ID 反推开始时间（雪花 ID 的时间戳部分）
                long timestamp = extractTimestamp(sessionId);
                if (timestamp > 0) {
                    startTime = timestamp;
                    endTime = now;
                    durationMs = now - timestamp;
                }
            } catch (Exception e) {
                log.warn("[同步] 计算会话时长失败 sessionId={}: {}", sessionId, e.getMessage());
            }

            // 写入会话元数据
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

            // 清除 Redis 数据
            chatMemoryRepository.deleteByConversationId(sessionId);
            try {
                chatMetadataRedisTemplate.delete("chat:emotion:" + sessionId);
                chatMetadataRedisTemplate.delete("chat:meta:" + sessionId);
            } catch (Exception e) {
                log.warn("[同步] 清除 Redis 元数据失败 sessionId={}: {}", sessionId, e.getMessage());
            }

            log.info("[同步] 对话已同步到 MySQL，sessionId={}，turnCount={}，emotion={}", sessionId, turnCount, emotion);
        } catch (Exception e) {
            log.error("[同步] 同步对话到 MySQL 失败，sessionId={}", sessionId, e);
        }
    }

    /**
     * 从雪花 ID 提取时间戳
     * 雪花 ID 结构：1 + 41位时间戳 + 10位 workerId + 12位序列号
     */
    private long extractTimestamp(String sessionId) {
        try {
            long id = Long.parseLong(sessionId);
            // 去掉最高位（符号位），然后右移 22 位（workerId 10位 + sequence 12位）
            return (id & 0x1FFFFFFFFFFFFFFFL) >> 22;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 截断字符串到指定长度
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    /**
     * 记录单轮对话（不含会话结束标志）
     * 在 chat()/chatStream() 成功后调用，写入当前这一轮交互
     */
    @Override
    @Async
    public void recordSingleTurn(String sessionId, Long scenicId, String visitorId,
                                  String userQuestion, String aiAnswer,
                                  String interactionType, String intentType,
                                  Integer responseTimeMs, Integer tokensUsed, String modelUsed) {
        try {
            VisitorInteraction record = new VisitorInteraction();
            record.setSessionId(sessionId);
            record.setScenicId(scenicId);
            record.setVisitorId(visitorId);
            record.setUserQuestion(userQuestion);
            record.setAiAnswer(aiAnswer);
            record.setInteractionType(interactionType);
            record.setIntentType(intentType);
            record.setResponseTimeMs(responseTimeMs);
            record.setTokensUsed(tokensUsed);
            record.setModelUsed(modelUsed);
            visitorInteractionMapper.insert(record);
            log.info("单轮对话已记录，sessionId={}, intent={}", sessionId, intentType);
        } catch (Exception e) {
            log.error("记录单轮对话失败，sessionId={}", sessionId, e);
        }
    }
}
