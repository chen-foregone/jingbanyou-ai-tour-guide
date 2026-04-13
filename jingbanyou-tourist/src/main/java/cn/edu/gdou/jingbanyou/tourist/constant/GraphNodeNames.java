package cn.edu.gdou.jingbanyou.tourist.constant;

/**
 * Graph 节点名称常量类
 * 统一管理 Spring AI Graph 中所有节点的名称
 * 
 * 使用示例：
 * <pre>{@code
 * // 添加节点
 * stateGraph.addNode(GraphNodeNames.DISTINGUISH, AsyncNodeAction.node_async(distinguishNode));
 * 
 * // 条件路由
 * stateGraph.addConditionalEdges(
 *     GraphNodeNames.DISTINGUISH,
 *     edgeRouter,
 *     Map.of(
 *         "route_plan", GraphNodeNames.MAP_ROUTE_API_INVOKER,
 *         "spot_question", GraphNodeNames.HYBRID_RETRIEVAL,
 *         "complex_other", GraphNodeNames.GENERAL_CHAT_FALLBACK
 *     )
 * );
 * }</pre>
 * 
 * @author JingbanYou Team
 * @date 2026-04-10
 */
public final class GraphNodeNames {
    
    // ==================== 意图识别相关 ====================

    /**
     * 意图分类器 — 纯文本路径
     * 功能：将纯文本问题分为 route_plan / spot_question / complex_other 三类
     * 输入：QUESTION
     * 输出：INTENT
     */
    public static final String TEXT_DISTINGUISH = "textDistinguish";

    /**
     * 意图分类器 — 多模态路径（音频 + 文字）
     * 功能：从音频中提取问题并分类意图
     * 输入：QUESTION, AUDIO_DATA
     * 输出：INTENT
     */
    public static final String MULTIMODAL_DISTINGUISH = "multimodalDistinguish";
    
    // ==================== 路线规划相关 ====================
    
    /**
     * 地图路线 API 调用节点
     * 功能：调用地图服务 API 获取路线数据
     * 输入：QUESTION, SCENIC_ID
     * 输出：RAW_ROUTES, ROUTE_DESCRIPTION, GUIDE_MESSAGE（缺参时）
     */
    public static final String MAP_ROUTE_API_INVOKER = "mapRouteApiInvoker";
    
    /**
     * 路线润色节点
     * 功能：结合用户画像对多条路线进行个性化润色
     * 输入：RAW_ROUTES, VISITOR_PROFILE
     * 输出：POLISHED_ROUTES
     */
    public static final String ROUTE_POLISH = "routePolish";
    
    // ==================== 知识问答相关 ====================

    /**
     * 混合检索节点
     * 功能：FAQ 和景区知识库并行检索，一次 LLM 生成答案
     * 输入：QUESTION, SCENIC_ID
     * 输出：ANSWER
     */
    public static final String HYBRID_RETRIEVAL = "hybridRetrieval";
    
    // ==================== 通用聊天相关 ====================
    
    /**
     * 通用聊天兜底节点
     * 功能：处理闲聊、问候等非业务相关问题
     * 输入：QUESTION, HISTORY
     * 输出：CHAT_RESPONSE
     */
    public static final String GENERAL_CHAT_FALLBACK = "generalChatFallback";
    
    // ==================== 用户画像相关 ====================
    
    /**
     * 用户画像加载节点
     * 功能：从 Redis 加载用户画像到 State
     * 输入：VISITOR_ID
     * 输出：VISITOR_PROFILE
     */
    public static final String PROFILE_LOADER = "profileLoader";
    
    /**
     * 用户画像更新节点
     * 功能：提取兴趣标签、更新已访问景点、累加对话轮数
     * 输入：QUESTION, ANSWER, VISITOR_PROFILE
     * 输出：VISITOR_PROFILE（更新后）
     * 注意：异步写入 Redis（TTL 24h）
     */
    public static final String PROFILE_UPDATER = "profileUpdater";
    
    // ==================== 特殊节点 ====================
    
    /**
     * 起始节点（START）
     * Graph 框架内置，无需手动添加
     */
    public static final String START = "__START__";
    
    /**
     * 结束节点（END）
     * Graph 框架内置，无需手动添加
     */
    public static final String END = "__END__";
    
    // 私有构造函数，防止实例化
    private GraphNodeNames() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
