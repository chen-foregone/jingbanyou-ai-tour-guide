package cn.edu.gdou.jingbanyou.tourist.graph;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphNodeNames;
import cn.edu.gdou.jingbanyou.tourist.graph.node.*;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GraphConfiguration.class);

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
        // 初始化图
        StateGraph stateGraph = new StateGraph();

        // 添加所有节点
        stateGraph.addNode(GraphNodeNames.DISTINGUISH, AsyncNodeAction.node_async(distinguishNode));
        stateGraph.addNode(GraphNodeNames.FAQ_ANSWER_POLISH, AsyncNodeAction.node_async(faqAnswerPolishNode));
        stateGraph.addNode(GraphNodeNames.GENERAL_CHAT_FALLBACK, AsyncNodeAction.node_async(generalChatFallbackNode));
        stateGraph.addNode(GraphNodeNames.MAP_ROUTE_API_INVOKER, AsyncNodeAction.node_async(mapRouteApiInvokerNode));
        stateGraph.addNode(GraphNodeNames.PROFILE_LOADER, AsyncNodeAction.node_async(profileLoaderNode));
        stateGraph.addNode(GraphNodeNames.PROFILE_UPDATER, AsyncNodeAction.node_async(profileUpdaterNode));
        stateGraph.addNode(GraphNodeNames.ROUTE_POLISH, AsyncNodeAction.node_async(routePolishNode));
        stateGraph.addNode(GraphNodeNames.SCENIC_KNOWLEDGE_ANSWER_GENERATOR, AsyncNodeAction.node_async(scenicKnowledgeAnswerGeneratorNode));
        stateGraph.addNode(GraphNodeNames.SCENIC_KNOWLEDGE_RETRIEVAL, AsyncNodeAction.node_async(scenicKnowledgeRetrievalNode));

        // 添加边：START -> distinguish
        stateGraph.addEdge(StateGraph.START, GraphNodeNames.DISTINGUISH);

        // 添加 PlantUML 打印
        GraphRepresentation representation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML, "tour guide flow");
        logger.info("\n=== Graph UML ===\n" + representation.content() + "\n==================");

        return stateGraph.compile();
    }
}
