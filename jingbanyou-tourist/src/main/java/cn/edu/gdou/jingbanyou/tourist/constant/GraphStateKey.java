package cn.edu.gdou.jingbanyou.tourist.constant;

/**
 * Graph State Key 枚举
 * 统一管理 Spring AI Graph 中 OverAllState 的所有键名
 * 
 * 使用示例：
 * <pre>{@code
 * // 读取
 * String question = state.value(GraphStateKey.QUESTION.getKey(), String.class).orElse("");
 * 
 * // 写入
 * Map<String, Object> result = new HashMap<>();
 * result.put(GraphStateKey.INTENT.getKey(), "route_plan");
 * return result;
 * }</pre>
 * 
 * @author JingbanYou Team
 * @date 2026-04-07
 */
public enum GraphStateKey {
    
    // ==================== 输入相关 ====================
    
    /**
     * 用户问题
     * 类型：String
     * 来源：用户输入
     */
    QUESTION("question", String.class, "用户问题"),
    
    /**
     * 历史对话（最近 N 轮）
     * 类型：String（JSON 格式或格式化文本）
     * 来源：对话历史管理器
     */
    HISTORY("history", String.class, "历史对话"),
    
    /**
     * 当前景区 ID
     * 类型：Long
     * 来源：上下文/URL 参数
     */
    SCENIC_ID("scenicId", Long.class, "当前景区ID"),
    
    /**
     * 用户 ID
     * 类型：Long
     * 来源：认证信息
     */
    USER_ID("userId", Long.class, "用户ID"),
    
    // ==================== 意图识别相关 ====================
    
    /**
     * 意图分类结果
     * 类型：String
     * 可选值：route_plan / spot_question / complex_other
     * 来源：DistinguishNode
     */
    INTENT("intent", String.class, "意图分类结果"),
    
    // ==================== 路线规划相关 ====================
    
    /**
     * 路线起点
     * 类型：String
     * 来源：RouteParamExtractorNode
     */
    ROUTE_START("routeStart", String.class, "路线起点"),
    
    /**
     * 路线终点
     * 类型：String
     * 来源：RouteParamExtractorNode
     */
    ROUTE_END("routeEnd", String.class, "路线终点"),
    
    /**
     * 缺失的参数列表
     * 类型：List<String>
     * 来源：RouteParamExtractorNode
     */
    MISSING_PARAMS("missingParams", java.util.List.class, "缺失的路线参数"),
    
    /**
     * 路线数据（结构化）
     * 类型：String（JSON 格式）
     * 来源：地图 API / 路径规划服务
     */
    ROUTE_DATA("routeData", String.class, "路线数据（JSON）"),
    
    /**
     * 路线描述（自然语言）
     * 类型：String
     * 来源：MapRouteApiInvokerNode
     */
    ROUTE_DESCRIPTION("routeDescription", String.class, "路线导航话术"),
    
    // ==================== 知识问答相关 ====================
    
    /**
     * 检索到的知识文档
     * 类型：String（JSON 数组格式）
     * 来源：ScenicKnowledgeRetrievalNode
     */
    RETRIEVED_DOCS("retrievedDocs", String.class, "检索到的知识文档"),
    
    /**
     * FAQ 标准答案
     * 类型：String
     * 来源：FAQ 数据库
     */
    FAQ_ANSWER("faqAnswer", String.class, "FAQ标准答案"),
    
    /**
     * 生成的答案
     * 类型：String
     * 来源：ScenicKnowledgeAnswerGeneratorNode / FaqAnswerPolishNode
     */
    ANSWER("answer", String.class, "生成的回答"),
    
    // ==================== 通用聊天相关 ====================
    
    /**
     * 闲聊回复
     * 类型：String
     * 来源：GeneralChatFallbackNode
     */
    CHAT_RESPONSE("chatResponse", String.class, "闲聊回复"),
    
    /**
     * 引导语（参数缺失时）
     * 类型：String
     * 来源：MissingParamGuideNode
     */
    GUIDE_MESSAGE("guideMessage", String.class, "引导补充参数的消息"),
    
    // ==================== 元数据相关 ====================
    
    /**
     * 当前节点名称
     * 类型：String
     * 来源：Graph 框架自动注入
     */
    CURRENT_NODE("currentNode", String.class, "当前节点名称"),
    
    /**
     * 处理状态
     * 类型：String
     * 可选值：success / error / pending
     */
    STATUS("status", String.class, "处理状态"),
    
    /**
     * 错误信息
     * 类型：String
     */
    ERROR_MESSAGE("errorMessage", String.class, "错误信息"),
    
    /**
     * 响应时间（毫秒）
     * 类型：Long
     */
    RESPONSE_TIME_MS("responseTimeMs", Long.class, "响应时间（毫秒）"),
    
    /**
     * 使用的模型名称
     * 类型：String
     */
    MODEL_USED("modelUsed", String.class, "使用的AI模型"),
    
    /**
     * 消耗的 Token 数量
     * 类型：Integer
     */
    TOKENS_USED("tokensUsed", Integer.class, "消耗的Token数"),

    // ==================== 用户画像相关 ====================

    /**
     * 游客唯一标识，由前端 WebSocket/HTTP 传入
     * 类型：String
     */
    VISITOR_ID("visitorId", String.class, "游客唯一标识"),

    /**
     * 游客画像（压缩 JSON，< 200 Token）
     * 类型：VisitorProfile
     * 来源：ProfileLoaderNode 初始化，ProfileUpdaterNode 更新
     */
    VISITOR_PROFILE("visitorProfile", Object.class, "游客画像");
    
    // ==================== 枚举属性 ====================
    
    private final String key;
    private final Class<?> type;
    private final String description;
    
    GraphStateKey(String key, Class<?> type, String description) {
        this.key = key;
        this.type = type;
        this.description = description;
    }
    
    /**
     * 获取 State Key 字符串
     */
    public String getKey() {
        return key;
    }
    
    /**
     * 获取预期的数据类型（用于文档说明，实际使用时 OverAllState 为弱类型）
     */
    public Class<?> getType() {
        return type;
    }
    
    /**
     * 获取描述信息
     */
    public String getDescription() {
        return description;
    }
}
