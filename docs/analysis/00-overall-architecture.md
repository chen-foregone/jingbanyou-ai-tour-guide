# 景伴游·AI数字人导览系统 — 整体架构认知

> 梳理日期：2026-05-12
> 基础框架：RuoYi-Vue 3.x（Spring Boot 3.5.0 + JDK 17）
> AI 引擎：Spring AI Alibaba Graph 1.1.2.2 + DashScope

---

## 1. 项目模块结构

```
jingbanyou-admin        (端口 9091) — 启动入口 + 管理后台 Controller
  └── jingbanyou-framework           — 框架核心（Security/Redis/Druid/MyBatis）
  │     └── jingbanyou-system        — 系统管理（用户/角色/菜单/权限）
  │           └── jingbanyou-common  — 通用基础（工具类/注解/异常/过滤器）
  ├── jingbanyou-manage             — AI 业务管理层（景区/景点/路线/FAQ/知识库/数字人）
  ├── jingbanyou-tourist            — 游客交互端（对话/RAG/TTS/ASR/画像）
  ├── jingbanyou-quartz             — 定时任务
  └── jingbanyou-generator          — 代码生成器
```

### 1.1 依赖关系

```
jingbanyou-common          (0 外部依赖)
    ↓
jingbanyou-system          (依赖 common)
    ↓
jingbanyou-framework      (依赖 system + common)
    ↓
jingbanyou-admin          (依赖 framework + manage + tourist + quartz + generator)
```

### 1.2 端口配置

| 模块 | 端口 | 说明 |
|------|------|------|
| jingbanyou-admin | 9091 | 主启动模块，管理后台 API |

---

## 2. 技术架构

### 2.0 消息队列（RabbitMQ — 请求解耦）

**用途**：将游客对话请求从 Tomcat 线程中解耦，应对高并发场景

**架构**：两阶段异步流程
```
前端 POST /tourist/chat → Controller → RabbitMQ → Consumer(执行Graph) → 流式结果写入Redis
前端 GET /tourist/stream/{cid}(SSE) → Redis Pub/Sub → 实时接收流式结果
```

**拓扑**：
| 组件 | 名称 | 说明 |
|------|------|------|
| Exchange | `jingbanyou.tourist.exchange` | Topic 类型 |
| 队列 | `tourist.chat.queue` | 核心对话处理 |
| DLX | `jingbanyou.tourist.dlx` | 死信交换机 |
| DLQ | `tourist.chat.dlq` | TTL 60s 后自动重试一次 |

**Redis Key 设计**：
| Key | 类型 | TTL | 用途 |
|-----|------|-----|------|
| `tourist:sse:events:{cid}` | LIST | 300s | SSE 事件缓冲（late-joiner） |
| `tourist:sse:status:{cid}` | STRING | 300s | 状态：pending/completed |
| `tourist:sse:{cid}` | Pub/Sub | — | 实时推送 |

**消费者配置**：concurrency=5，max-concurrency=20，prefetch=1

### 2.1 AI 对话引擎（核心）

**框架**：Spring AI Alibaba Graph 状态机

**对话流程**：
```
游客请求 → ProfileLoaderNode(加载画像)
  ↓
[有音频] → MultimodalDistinguishNode(多模态意图分类)
[纯文本] → TextDistinguishNode(文本意图分类)
  ↓
意图路由：
  ├── route_plan   → MapRouteApiInvokerNode(MCP高德路线) → RoutePolishNode(画像润色)
  ├── spot_question → HybridRetrievalNode(FAQ向量库+知识库双路检索)
  └── complex_other → GeneralChatFallbackNode(闲聊兜底)
  ↓
ProfileUpdaterNode(异步更新画像) → END
```

**意图分类**：三类（route_plan / spot_question / complex_other）

**RAG 双路检索**：
- FAQ 向量库（Redis VectorStore，topK=3）
- 知识库向量库（Redis VectorStore，topK=3，过滤 scenicId）

**7 个 ChatClient Bean**：

| Bean | 配置前缀 | 用途 |
|------|---------|------|
| textDistinguishChatClient | jingbanyou.ai.distinguish | 文本意图分类 |
| multimodalDistinguishChatClient | jingbanyou.ai.distinguish | 多模态意图分类 |
| hybridRetrievalStreamingChatClient | jingbanyou.ai.hybrid-retrieval | 混合检索流式回答 |
| generalChatStreamingChatClient | jingbanyou.ai.general-chat | 闲聊兜底 |
| mapRouteInvokerChatClient | jingbanyou.ai.map-route-invoker | 路线 MCP 调用 |
| profileUpdateChatClient | jingbanyou.ai.profile-update | 画像更新 |
| routePolishChatClient | jingbanyou.ai.route-polish | 路线润色 |

### 2.2 语音服务

**TTS**：DashScope CosyVoice v3 Flash
- 两种模式：文件模式 + 流式模式
- Caffeine 本地缓存（1h TTL，最大 500 条）
- 按音色代码区分（digitalHuman.ttsVoiceCode）

**ASR**：DashScope Paraformer v1
- 支持中文标点预测、不流畅去除

### 2.3 对话记忆

**热存储**：Redis ChatMemory（MessageChatMemoryAdvisor 自动管理）
**冷存储**：会话结束时同步到 MySQL（VisitorConversation + VisitorInteraction 表）

