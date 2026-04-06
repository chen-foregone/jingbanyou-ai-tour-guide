package cn.edu.gdou.jingbanyou.tourist.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 景区知识检索节点（RAG检索）
 * 任务：从向量数据库中检索与游客问题相关的景区知识文档
 * 注意：此节点主要调用向量数据库检索，不需要LLM
 */
@Component
public class ScenicKnowledgeRetrievalNode implements NodeAction {

    public ScenicKnowledgeRetrievalNode() {
        // 知识检索节点主要通过向量数据库实现，不需要ChatClient
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // TODO: 实现景区知识检索逻辑
        // 1. 从state中获取question
        // 2. 将问题转换为向量
        // 3. 在向量数据库中检索相似文档
        // 4. 将检索结果放入state
        return new HashMap<>();
    }
}
