package cn.edu.gdou.jingbanyou.tourist.graph.node;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 景区知识检索节点（RAG检索）
 * 任务：从向量数据库中检索与游客问题相关的景区知识文档
 * 注意：此节点主要调用向量数据库检索，不需要LLM
 */
@Component
public class ScenicKnowledgeRetrievalNode implements NodeAction {

    //创建redisVector对象
    RedisVectorStore redisVectorStore;

    public ScenicKnowledgeRetrievalNode(RedisVectorStore redisVectorStore) {
        this.redisVectorStore = redisVectorStore;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(GraphStateKey.QUESTION, String.class).orElse("");
        Long scenicId = state.value(GraphStateKey.SCENIC_ID, Long.class).orElse(0L);


        //创建搜索请求对象
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(3)
                .similarityThreshold(0.55)
                .filterExpression("scenicId == '" + scenicId + "'")
                .build();

        //执行检索
        List<Document> documents = redisVectorStore.doSimilaritySearch(request);

        //处理查询结果
        //将文档字符串化
        StringBuilder sb = new StringBuilder();
        for (Document document : documents) {
            sb.append(document.getText()).append("\n");
        }
        String retrievedDocs = sb.toString();
        return state.updateState(Map.of(GraphStateKey.RETRIEVED_DOCS, retrievedDocs));
    }
}
