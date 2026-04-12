package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 景区知识检索节点（RAG检索）
 * 任务：从向量数据库中检索与游客问题相关的景区知识文档
 * 注意：此节点主要调用向量数据库检索，不需要LLM
 */
@Component
public class ScenicKnowledgeRetrievalNode implements NodeAction {

    private final VectorStore vectorStore;

    public ScenicKnowledgeRetrievalNode(@Qualifier("knowledgeVectorStore") VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");
        Long scenicId = state.value(SCENIC_ID, Long.class).orElse(0L);

        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(3)
                .similarityThreshold(0.55)
                .filterExpression("scenicId == '" + scenicId + "'")
                .build();

        List<Document> documents = ((RedisVectorStore) vectorStore).doSimilaritySearch(request);

        StringBuilder sb = new StringBuilder();
        for (Document document : documents) {
            sb.append(document.getText()).append("\n");
        }

        return state.updateState(Map.of(RETRIEVED_DOCS, sb.toString()));
    }
}
