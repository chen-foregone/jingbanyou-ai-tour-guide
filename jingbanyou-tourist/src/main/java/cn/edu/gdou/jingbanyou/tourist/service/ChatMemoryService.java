package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.manage.entity.VisitorInteraction;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorInteractionMapper;
import com.alibaba.cloud.ai.memory.redis.RedisChatMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对话记忆服务
 * <p>仅负责对话结束时的 MySQL 持久化（Redis 读写已由 MessageChatMemoryAdvisor 自动处理）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private final RedisChatMemoryRepository chatMemoryRepository;
    private final VisitorInteractionMapper visitorInteractionMapper;

    /**
     * 对话结束时，异步全量同步到 MySQL
     * 调用时机：前端页面离开时调用 /tourist/chat/end
     */
    @Async
    public void syncToMySQL(String sessionId, Long scenicId, String visitorId) {
        try {
            // 从 Redis 读取全部历史消息（lastN=-1 表示全部）
            List<Message> msgs = chatMemoryRepository.get(sessionId, -1);
            if (msgs == null || msgs.isEmpty()) return;

            // 每 user+assistant 配对 → 一条 VisitorInteraction
            for (int i = 0; i + 1 < msgs.size(); i += 2) {
                if (msgs.get(i).getText() != null) {
                    VisitorInteraction record = new VisitorInteraction();
                    record.setSessionId(sessionId);
                    record.setScenicId(scenicId);
                    record.setVisitorId(visitorId);
                    record.setUserQuestion(msgs.get(i).getText());
                    record.setAiAnswer(msgs.get(i + 1).getText());
                    record.setInteractionType("text");
                    visitorInteractionMapper.insert(record);
                }
            }
            // 清除 Redis 缓存
            chatMemoryRepository.clear(sessionId);
            log.info("对话已同步到 MySQL 并清除 Redis，sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("同步对话到 MySQL 失败，sessionId={}", sessionId, e);
        }
    }
}
