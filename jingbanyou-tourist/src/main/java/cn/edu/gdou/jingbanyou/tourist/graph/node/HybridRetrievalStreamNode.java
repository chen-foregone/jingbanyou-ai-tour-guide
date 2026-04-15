package cn.edu.gdou.jingbanyou.tourist.graph.node;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

import reactor.core.publisher.Flux;

/**
 * 混合检索流式节点
 * 流程：FAQ + 知识库并行检索 → 构建 context → LLM 流式生成
 */
@Component
public class HybridRetrievalStreamNode extends AnswerStreamNode {

    private final VectorStore knowledgeVectorStore;
    private final VectorStore faqVectorStore;
    private final ChatClient chatClient;

    public HybridRetrievalStreamNode(
            VectorStore knowledgeVectorStore,
            VectorStore faqVectorStore,
            @Qualifier("hybridRetrievalChatClient") ChatClient chatClient) {
        this.knowledgeVectorStore = knowledgeVectorStore;
        this.faqVectorStore = faqVectorStore;
        this.chatClient = chatClient;
    }

    @Override
    protected ChatClient getChatClient(Function<String, Object> stateGetter) {
        return chatClient;
    }

    @Override
    public String buildUserText(Function<String, Object> stateGetter) {
        String question = (String) stateGetter.apply(GraphStateKey.QUESTION);
        Long scenicId = stateGetter.apply(GraphStateKey.SCENIC_ID) instanceof Number n
                ? n.longValue() : 0L;

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
        log.info("[混合检索-流式] FAQ检索到 {} 条", faqDocs.size());

        // 2. 查询景区知识向量库
        SearchRequest kbRequest = SearchRequest.builder()
                .query(question)
                .topK(10)
                .similarityThreshold(0.0)
                .build();
        List<Document> kbAllDocs = knowledgeVectorStore.similaritySearch(kbRequest);

        List<Document> kbDocs = kbAllDocs.stream()
                .filter(doc -> {
                    Object sid = doc.getMetadata().get("scenicId");
                    if (sid == null) return false;
                    if (sid instanceof Long) return ((Long) sid).equals(scenicId);
                    if (sid instanceof Integer) return ((Integer) sid).equals(scenicId.intValue());
                    if (sid instanceof String) return sid.toString().equals(String.valueOf(scenicId));
                    return false;
                })
                .limit(3)
                .toList();

        log.info("[混合检索-流式] 知识库过滤后剩余 {} 条", kbDocs.size());

        StringBuilder kbContext = new StringBuilder();
        for (int i = 0; i < kbDocs.size(); i++) {
            Document doc = kbDocs.get(i);
            kbContext.append("景区知识").append(i + 1).append(": ").append(doc.getText()).append("\n");
        }

        // 3. 合并上下文
        if (faqDocs.isEmpty() && kbDocs.isEmpty()) {
            return "游客问题：" + question + "\n\n（未检索到相关信息）";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("游客问题：").append(question).append("\n\n");
        if (!faqDocs.isEmpty()) {
            sb.append("=== FAQ 参考资料 ===\n").append(faqContext);
        }
        if (!kbDocs.isEmpty()) {
            sb.append("=== 景区知识 ===\n").append(kbContext);
        }
        log.info("[混合检索-流式] 合并上下文，FAQ={}条 知识库={}条", faqDocs.size(), kbDocs.size());

        return sb.toString();
    }

    /**
     * 执行检索并流式生成
     */
    public Flux<String> retrieveAndStream(Function<String, Object> stateGetter) {
        String userText = buildUserText(stateGetter);
        String sessionId = getSessionId(stateGetter);

        // 未检索到内容时直接返回兜底文本
        if (userText.contains("（未检索到相关信息）")) {
            String fallback = "这个问题我暂时还不太清楚，建议您咨询景区工作人员。";
            return Flux.just(fallback);
        }

        return streamAnswer(chatClient, userText, sessionId);
    }
}
