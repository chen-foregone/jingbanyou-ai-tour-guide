package cn.edu.gdou.jingbanyou.tourist.controller;

import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.enums.TouristErrorCode;
import cn.edu.gdou.jingbanyou.manage.dto.ConversationDetailVO;
import cn.edu.gdou.jingbanyou.manage.dto.ConversationListVO;
import cn.edu.gdou.jingbanyou.tourist.service.IVisitorConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会话历史
 *
 * @author jingbanyou
 */
@Slf4j
@RestController
@RequestMapping("/tourist")
@RequiredArgsConstructor
@Tag(name = "游客端-会话", description = "会话列表和详情")
public class ConversationController extends BaseController {

    private final IVisitorConversationService visitorConversationService;

    /**
     * 获取会话列表（简要信息）
     *
     * @param visitorId 游客ID
     * @param scenicId 景区ID（可选）
     * @param page 页码
     * @param size 每页条数
     * @return 会话列表
     */
    @Operation(summary = "获取会话列表", description = "分页获取游客的会话列表")
    @GetMapping("/conversation/list")
    public AjaxResult getConversationList(
            @RequestParam String visitorId,
            @RequestParam(required = false) Long scenicId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (visitorId == null || visitorId.isBlank()) {
            return error(TouristErrorCode.T005);
        }
        List<ConversationListVO> list = visitorConversationService.getConversationList(visitorId, scenicId, page, size);
        long total = visitorConversationService.getConversationCount(visitorId, scenicId);
        return success(Map.of("list", list, "total", total, "page", page, "size", size));
    }

    /**
     * 获取会话详情（完整对话）
     *
     * @param sessionId 会话ID
     * @param visitorId 游客ID（用于归属校验）
     * @return 会话详情
     */
    @Operation(summary = "获取会话详情", description = "根据会话ID获取完整的对话记录，含归属校验")
    @GetMapping("/conversation/{sessionId}")
    public AjaxResult getConversationDetail(@PathVariable String sessionId,
                                            @RequestParam(required = false) String visitorId) {
        if (sessionId == null || sessionId.isBlank()) {
            return error(TouristErrorCode.T006);
        }
        ConversationDetailVO detail = visitorConversationService.getConversationDetail(sessionId);
        if (detail == null) {
            return error(TouristErrorCode.T003);
        }
        // visitorId 归属校验：只能查看自己的会话
        if (visitorId != null && !visitorId.isBlank()
                && !visitorId.equals(detail.getVisitorId())) {
            return error("无权访问该会话");
        }
        return success(detail);
    }
}