### 2.4 用户画像

**存储**：Redis VectorStore（独立索引 profile-index）
**数据结构**：VisitorProfile（visitorId, interestTags, groupType, visitedSpots, preferRouteType, turnCount）
**TTL**：24 小时

### 2.5 缓存策略

| 缓存 | 存储 | TTL | 用途 |
|------|------|-----|------|
| ChatMemory | Redis | 配置项 | 对话历史 |
| TTS 缓存 | Caffeine | 1h | 语音合成 |
| 首屏缓存 | Caffeine | 5min | bootstrap 数据 |
| FAQ 缓存 | Caffeine | 1min | 热门 FAQ |
| 路线缓存 | Redis | 7 天 | 高德路线结果 |

### 2.6 安全架构

**认证**：JWT + Redis 双缓存
- JWT 仅存 UUID，真实用户信息存 Redis
- Token 刷新：过期前 20 分钟自动延长 Redis TTL

**权限**：Spring Security + @EnableMethodSecurity
- 注解权限：`@PreAuthorize("@ss.hasPermi('xxx')")`
- 数据权限：`@DataScope` 注解（SQL 拼接，5 级权限）

**密码**：BCrypt + 错误次数限制（Redis，5 次锁定 10 分钟）

**限流**：Redis + Lua 脚本（@RateLimiter 注解）

**XSS**：XssFilter + RefererFilter

### 2.7 数据源

**主从双数据源**：`@DataSource(DataSourceType.SLAVE)` 切换
**连接池**：Druid（master + slave）

---

## 3. API 总览

### 3.1 游客端（/api/tourist，@Anonymous）

| 端点 | 方法 | 说明 |
|------|------|------|
| /bootstrap | GET | 首屏初始化 |
| /stream | POST(SSE) | 流式对话（核心，保留兼容） |
| /chat | POST | 提交对话请求（异步模式，返回 conversationId） |
| /stream/{conversationId} | GET(SSE) | 订阅异步对话结果流 |
| /voice/transcribe | POST | 语音转文字 |
| /chat/end | POST | 会话结束 |
| /tts | GET | TTS 合成 |
| /tts/{filename} | GET | 获取音频文件 |
| /conversation/list | GET | 会话列表 |
| /conversation/{sessionId} | GET | 会话详情 |

### 3.2 管理端（/manage/*，需认证）

| 端点 | 说明 |
|------|------|
| /manage/scenic | 景区 CRUD |
| /manage/spot | 景点 CRUD |
| /manage/route | 路线 CRUD |
| /manage/faq | FAQ CRUD + 向量化 |
| /manage/knowledge | 知识库 CRUD + 向量化 |
| /manage/digital-human | 数字人配置 |
| /manage/stats | 运营统计 |
| /manage/analysis | 游客分析 |

---

## 4. 数据库表

### 4.1 景区业务表（manage_*）

- `manage_scenic_area` — 景区信息
- `manage_scenic_spot` — 景点信息
- `manage_tour_route` — 游览路线
- `manage_route_spot_relation` — 路线-景点关联
- `manage_faq` — 常见问答（含 vectorId）
- `manage_knowledge_doc` — 知识文档
- `manage_knowledge_chunk` — 文档切片（含 embeddingVersion）
- `manage_digital_human_config` — 数字人配置
- `manage_visitor_conversation` — 访客会话
- `manage_visitor_interaction` — 访客交互记录
- `manage_visitor_analysis` — 访客分析报告
- `manage_operation_stats` — 运营统计

### 4.2 系统表（sys_*）

标准 RuoYi 系统表：sys_user / sys_role / sys_menu / sys_dept / sys_dict_type / sys_dict_data / sys_config / sys_notice / sys_logininfor / sys_oper_log 等

---

## 5. 关键配置文件

- `jingbanyou-admin/src/main/resources/application.yml` — 主配置
- DashScope API Key、阿里云 OSS 配置、JWT secret 均通过环境变量读取
- AI 模型配置（qwen-turbo / cosyvoice-v3-flash / text-embedding-v2）
- Redis 连接配置（端口 6379）
- MySQL 数据源配置

---

## 6. 项目亮点

1. **Spring AI Graph 状态机**：清晰的对话流程编排，支持多意图路由
2. **RAG 双路检索**：FAQ + 知识库并行检索，提升问答质量
3. **数字人驱动**：TTS + 画像驱动的个性化语音导览
4. **Redis 向量存储**：一站式管理向量检索，无需额外向量数据库
5. **多级缓存**：Caffeine 本地缓存 + Redis 分布式缓存分层
6. **RabbitMQ 请求解耦**：Graph 执行从 Tomcat 线程迁移到 Consumer 线程池，应对高并发
7. **RuoYi 成熟框架**：开箱即用的权限、日志、数据权限体系

---

## 7. 各分析报告索引

| 文件 | 覆盖范围 |
|------|---------|
| `01-tourist-analysis.md` | 游客端 AI 对话/RAG/TTS/ASR 模块深度分析 |
| `02-manage-analysis.md` | 管理后台 CRUD/向量化/统计模块分析 |
| `03-framework-system-analysis.md` | 框架核心（Security/JWT/AOP/Redis）分析 |
| `04-admin-common-aux-analysis.md` | 启动入口/通用模块/API规范分析 |
