package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 混合检索节点
 *
 * 只做向量检索，将 RAG 上下文写入 RETRIEVED_DOCS，不调用 LLM
 * LLM 流式调用由 StreamingAnswerService 负责
 *
 * @author jingbanyou
 */
@Slf4j
@Component
public class HybridRetrievalNode implements NodeAction {

    private final VectorStore knowledgeVectorStore;
    private final VectorStore faqVectorStore;

    public HybridRetrievalNode(
            VectorStore knowledgeVectorStore,
            VectorStore faqVectorStore) {
        this.knowledgeVectorStore = knowledgeVectorStore;
        this.faqVectorStore = faqVectorStore;
    }

    @Override
    @CircuitBreaker(name = "dashscope-llm", fallbackMethod = "applyFallback")
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");
        Long scenicId = state.value(SCENIC_ID, Long.class).orElse(0L);

        log.info("[混合检索] 开始: question={}, scenicId={}", question, scenicId);

        // 1. 查询 FAQ 向量库
        SearchRequest faqRequest = SearchRequest.builder()
                .query(question)
                .topK(3)
                .similarityThreshold(0.0)
                .build();
        List<Document> faqDocs = faqVectorStore.similaritySearch(faqRequest);
        log.info("[混合检索] FAQ检索到 {} 条", faqDocs.size());

        // 2. 查询景区知识向量库
        SearchRequest.Builder kbRequestBuilder = SearchRequest.builder()
                .query(question)
                .topK(3)
                .similarityThreshold(0.0);
        if (scenicId != null && scenicId > 0) {
            kbRequestBuilder.filterExpression(new FilterExpressionBuilder().eq("scenicId", scenicId).build());
        }
        List<Document> kbDocs = knowledgeVectorStore.similaritySearch(kbRequestBuilder.build());
        log.info("[混合检索] 知识库检索到 {} 条", kbDocs.size());

        // 3. 构建 RAG 上下文并写入 RETRIEVED_DOCS
        String retrievedDocs;
        if (faqDocs.isEmpty() && kbDocs.isEmpty()) {
            // 无检索结果，存入空字符串，由 Service 判断后返回默认回复
            retrievedDocs = "";
            log.info("[混合检索] 无检索结果，RETRIEVED_DOCS 为空");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("游客问题：").append(question).append("\n\n");
            if (!faqDocs.isEmpty()) {
                sb.append("=== FAQ 参考资料 ===\n");
                for (int i = 0; i < faqDocs.size(); i++) {
                    sb.append("FAQ").append(i + 1).append(": ").append(faqDocs.get(i).getText()).append("\n");
                }
            }
            if (!kbDocs.isEmpty()) {
                sb.append("=== 景区知识 ===\n");
                for (int i = 0; i < kbDocs.size(); i++) {
                    sb.append("景区知识").append(i + 1).append(": ").append(kbDocs.get(i).getText()).append("\n");
                }
            }
            retrievedDocs = sb.toString();
            log.info("[混合检索] RETRIEVED_DOCS 长度={}", retrievedDocs.length());
        }

        return state.updateState(Map.of(
                RETRIEVED_DOCS, retrievedDocs,
                ROUTE_STATUS, "",
                GUIDE_MESSAGE, "",
                ANSWER, ""
        ));
    }

    /**
     * 混合检索熔断降级方法
     */
    private Map<String, Object> applyFallback(OverAllState state, Throwable t) {
        log.warn("[混合检索] 熔断降级: {}", t.getMessage());
        return state.updateState(Map.of(
                RETRIEVED_DOCS, "",
                ROUTE_STATUS, "",
                GUIDE_MESSAGE, "",
                ANSWER, ""
        ));
    }
}
