package cn.edu.gdou.jingbanyou.manage.tool;

import cn.edu.gdou.jingbanyou.manage.entity.Faq;
import cn.edu.gdou.jingbanyou.manage.service.IFaqService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * FAQ 知识库 RAG 检索工具
 * AI 可通过此工具在景区 FAQ 知识库中检索最匹配的问答
 */
@Slf4j
public class ScenicFaqRagTool {

    @Setter
    private IFaqService faqService;

    /**
     * 检索景区 FAQ 知识库，返回最匹配的标准问答
     *
     * @param scenicId 景区 ID
     * @param question 游客问题（中文描述）
     * @return 匹配到的 FAQ 标准问答，未找到返回 null
     */
    @Tool(name = "faq_knowledge_search", description = """
        在景区 FAQ 知识库中检索与游客问题最匹配的问答对。
        当游客询问景区开放时间、门票价格、设施位置、游览规则、停车信息、表演时间等常见问题时使用。
        返回最匹配的 FAQ 问答，包含标准问题和标准回答。
        """)
    public FaqResult searchFaq(
            @ToolParam(description = "景区 ID", required = true) Long scenicId,
            @ToolParam(description = "游客的自然语言问题，如'景区几点开门'、'门票多少钱'", required = true) String question) {
        log.debug("[FAQ-RAG] scenicId={}, question={}", scenicId, question);
        Faq faq = faqService.matchSimilarQuestion(scenicId, question);
        if (faq == null) {
            return new FaqResult(null, null, "未找到匹配的 FAQ");
        }
        return new FaqResult(faq.getQuestion(), faq.getAnswer(), null);
    }

    public record FaqResult(
            String matchedQuestion,
            String standardAnswer,
            String errorMessage
    ) {}
}
