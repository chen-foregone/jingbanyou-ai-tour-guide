package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.manage.entity.VisitorInteraction;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorInteractionMapper;
import com.alibaba.cloud.ai.memory.redis.JedisRedisChatMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

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

            for (int i = 0; i + 1 < msgs.size(); i += 2) {
                if (msgs.get(i).getText() != null) {
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
                    visitorInteractionMapper.insert(record);
                }
            }
            chatMemoryRepository.deleteByConversationId(sessionId);
            log.info("对话已同步到 MySQL 并清除 Redis，sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("同步对话到 MySQL 失败，sessionId={}", sessionId, e);
        }
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
