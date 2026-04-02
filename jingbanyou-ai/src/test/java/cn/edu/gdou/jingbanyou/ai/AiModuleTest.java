package cn.edu.gdou.jingbanyou.ai;

import cn.edu.gdou.jingbanyou.ai.service.DigitalHumanService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * AI 模块集成测试
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Slf4j
@SpringBootTest
public class AiModuleTest {

    @Autowired
    private DigitalHumanService digitalHumanService;

    /**
     * 测试智能问答功能
     */
    @Test
    public void testAskQuestion() {
        log.info("开始测试智能问答...");
        
        String question = "请介绍一下这个景区的历史";
        String answer = digitalHumanService.answerWithRag(question);
        
        log.info("问题：{}", question);
        log.info("回答：{}", answer);
    }

    /**
     * 测试路线推荐功能
     */
    @Test
    public void testRecommendRoute() {
        log.info("开始测试路线推荐...");
        
        String interest = "历史文化";
        Integer duration = 120; // 2 小时
        
        String route = digitalHumanService.recommendRoute(interest, duration);
        
        log.info("兴趣：{}", interest);
        log.info("时长：{}分钟", duration);
        log.info("推荐路线：{}", route);
    }

}
