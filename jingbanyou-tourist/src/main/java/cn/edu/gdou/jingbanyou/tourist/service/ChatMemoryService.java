package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.manage.entity.VisitorInteraction;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorInteractionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 对话记忆服务
 * <p>
 * 双层存储：
 * - Redis（热缓存）：存储最近 N 轮对话，TTL 24h，供实时推理使用
 * - MySQL VisitorInteraction（冷存储）：每轮交互记录存档，持久化
 * <p>
 * 流程：
 * 1. 对话轮次开始 → 从 Redis 加载历史消息 → 注入 Graph State
 * 2. 对话轮次结束 → 写入 Redis 缓存 + 写入 MySQL VisitorInteraction
 * 3. 会话超时/主动结束 → 从 Redis 全量写入 MySQL 存档
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private final ChatMemory chatMemory;
    private final RedisTemplate<String, String> chatMemoryRedisTemplate;
    private final VisitorInteractionMapper visitorInteractionMapper;
    private final ObjectMapper objectMapper;

    /** Redis key 前缀 */
    private static final String REDIS_KEY_PREFIX = "chat:memory:";
    /** Redis TTL（小时） */
    private static final int TTL_HOURS = 24;

    // ==================== 读：从 Redis 加载记忆 ====================

    /**
     * 加载指定会话的最近 N 轮对话历史
     *
     * @param sessionId 会话 ID
     * @param maxTurns  最大轮次数（null=全部）
     * @return 格式化的历史字符串，供各节点 prompt 使用
     */
    public String loadHistory(String sessionId, Integer maxTurns) {
        try {
            List<Message> messages = chatMemory.get(sessionId);
            if (messages == null || messages.isEmpty()) {
                return "";
            }

            List<Message> toUse = messages;
            if (maxTurns != null && maxTurns > 0) {
                int keepCount = maxTurns * 2; // 每轮 = user + assistant
                if (messages.size() > keepCount) {
                    toUse = messages.subList(messages.size() - keepCount, messages.size());
                }
            }

            return formatHistory(toUse);

        } catch (Exception e) {
            log.warn("加载对话历史失败，sessionId={}", sessionId, e);
            return "";
        }
    }

    /**
     * 获取原始 Message 列表（供需要 ChatMemory 的节点使用）
     */
    public List<Message> getMessages(String sessionId) {
        try {
            List<Message> messages = chatMemory.get(sessionId);
            return messages != null ? messages : List.of();
        } catch (Exception e) {
            log.warn("获取消息列表失败，sessionId={}", sessionId, e);
            return List.of();
        }
    }

    // ==================== 写：添加消息到 Redis ====================

    /**
     * 添加用户消息到对话记忆
     */
    public void addUserMessage(String sessionId, String text) {
        try {
            chatMemory.add(sessionId, new UserMessage(text));
            refreshTtl(sessionId);
            log.debug("添加用户消息到记忆，sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("添加用户消息到记忆失败，sessionId={}", sessionId, e);
        }
    }

    /**
     * 添加 AI 助手消息到对话记忆
     */
    public void addAssistantMessage(String sessionId, String text) {
        try {
            chatMemory.add(sessionId,
                    new org.springframework.ai.chat.messages.AssistantMessage(text));
            refreshTtl(sessionId);
            log.debug("添加助手消息到记忆，sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("添加助手消息到记忆失败，sessionId={}", sessionId, e);
        }
    }

    // ==================== 持久化：写入 MySQL ====================

    /**
     * 将本轮交互记录持久化到 MySQL
     * 在 ProfileUpdaterNode 之后调用（Graph 流程结束时）
     */
    @Async
    public void persistInteraction(VisitorInteraction interaction) {
        try {
            visitorInteractionMapper.insert(interaction);
            log.debug("交互记录已写入 MySQL，sessionId={}", interaction.getSessionId());
        } catch (Exception e) {
            log.error("交互记录写入 MySQL 失败，sessionId={}", interaction.getSessionId(), e);
        }
    }

    /**
     * 会话结束时，将 Redis 中的全部对话记忆同步到 MySQL 存档
     * 调用时机：会话超时 / 用户主动结束 / 定时任务触发
     */
    @Async
    public void syncToMySQLOnSessionEnd(String sessionId, Long scenicId, String visitorId) {
        try {
            List<Message> messages = chatMemory.get(sessionId);
            if (messages == null || messages.isEmpty()) {
                return;
            }

            // 按 user+assistant 配对，每对为一轮
            Date now = new Date();
            for (int i = 0; i + 1 < messages.size(); i += 2) {
                Message userMsg = messages.get(i);
                Message assistantMsg = messages.get(i + 1);

                if (!(userMsg instanceof UserMessage)) {
                    continue;
                }

                VisitorInteraction record = new VisitorInteraction();
                record.setSessionId(sessionId);
                record.setScenicId(scenicId);
                record.setVisitorId(visitorId);
                record.setUserQuestion(((UserMessage) userMsg).getText());
                record.setAiAnswer(assistantMsg.getText());
                record.setInteractionType("text");
                record.setCreateTime(now);

                visitorInteractionMapper.insert(record);
            }

            // 清除 Redis 缓存
            clearMemory(sessionId);
            log.info("会话记忆已同步到 MySQL 并清除 Redis，sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("会话记忆同步 MySQL 失败，sessionId={}", sessionId, e);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 清除指定会话的对话记忆
     */
    public void clearMemory(String sessionId) {
        try {
            chatMemory.clear(sessionId);
            chatMemoryRedisTemplate.delete(REDIS_KEY_PREFIX + sessionId);
            log.debug("清除对话记忆，sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("清除对话记忆失败，sessionId={}", sessionId, e);
        }
    }

    /**
     * 刷新 Redis TTL
     */
    private void refreshTtl(String sessionId) {
        try {
            chatMemoryRedisTemplate.expire(REDIS_KEY_PREFIX + sessionId, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.debug("刷新 TTL 失败（不影响主流程），sessionId={}", sessionId);
        }
    }

    /**
     * 将消息列表格式化为字符串（供历史 prompt 使用）
     * 格式：Q1: xxx\nA1: xxx\nQ2: xxx\nA2: xxx
     */
    private String formatHistory(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int qCount = 0;
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (msg instanceof UserMessage) {
                qCount++;
                sb.append("Q").append(qCount).append(": ")
                  .append(((UserMessage) msg).getText().trim()).append("\n");
            } else {
                sb.append("A").append(qCount).append(": ")
                  .append(msg.getText().trim()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 从 MySQL 加载历史交互（用于恢复会话场景）
     */
    public String loadFromMySQL(String sessionId) {
        try {
            LambdaQueryWrapper<VisitorInteraction> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(VisitorInteraction::getSessionId, sessionId)
                   .orderByAsc(VisitorInteraction::getId);
            List<VisitorInteraction> records = visitorInteractionMapper.selectList(wrapper);

            if (records == null || records.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (VisitorInteraction r : records) {
                count++;
                if (r.getUserQuestion() != null) {
                    sb.append("Q").append(count).append(": ")
                      .append(r.getUserQuestion().trim()).append("\n");
                }
                if (r.getAiAnswer() != null) {
                    sb.append("A").append(count).append(": ")
                      .append(r.getAiAnswer().trim()).append("\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("从 MySQL 加载历史失败，sessionId={}", sessionId, e);
            return "";
        }
    }
}
