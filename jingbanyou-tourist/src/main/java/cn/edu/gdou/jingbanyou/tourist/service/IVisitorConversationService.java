package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.manage.dto.ConversationDetailVO;
import cn.edu.gdou.jingbanyou.manage.dto.ConversationListVO;

import java.util.List;

/**
 * 游客会话服务接口
 *
 * @author jingbanyou
 */
public interface IVisitorConversationService {

    /**
     * 获取会话列表
     *
     * @param visitorId 游客ID
     * @param scenicId 景区ID（可选）
     * @param page 页码
     * @param size 每页条数
     * @return 会话列表
     */
    List<ConversationListVO> getConversationList(String visitorId, Long scenicId, int page, int size);

    /**
     * 获取会话总数
     *
     * @param visitorId 游客ID
     * @param scenicId 景区ID（可选）
     * @return 总数
     */
    long getConversationCount(String visitorId, Long scenicId);

    /**
     * 获取会话详情
     *
     * @param sessionId 会话ID
     * @return 会话详情
     */
    ConversationDetailVO getConversationDetail(String sessionId);
}
