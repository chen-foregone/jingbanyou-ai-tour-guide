package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 任务：FAQ 和景区知识库并行检索，一次 LLM 调用返回答案和置信度
 * 流程：
 *   1. 查询 FAQ 向量库（topK=3）
 *   2. 查询景区知识向量库（topK=3）
 *   3. 一次 LLM 调用，同时返回 answer + confidence + reason
 *
 * @author jingbanyou
 */
@Slf4j
@Component
public class HybridRetrievalNode implements NodeAction {

    private final VectorStore knowledgeVectorStore;
    private final VectorStore faqVectorStore;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        // 3. 构建上下文
        String userText;
        if (faqDocs.isEmpty() && kbDocs.isEmpty()) {
            userText = "游客问题：" + question + "\n\n（未检索到相关信息）";
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
            userText = sb.toString();
        }

        // 4. 一次 LLM 调用，同时返回答案和置信度
        String content;
        if (faqDocs.isEmpty() && kbDocs.isEmpty()) {
            content = "这个问题我暂时还不太清楚，建议您咨询景区工作人员。";
            log.info("[混合检索] 无检索结果，使用默认回复");
        } else {
            String rawOutput = chatClient.prompt()
                    .user(userText)
                    .advisors(ctx -> ctx.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();
            log.info("[混合检索] LLM原始输出: {}", rawOutput);

            try {
                String cleaned = rawOutput.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                JsonNode json = objectMapper.readTree(cleaned);
                content = json.get("answer").asText();
                String confidence = json.has("confidence") ? json.get("confidence").asText() : "medium";
                String reason = json.has("reason") ? json.get("reason").asText() : "";
                log.info("[混合检索] 置信度={}, reason={}", confidence, reason);
            } catch (Exception e) {
                log.warn("[混合检索] JSON解析失败，使用原始输出", e);
                content = rawOutput;
            }
        }

        log.info("[混合检索] 最终答案: {}", content);
        return state.updateState(Map.of(ANSWER, content));
    }
}
