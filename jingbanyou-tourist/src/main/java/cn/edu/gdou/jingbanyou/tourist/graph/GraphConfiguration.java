package cn.edu.gdou.jingbanyou.tourist.graph;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphNodeNames;
import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import cn.edu.gdou.jingbanyou.tourist.graph.node.*;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphNodeNames.*;

@Configuration
public class GraphConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GraphConfiguration.class);

    // ==================== 意图识别常量 ====================
    
    /**
     * 路线规划意图
     */
    public static final String INTENT_ROUTE_PLAN = "route_plan";
    
    /**
     * 景点问答意图
     */
    public static final String INTENT_SPOT_QUESTION = "spot_question";
    
    /**
     * 复杂其他/闲聊意图
     */
    public static final String INTENT_COMPLEX_OTHER = "complex_other";

    // 声明所有节点
    @Autowired
    private DistinguishNode distinguishNode;
    @Autowired
    private FaqAnswerPolishNode faqAnswerPolishNode;
    @Autowired
    private GeneralChatFallbackNode generalChatFallbackNode;
    @Autowired
    private MapRouteApiInvokerNode mapRouteApiInvokerNode;
    @Autowired
    private ProfileLoaderNode profileLoaderNode;
    @Autowired
    private ProfileUpdaterNode profileUpdaterNode;
    @Autowired
    private RoutePolishNode routePolishNode;
    @Autowired
    private ScenicKnowledgeAnswerGeneratorNode scenicKnowledgeAnswerGeneratorNode;
    @Autowired
    private ScenicKnowledgeRetrievalNode scenicKnowledgeRetrievalNode;

    // 绘制图
    @Bean
    public CompiledGraph compiledGraph() throws GraphStateException {
        // 初始化图（无需 KeyStrategyFactory，用默认策略）
        StateGraph stateGraph = new StateGraph();

        // 添加所有节点
        stateGraph.addNode(DISTINGUISH, AsyncNodeAction.node_async(distinguishNode));
        stateGraph.addNode(FAQ_ANSWER_POLISH, AsyncNodeAction.node_async(faqAnswerPolishNode));
        stateGraph.addNode(GENERAL_CHAT_FALLBACK, AsyncNodeAction.node_async(generalChatFallbackNode));
        stateGraph.addNode(MAP_ROUTE_API_INVOKER, AsyncNodeAction.node_async(mapRouteApiInvokerNode));
        stateGraph.addNode(PROFILE_LOADER, AsyncNodeAction.node_async(profileLoaderNode));
        stateGraph.addNode(PROFILE_UPDATER, AsyncNodeAction.node_async(profileUpdaterNode));
        stateGraph.addNode(ROUTE_POLISH, AsyncNodeAction.node_async(routePolishNode));
        stateGraph.addNode(SCENIC_KNOWLEDGE_ANSWER_GENERATOR, AsyncNodeAction.node_async(scenicKnowledgeAnswerGeneratorNode));
        stateGraph.addNode(SCENIC_KNOWLEDGE_RETRIEVAL, AsyncNodeAction.node_async(scenicKnowledgeRetrievalNode));

        // 添加边
        stateGraph.addEdge(StateGraph.START, DISTINGUISH);

        //路径识别
        stateGraph.addConditionalEdges(DISTINGUISH, new AsyncEdgeAction() {
            @Override
            public CompletableFuture<String> apply(OverAllState state) {
                return CompletableFuture.completedFuture(state.value(GraphStateKey.INTENT).orElse("").toString());
            }
        }, Map.of(INTENT_ROUTE_PLAN, MAP_ROUTE_API_INVOKER, INTENT_SPOT_QUESTION, SCENIC_KNOWLEDGE_RETRIEVAL, INTENT_COMPLEX_OTHER, GENERAL_CHAT_FALLBACK));

        //路径规划
        stateGraph.addNode(DISTINGUISH ,AsyncNodeAction.node_async(mapRouteApiInvokerNode));
        stateGraph.addNode(MAP_ROUTE_API_INVOKER ,AsyncNodeAction.node_async(routePolishNode));
        stateGraph.addEdge(ROUTE_POLISH , StateGraph.END);

        //景区问题
        stateGraph.addNode(DISTINGUISH ,AsyncNodeAction.node_async(scenicKnowledgeRetrievalNode));
        stateGraph.addNode(SCENIC_KNOWLEDGE_RETRIEVAL ,AsyncNodeAction.node_async(scenicKnowledgeAnswerGeneratorNode));
        stateGraph.addEdge(SCENIC_KNOWLEDGE_ANSWER_GENERATOR , StateGraph.END);

        //闲聊
        stateGraph.addNode(DISTINGUISH ,AsyncNodeAction.node_async(generalChatFallbackNode));
        stateGraph.addEdge(GENERAL_CHAT_FALLBACK , StateGraph.END);

        return stateGraph.compile();
    }
}
