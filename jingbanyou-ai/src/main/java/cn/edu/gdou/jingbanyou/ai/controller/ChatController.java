package cn.edu.gdou.jingbanyou.ai.controller;

import cn.edu.gdou.jingbanyou.common.core.domain.R;
import cn.edu.gdou.jingbanyou.ai.service.DigitalHumanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * AI 数字人对话接口
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Slf4j
@RestController
@RequestMapping("/ai/chat")
public class ChatController {

    @Autowired
    private DigitalHumanService digitalHumanService;

    /**
     * 智能问答接口
     * 
     * @param question 用户问题
     * @return AI 回答
     */
    @PostMapping("/ask")
    public R<String> ask(@RequestParam String question) {
        log.info("收到问答请求：{}", question);
        try {
            String answer = digitalHumanService.answerWithRag(question);
            return R.ok(answer);
        } catch (Exception e) {
            log.error("问答处理失败", e);
            return R.fail("AI 回答失败：" + e.getMessage());
        }
    }

    /**
     * 个性化路线推荐
     * 
     * @param interest 游客兴趣
     * @param duration 可用时间（分钟）
     * @return 推荐路线
     */
    @PostMapping("/recommend")
    public R<String> recommendRoute(
            @RequestParam String interest,
            @RequestParam Integer duration) {
        log.info("收到路线推荐请求：兴趣={}, 时长={}分钟", interest, duration);
        try {
            String route = digitalHumanService.recommendRoute(interest, duration);
            return R.ok(route);
        } catch (Exception e) {
            log.error("路线推荐失败", e);
            return R.fail("路线推荐失败：" + e.getMessage());
        }
    }

}
