package cn.edu.gdou.jingbanyou.tourist.constant;

/**
 * Graph State Key 常量类
 * 统一管理 Spring AI Graph 中 OverAllState 的所有键名
 * 
 * 使用示例：
 * <pre>{@code
 * // 读取
 * String question = state.value(GraphStateKey.QUESTION, String.class).orElse("");
 * 
 * // 写入
 * Map<String, Object> result = new HashMap<>();
 * result.put(GraphStateKey.INTENT, "route_plan");
 * return result;
 * }</pre>
 * 
 * @author JingbanYou Team
 * @date 2026-04-07
 */
public final class GraphStateKey {
    
    // ==================== 输入相关 ====================
    
    /**
     * 用户问题
     * 类型：String
     * 来源：用户输入
     */
    public static final String QUESTION = "question";
    
    /**
     * 历史对话（最近 N 轮）
     * 类型：String（JSON 格式或格式化文本）
     * 来源：对话历史管理器
     */
    public static final String HISTORY = "history";
    
    /**
     * 当前景区 ID
     * 类型：Long
     * 来源：上下文/URL 参数
     */
    public static final String SCENIC_ID = "scenicId";
    
    /**
     * 用户 ID
     * 类型：Long
     * 来源：认证信息
     */
    public static final String USER_ID = "userId";
    
    // ==================== 意图识别相关 ====================
    
    /**
     * 意图分类结果
     * 类型：String
     * 可选值：route_plan / spot_question / complex_other
     * 来源：TextDistinguishNode / MultimodalDistinguishNode
     */
    public static final String INTENT = "intent";
    
    // ==================== 路线规划相关 ====================

    /**
     * 多条原始路线数据（从MCP获取）
     * 类型：List<Map> 每条路线包含 strategy(策略)、distance(距离)、duration(时长)、steps(步骤)
     * 来源：MapRouteApiInvokerNode
     */
    public static final String RAW_ROUTES = "rawRoutes";

    /**
     * 润色后的多条路线（结合用户画像）
     * 类型：List<Map> 每条路线包含 description(润色描述)、suitableFor(适合人群)、tips(提示)
     * 来源：RoutePolishNode
     */
    public static final String POLISHED_ROUTES = "polishedRoutes";
    
    // ==================== 知识问答相关 ====================
    
    /**
     * 检索到的知识文档
     * 类型：String（JSON 数组格式）
     * 来源：HybridRetrievalNode（内部使用，不对外暴露）
     */
    public static final String RETRIEVED_DOCS = "retrievedDocs";
    
    
    /**
     * 生成的答案
     * 类型：String
     * 来源：HybridRetrievalNode / GeneralChatFallbackNode
     */
    public static final String ANSWER = "answer";
    
    // ==================== 通用聊天相关 ====================
    

    /**
     * 当前节点名称
     * 类型：String
     * 来源：Graph 框架自动注入
     */
    public static final String CURRENT_NODE = "currentNode";
    
    /**
     * 处理状态
     * 类型：String
     * 可选值：success / error / pending
     */
    public static final String STATUS = "status";
    
    /**
     * 错误信息
     * 类型：String
     */
    public static final String ERROR_MESSAGE = "errorMessage";
    
    /**
     * 响应时间（毫秒）
     * 类型：Long
     */
    public static final String RESPONSE_TIME_MS = "responseTimeMs";
    
    /**
     * 使用的模型名称
     * 类型：String
     */
    public static final String MODEL_USED = "modelUsed";
    
    /**
     * 消耗的 Token 数量
     * 类型：Integer
     */
    public static final String TOKENS_USED = "tokensUsed";

    // ==================== 用户画像相关 ====================

    /**
     * 语言（zh/en）
     * 类型：String
     */
    public static final String LANGUAGE = "language";

    /**
     * 音频数据（Controller 层注入）
     * 类型：byte[]
     * 来源：ChatController / WebSocket
     */
    public static final String AUDIO_DATA = "audioData";

    /**
     * 游客唯一标识，由前端 WebSocket/HTTP 传入
     * 类型：String
     */
    public static final String VISITOR_ID = "visitorId";

    /**
     * 会话 ID（用于 Redis ChatMemory key）
     * 类型：String
     */
    public static final String SESSION_ID = "sessionId";

    /**
     * 游客画像（压缩 JSON，< 200 Token）
     * 类型：VisitorProfile
     * 来源：ProfileLoaderNode 初始化，ProfileUpdaterNode 更新
     */
    public static final String VISITOR_PROFILE = "visitorProfile";

    /**
     * 引导语（参数缺失时）
     * 类型：String
     * 来源：MapRouteApiInvokerNode（LLM 缺参回复）
     */
    public static final String GUIDE_MESSAGE = "guideMessage";

    /**
     * 路线状态标识
     * 类型：String
     * 可选值：success（正常）/ pending（缺参，等待补充信息）
     * 来源：MapRouteApiInvokerNode
     */
    public static final String ROUTE_STATUS = "routeStatus";

    /**
     * 路线缓存命中标识（用于跳过 RoutePolish 节点）
     * 类型：Boolean
     * 来源：MapRouteApiInvokerNode（命中缓存时写入 true）
     */
    public static final String ROUTE_CACHE_HIT = "routeCacheHit";

    // ==================== 情感分析相关 ====================

    /**
     * 检测到的用户情感
     * 类型：String
     * 可选值：positive / neutral / negative
     * 来源：EmotionAnalysisNode
     */
    public static final String EMOTION_DETECTED = "emotionDetected";

    /**
     * 情感置信度
     * 类型：Double
     * 来源：EmotionAnalysisNode
     */
    public static final String EMOTION_CONFIDENCE = "emotionConfidence";

    // 私有构造函数，防止实例化
    private GraphStateKey() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
