package cn.edu.gdou.jingbanyou.tourist.graph;

import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import cn.edu.gdou.jingbanyou.tourist.graph.node.*;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphNodeNames.*;

@Configuration
public class GraphConfiguration {

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
    private NodeAction textDistinguishNode;
    @Autowired
    private NodeAction multimodalDistinguishNode;
    @Autowired
    private NodeAction generalChatFallbackNode;
    @Autowired
    private NodeAction mapRouteApiInvokerNode;
    @Autowired
    private NodeAction profileLoaderNode;
    @Autowired
    private NodeAction profileUpdaterNode;
    @Autowired
    private NodeAction routePolishNode;
    @Autowired
    private NodeAction hybridRetrievalNode;

    // 绘制图
    @Bean
    public CompiledGraph compiledGraph() throws GraphStateException {
        // 注册所有输入 key，确保 OverAllState.input() 能正确写入这些值
        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put(GraphStateKey.SCENIC_ID, new ReplaceStrategy());
            strategies.put(GraphStateKey.SESSION_ID, new ReplaceStrategy());
            strategies.put(GraphStateKey.QUESTION, new ReplaceStrategy());
            strategies.put(GraphStateKey.HISTORY, new ReplaceStrategy());
            strategies.put(GraphStateKey.LANGUAGE, new ReplaceStrategy());
            strategies.put(GraphStateKey.VISITOR_ID, new ReplaceStrategy());
            strategies.put(GraphStateKey.AUDIO_DATA, new ReplaceStrategy());
            return strategies;
        };

        StateGraph stateGraph = new StateGraph(keyStrategyFactory);

        // 添加所有节点
        stateGraph.addNode(TEXT_DISTINGUISH, AsyncNodeAction.node_async(textDistinguishNode));
        stateGraph.addNode(MULTIMODAL_DISTINGUISH, AsyncNodeAction.node_async(multimodalDistinguishNode));
        stateGraph.addNode(GENERAL_CHAT_FALLBACK, AsyncNodeAction.node_async(generalChatFallbackNode));
        stateGraph.addNode(MAP_ROUTE_API_INVOKER, AsyncNodeAction.node_async(mapRouteApiInvokerNode));
        stateGraph.addNode(PROFILE_LOADER, AsyncNodeAction.node_async(profileLoaderNode));
        stateGraph.addNode(PROFILE_UPDATER, AsyncNodeAction.node_async(profileUpdaterNode));
        stateGraph.addNode(ROUTE_POLISH, AsyncNodeAction.node_async(routePolishNode));
        stateGraph.addNode(HYBRID_RETRIEVAL, AsyncNodeAction.node_async(hybridRetrievalNode));

        // START → ProfileLoader
        stateGraph.addEdge(StateGraph.START, PROFILE_LOADER);

        // ProfileLoader → 条件路由：audioData 存在 → 多模态，否则 → 纯文本
        stateGraph.addConditionalEdges(PROFILE_LOADER, new AsyncEdgeAction() {
            @Override
            public CompletableFuture<String> apply(OverAllState state) {
                byte[] audioData = state.value(GraphStateKey.AUDIO_DATA, byte[].class).orElse(null);
                return CompletableFuture.completedFuture(
                        audioData != null ? MULTIMODAL_DISTINGUISH : TEXT_DISTINGUISH);
            }
        }, Map.of(
                TEXT_DISTINGUISH, TEXT_DISTINGUISH,
                MULTIMODAL_DISTINGUISH, MULTIMODAL_DISTINGUISH
        ));

        // 意图识别后的条件路由
        stateGraph.addConditionalEdges(TEXT_DISTINGUISH, intentRouter(), intentRoutingMap());
        stateGraph.addConditionalEdges(MULTIMODAL_DISTINGUISH, intentRouter(), intentRoutingMap());

        // 路线规划路径：API调用 → 润色 → 画像更新 → 结束
        stateGraph.addEdge(MAP_ROUTE_API_INVOKER, ROUTE_POLISH);
        stateGraph.addEdge(ROUTE_POLISH, PROFILE_UPDATER);

        // 景点问答路径：混合检索（FAQ+知识库并行） → 画像更新 → 结束
        stateGraph.addEdge(HYBRID_RETRIEVAL, PROFILE_UPDATER);

        // 闲聊兜底路径：闲聊 → 画像更新 → 结束
        stateGraph.addEdge(GENERAL_CHAT_FALLBACK, PROFILE_UPDATER);
        stateGraph.addEdge(PROFILE_UPDATER, StateGraph.END);

        return stateGraph.compile();
    }

    /**
     * 意图路由：读取 INTENT 值
     */
    private AsyncEdgeAction intentRouter() {
        return new AsyncEdgeAction() {
            @Override
            public CompletableFuture<String> apply(OverAllState state) {
                return CompletableFuture.completedFuture(
                        state.value(GraphStateKey.INTENT).orElse("").toString());
            }
        };
    }

    /**
     * 意图 → 下一节点映射
     */
    private Map<String, String> intentRoutingMap() {
        return Map.of(
                INTENT_ROUTE_PLAN, MAP_ROUTE_API_INVOKER,
                INTENT_SPOT_QUESTION, HYBRID_RETRIEVAL,
                INTENT_COMPLEX_OTHER, GENERAL_CHAT_FALLBACK
        );
    }
}
