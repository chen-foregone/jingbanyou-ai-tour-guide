package cn.edu.gdou.jingbanyou.tourist.service.impl;

import cn.edu.gdou.jingbanyou.manage.dto.ConversationDetailVO;
import cn.edu.gdou.jingbanyou.manage.dto.ConversationListVO;
import cn.edu.gdou.jingbanyou.manage.entity.VisitorConversation;
import cn.edu.gdou.jingbanyou.manage.entity.VisitorInteraction;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorConversationMapper;
import cn.edu.gdou.jingbanyou.manage.mapper.VisitorInteractionMapper;
import cn.edu.gdou.jingbanyou.tourist.service.IEmotionDetectService;
import cn.edu.gdou.jingbanyou.tourist.service.IVisitorConversationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 游客会话服务实现
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitorConversationServiceImpl implements IVisitorConversationService {

    private final VisitorConversationMapper conversationMapper;
    private final VisitorInteractionMapper interactionMapper;
    private final IEmotionDetectService emotionDetectService;

    @Override
    public List<ConversationListVO> getConversationList(String visitorId, Long scenicId, int page, int size) {
        int offset = (page - 1) * size;
        List<VisitorConversation> conversations = conversationMapper.selectConversationList(visitorId, scenicId, offset, size);
        return conversations.stream().map(this::toConversationListVO).toList();
    }

    private ConversationListVO toConversationListVO(VisitorConversation vc) {
        ConversationListVO vo = new ConversationListVO();
        vo.setSessionId(vc.getSessionId());
        vo.setScenicId(vc.getScenicId());
        vo.setTitle(vc.getTitle());
        vo.setFirstMessage(vc.getFirstMessage());
        vo.setLastMessage(vc.getLastMessage());
        vo.setTurnCount(vc.getTurnCount());
        vo.setIntentType(vc.getIntentType());
        vo.setEmotionDetected(vc.getEmotionDetected());
        vo.setStartTime(vc.getStartTime());
        vo.setEndTime(vc.getEndTime());
        vo.setDurationMs(vc.getDurationMs());
        return vo;
    }

    @Override
    public long getConversationCount(String visitorId, Long scenicId) {
        return conversationMapper.countConversationList(visitorId, scenicId);
    }

    @Override
    public ConversationDetailVO getConversationDetail(String sessionId) {
        // 查询会话元数据
        VisitorConversation conversation = conversationMapper.selectById(sessionId);
        if (conversation == null) {
            return null;
        }

        // 查询该会话的所有交互记录
        LambdaQueryWrapper<VisitorInteraction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VisitorInteraction::getSessionId, sessionId)
                .orderByAsc(VisitorInteraction::getCreateTime);
        List<VisitorInteraction> interactions = interactionMapper.selectList(wrapper);

        ConversationDetailVO vo = new ConversationDetailVO();
        vo.setSessionId(conversation.getSessionId());
        vo.setVisitorId(conversation.getVisitorId());
        vo.setScenicId(conversation.getScenicId());
        vo.setHumanId(conversation.getHumanId());
        vo.setTitle(conversation.getTitle());
        vo.setStartTime(conversation.getStartTime());
        vo.setEndTime(conversation.getEndTime());
        vo.setIntentType(conversation.getIntentType());
        vo.setEmotionDetected(conversation.getEmotionDetected());
        vo.setEmotionConfidence(conversation.getEmotionConfidence());
        vo.setDurationMs(conversation.getDurationMs());
        vo.setTurnCount(conversation.getTurnCount());
        vo.setInteractionType(conversation.getInteractionType());

        // 转换交互记录为轮次列表
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        List<ConversationDetailVO.ConversationTurn> turns = new ArrayList<>();
        for (VisitorInteraction interaction : interactions) {
            if (interaction.getUserQuestion() != null) {
                ConversationDetailVO.ConversationTurn userTurn = new ConversationDetailVO.ConversationTurn();
                userTurn.setRole("user");
                userTurn.setContent(interaction.getUserQuestion());
                userTurn.setTime(interaction.getCreateTime() != null
                        ? sdf.format(interaction.getCreateTime()) : "");
                turns.add(userTurn);
            }
            if (interaction.getAiAnswer() != null) {
                ConversationDetailVO.ConversationTurn assistantTurn = new ConversationDetailVO.ConversationTurn();
                assistantTurn.setRole("assistant");
                assistantTurn.setContent(interaction.getAiAnswer());
                assistantTurn.setTime(interaction.getCreateTime() != null
                        ? sdf.format(interaction.getCreateTime()) : "");
                turns.add(assistantTurn);
            }
        }
        vo.setTurns(turns);

        return vo;
    }
}
