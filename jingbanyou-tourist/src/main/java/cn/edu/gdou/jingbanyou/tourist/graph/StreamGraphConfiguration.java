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

/**
 * 流式对话 Graph 配置
 * 所有节点直接执行业务逻辑，结果写入 state
 * Controller 读取 state.ANSWER 进行流式发送
 */
@Configuration
public class StreamGraphConfiguration {

    // 意图识别节点
    @Autowired
    private NodeAction textDistinguishNode;
    @Autowired
    private NodeAction multimodalDistinguishNode;

    // 用户画像节点
    @Autowired
    private NodeAction profileLoaderNode;
    @Autowired
    private NodeAction profileUpdaterNode;

    // 业务处理节点（直接执行业务逻辑，写入 state）
    @Autowired
    private NodeAction mapRouteApiInvokerNode;
    @Autowired
    private NodeAction hybridRetrievalNode;
    @Autowired
    private NodeAction generalChatFallbackNode;
    @Autowired
    private NodeAction routePolishNode;
    @Autowired
    private NodeAction emotionAnalysisNode;

    @Bean
    public CompiledGraph streamCompiledGraph() throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put(GraphStateKey.SCENIC_ID, new ReplaceStrategy());
            strategies.put(GraphStateKey.SESSION_ID, new ReplaceStrategy());
            strategies.put(GraphStateKey.QUESTION, new ReplaceStrategy());
            strategies.put(GraphStateKey.HISTORY, new ReplaceStrategy());
            strategies.put(GraphStateKey.LANGUAGE, new ReplaceStrategy());
            strategies.put(GraphStateKey.VISITOR_ID, new ReplaceStrategy());
            strategies.put(GraphStateKey.AUDIO_DATA, new ReplaceStrategy());
            strategies.put(GraphStateKey.ANSWER, new ReplaceStrategy());
            strategies.put(GraphStateKey.INTENT, new ReplaceStrategy());
            strategies.put(GraphStateKey.RAW_ROUTES, new ReplaceStrategy());
            strategies.put(GraphStateKey.POLISHED_ROUTES, new ReplaceStrategy());
            strategies.put(GraphStateKey.ROUTE_STATUS, new ReplaceStrategy());
            strategies.put(GraphStateKey.GUIDE_MESSAGE, new ReplaceStrategy());
            strategies.put(GraphStateKey.VISITOR_PROFILE, new ReplaceStrategy());
            strategies.put(GraphStateKey.ROUTE_CACHE_HIT, new ReplaceStrategy());
            return strategies;
        };

        StateGraph stateGraph = new StateGraph(keyStrategyFactory);

        // 添加所有节点
        stateGraph.addNode(TEXT_DISTINGUISH, AsyncNodeAction.node_async(textDistinguishNode));
        stateGraph.addNode(MULTIMODAL_DISTINGUISH, AsyncNodeAction.node_async(multimodalDistinguishNode));
        stateGraph.addNode(PROFILE_LOADER, AsyncNodeAction.node_async(profileLoaderNode));
        stateGraph.addNode(PROFILE_UPDATER, AsyncNodeAction.node_async(profileUpdaterNode));
        stateGraph.addNode(MAP_ROUTE_API_INVOKER, AsyncNodeAction.node_async(mapRouteApiInvokerNode));
        stateGraph.addNode(HYBRID_RETRIEVAL, AsyncNodeAction.node_async(hybridRetrievalNode));
        stateGraph.addNode(GENERAL_CHAT_FALLBACK, AsyncNodeAction.node_async(generalChatFallbackNode));
        stateGraph.addNode(ROUTE_POLISH, AsyncNodeAction.node_async(routePolishNode));

        // START → ProfileLoader
        stateGraph.addEdge(StateGraph.START, PROFILE_LOADER);

        // ProfileLoader → 条件路由
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

        // 路线规划路径：缓存命中时跳过 RoutePolish，直接到 ProfileUpdater
        stateGraph.addConditionalEdges(MAP_ROUTE_API_INVOKER, routeStatusRouter(), Map.of(
                "success", ROUTE_POLISH,
                "pending", PROFILE_UPDATER,
                "cache_hit", PROFILE_UPDATER
        ));
        stateGraph.addEdge(ROUTE_POLISH, PROFILE_UPDATER);

        // 景点问答路径
        stateGraph.addEdge(HYBRID_RETRIEVAL, PROFILE_UPDATER);

        // 闲聊兜底路径
        stateGraph.addEdge(GENERAL_CHAT_FALLBACK, PROFILE_UPDATER);
        stateGraph.addEdge(PROFILE_UPDATER, StateGraph.END);

        return stateGraph.compile();
    }

    private AsyncEdgeAction intentRouter() {
        return new AsyncEdgeAction() {
            @Override
            public CompletableFuture<String> apply(OverAllState state) {
                return CompletableFuture.completedFuture(
                        state.value(GraphStateKey.INTENT).orElse("").toString());
            }
        };
    }

    private Map<String, String> intentRoutingMap() {
        return Map.of(
                StreamGraphConfiguration.INTENT_ROUTE_PLAN, MAP_ROUTE_API_INVOKER,
                StreamGraphConfiguration.INTENT_SPOT_QUESTION, HYBRID_RETRIEVAL,
                StreamGraphConfiguration.INTENT_COMPLEX_OTHER, GENERAL_CHAT_FALLBACK
        );
    }

    private AsyncEdgeAction routeStatusRouter() {
        return new AsyncEdgeAction() {
            @Override
            public CompletableFuture<String> apply(OverAllState state) {
                // 缓存命中时跳过 RoutePolish，直接到 ProfileUpdater
                Boolean cacheHit = state.value(GraphStateKey.ROUTE_CACHE_HIT, Boolean.class).orElse(false);
                if (Boolean.TRUE.equals(cacheHit)) {
                    return CompletableFuture.completedFuture("cache_hit");
                }
                String status = state.value(GraphStateKey.ROUTE_STATUS).orElse("success").toString();
                return CompletableFuture.completedFuture(status);
            }
        };
    }

    // 意图常量
    public static final String INTENT_ROUTE_PLAN = "route_plan";
    public static final String INTENT_SPOT_QUESTION = "spot_question";
    public static final String INTENT_COMPLEX_OTHER = "complex_other";
}
