# jingbanyou - 项目分析文档

> 自动生成 | 更新时间：2026-05-14 10:45 | 触发来源：manual

---

## 1. 项目概览

- **项目名称**：jingbanyou (景伴游)
- **项目类型**：单体应用（多模块 Maven）
- **核心功能**：AI 景区导游对话系统，支持景点知识问答、路线规划、游客画像管理
- **目标用户**：景区游客

---

## 2. 技术栈

| 层级 | 技术 | 版本/备注 |
|------|------|----------|
| 语言 | Java | JDK 17 |
| 框架 | Spring Boot | 3.x |
| 持久层 | MyBatis Plus | 3.5.9 |
| 数据库 | MySQL | 配合 Druid 连接池 |
| 缓存 | Redis / RouteCache | 本地缓存路线数据 |
| AI | Spring AI + 通义/Qianfan/Baidu | 多模型支持，流式对话 |
| 文档 | Knife4j | API 文档 |
| 分页 | PageHelper | 分页插件 |

---

## 3. 整体架构

### 3.1 模块划分

```
jingbanyou/
├── jingbanyou-admin/        # 后台管理（系统、监控、用户）
├── jingbanyou-common/       # 公共工具类
├── jingbanyou-framework/    # 框架层（安全、权限、Token）
├── jingbanyou-generator/    # 代码生成器
├── jingbanyou-manage/       # 管理端业务（景点、路线管理）
├── jingbanyou-quartz/       # 定时任务
├── jingbanyou-system/       # 系统模块
├── jingbanyou-tourist/      # 游客端核心业务 ⭐
├── bin/                     # 脚本工具
├── doc/                    # 文档
├── docs/                   # 接口文档
└── sql/                    # 数据库脚本
```

### 3.2 架构模式

- **分层架构**：Controller → Service → Mapper
- **AI 对话**：Spring AI Graph（状态机驱动的多节点对话流）
- **多模型路由**：策略模式，运行时选择通义/Qianfan/Baidu 模型

---

## 4. 核心功能实现

### 4.1 AI 对话（Spring AI Graph）

- **实现路径**：`TouristController.chat()` → `StreamingAnswerService` → `Spring AI Graph Node`
- **关键类/方法**：
  - `jingbanyou-tourist/controller/TouristController.java` — 对话入口
  - `jingbanyou-tourist/service/impl/StreamingAnswerService.java` — 流式响应
  - `jingbanyou-tourist/graph/StreamGraphConfiguration.java` — Graph 配置
  - `jingbanyou-tourist/graph/node/*` — 各对话节点（景点问答/路线规划/画像更新等）
- **数据流**：用户消息 → Graph 路由 → 节点处理 → 流式响应
- **技术亮点**：
  - 流式输出（SSE）
  - 多模型策略路由（通义/Qianfan/Baidu）
  - 对话记忆（ChatMemoryService）
  - 情感检测解耦（EmotionDetectService）

### 4.2 游客画像管理

- **实现路径**：`TouristController` → `ProfileVectorStoreService` → 向量存储
- **关键类/方法**：
  - `jingbanyou-tourist/service/impl/ProfileVectorStoreService.java` — 画像向量存储
  - `jingbanyou-tourist/config/ProfileVectorStoreConfig.java` — 向量存储配置
  - `jingbanyou-tourist/graph/node/ProfileLoaderNode.java` — 画像加载节点
  - `jingbanyou-tourist/graph/node/ProfileUpdaterNode.java` — 画像更新节点
- **技术亮点**：
  - 向量检索（RAG 风格）
  - Graph 节点解耦，职责单一

### 4.3 路线规划

- **实现路径**：`MapRouteApiInvokerNode` → 外部地图 API
- **关键类/方法**：
  - `jingbanyou-tourist/graph/node/MapRouteApiInvokerNode.java` — 地图路线 API 调用
  - `jingbanyou-tourist/graph/node/RoutePolishNode.java` — 路线优化节点
  - `jingbanyou-tourist/service/impl/RouteCacheServiceImpl.java` — 路线本地缓存
- **技术亮点**：本地缓存路线结果，避免重复调用外部 API

---

## 5. 技术亮点

- **Graph 状态机对话流**：使用 Spring AI Graph 实现多意图路由，节点职责单一易于扩展
  - 位置：`jingbanyou-tourist/graph/`
  - 说明：各对话意图（景点问答、路线规划、情感检测、画像管理）拆分为独立节点，Graph 配置统一编排
- **多模型策略路由**：运行时根据配置选择通义/Qianfan/Baidu 模型，支持模型热切换
  - 位置：`jingbanyou-tourist/config/chatclient/`
  - 说明：每种模型对应独立 ChatClient 配置，通过策略模式注入
- **流式对话输出**：SSE 流式响应，用户体验好
  - 位置：`jingbanyou-tourist/service/impl/StreamingAnswerService.java`
  - 说明：后端流式返回，前端逐步渲染
- **情感检测解耦**：作为独立 Service 和 Graph 节点，与主对话流程解耦
  - 位置：`jingbanyou-tourist/service/impl/EmotionDetectServiceImpl.java`
- **路线本地缓存**：避免重复调用外部地图 API，提升性能和稳定性
  - 位置：`jingbanyou-tourist/service/impl/RouteCacheServiceImpl.java`
- **向量检索画像**：使用向量存储实现游客画像的语义检索
  - 位置：`jingbanyou-tourist/service/impl/ProfileVectorStoreService.java`

---

## 6. 最近变更记录

| 时间 | 提交 | 变更内容 |
|------|------|---------|
| 2026-05-03 | bd3843d | chore(admin): 全场景 AI 模型升级 qwen-turbo → qwen-plus |
| 2026-05-02 | c5e906e | docs: 接口文档 v3 + 架构分析文档 + 测试适配 |
| 2026-05-02 | b5ffdd4 | chore: admin/framework 配置更新 + 路线规划 prompt 优化 |
| 2026-05-02 | e9e9c63 | refactor(tourist): Graph 节点精简 + sessionId 前端管理 + 流式对话重构 |
| 2026-05-02 | ec27384 | refactor(tourist): 服务层整理 — 接口/实现分离到 impl 子包，情感检测解耦 |

---

## 7. 数据库设计摘要

- **核心表**：
  - `tourist` — 游客基本信息
  - `scenic_spot` — 景点信息
  - `route_plan` — 路线规划
  - `conversation_history` — 对话历史
  - `tourist_profile` — 游客画像
- **表关系**：tourist 为核心，与景点/路线/对话形成一对多关系
