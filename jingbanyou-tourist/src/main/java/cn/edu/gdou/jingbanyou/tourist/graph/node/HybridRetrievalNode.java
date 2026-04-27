package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 混合检索节点
 *
 * 任务：FAQ 和景区知识库并行检索，一次 LLM 生成答案
 * 流程：
 *   1. 查询 FAQ 向量库
 *   2. 查询景区知识向量库
 *   3. 合并结果，一次 LLM 调用生成最终答案
 *
 * @author jingbanyou
 * @author jingbanyou
 */
@Slf4j
@Component
public class HybridRetrievalNode implements NodeAction {

    private final VectorStore knowledgeVectorStore;
    private final VectorStore faqVectorStore;
    private final ChatClient chatClient;

    public HybridRetrievalNode(
            VectorStore knowledgeVectorStore,
            VectorStore faqVectorStore,
            @Qualifier("hybridRetrievalChatClient") ChatClient chatClient) {
        this.knowledgeVectorStore = knowledgeVectorStore;
        this.faqVectorStore = faqVectorStore;
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");
        Long scenicId = state.value(SCENIC_ID, Long.class).orElse(0L);
        String sessionId = state.value(SESSION_ID, String.class).orElse(null);

        log.info("[混合检索] 开始: question={}, scenicId={}", question, scenicId);

        // 1. 查询 FAQ 向量库
        SearchRequest faqRequest = SearchRequest.builder()
                .query(question)
                .topK(2)
                .similarityThreshold(0.0)
                .build();
        List<Document> faqDocs = faqVectorStore.similaritySearch(faqRequest);

        StringBuilder faqContext = new StringBuilder();
        for (int i = 0; i < faqDocs.size(); i++) {
            faqContext.append("FAQ").append(i + 1).append(": ").append(faqDocs.get(i).getText()).append("\n");
        }
        log.info("[混合检索] FAQ检索到 {} 条", faqDocs.size());

        // 2. 查询景区知识向量库（使用 scenicId 过滤器，在 Redis 层直接过滤）
        SearchRequest.Builder kbRequestBuilder = SearchRequest.builder()
                .query(question)
                .topK(5)
                .similarityThreshold(0.0);
        if (scenicId != null && scenicId > 0) {
            kbRequestBuilder.filterExpression(new FilterExpressionBuilder().eq("scenicId", scenicId).build());
        }
        List<Document> kbDocs = knowledgeVectorStore.similaritySearch(kbRequestBuilder.build());
        log.info("[混合检索] scenicId={} 知识库检索到 {} 条", scenicId, kbDocs.size());

        StringBuilder kbContext = new StringBuilder();
        for (int i = 0; i < kbDocs.size(); i++) {
            Document doc = kbDocs.get(i);
            kbContext.append("景区知识").append(i + 1).append(": ").append(doc.getText()).append("\n");
            log.debug("[混合检索] 知识库结果{}: content前50字={}", i,
                    doc.getText().substring(0, Math.min(50, doc.getText().length())));
        }

        // 3. 合并上下文，一次 LLM 生成
        String userText;
        if (faqDocs.isEmpty() && kbDocs.isEmpty()) {
            userText = "游客问题：" + question + "\n\n（未检索到相关信息）";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("游客问题：").append(question).append("\n\n");
            if (!faqDocs.isEmpty()) {
                sb.append("=== FAQ 参考资料 ===\n").append(faqContext);
            }
            if (!kbDocs.isEmpty()) {
                sb.append("=== 景区知识 ===\n").append(kbContext);
            }
            userText = sb.toString();
        }

        log.info("[混合检索] 合并上下文，FAQ={}条 知识库={}条", faqDocs.size(), kbDocs.size());

        String content;
        if (faqDocs.isEmpty() && kbDocs.isEmpty()) {
            content = "这个问题我暂时还不太清楚，建议您咨询景区工作人员。";
        } else {
            content = chatClient.prompt()
                    .user(userText)
                    .advisors(ctx -> ctx.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();
        }

        log.info("[混合检索] 生成结果: {}", content);

        return state.updateState(Map.of(ANSWER, content));
    }
}
