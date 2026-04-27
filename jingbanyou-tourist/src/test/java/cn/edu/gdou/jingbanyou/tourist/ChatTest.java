package cn.edu.gdou.jingbanyou.tourist;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * AI 对话功能测试
 *
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Slf4j
public class ChatTest {

    /**
     * AI 对话流程测试
     * 注意：需要 Spring Boot Test 和 mock ChatClient
     * 当前为占位测试，实际 AI 调用测试需要在集成测试中进行
     */
    @Test
    @Disabled("需要 Spring Boot Test 环境，集成测试时启用")
    void chatWithModel() {
        // TODO: 实现集成测试，使用 @SpringBootTest + @MockBean
        // 1. Mock ChatClient 返回预设回答
        // 2. 调用 TouristController.chat()
        // 3. 验证返回结果
        log.info("AI 对话测试已禁用，请在集成测试中启用");
    }
}
