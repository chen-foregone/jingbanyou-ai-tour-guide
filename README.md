# 景伴游 · AI 数字人交互导游系统

基于 Spring AI + 通义千问的多模态景区导游系统。游客通过文字/语音与 AI 数字人实时对话，完成景点知识问答、路线智能规划、个性化推荐。

## 功能特性

- **多意图对话** —— Spring AI Graph 状态机编排对话流程，自动识别游客意图并路由至问答/路线规划/闲聊兜底节点
- **RAG 知识问答** —— FAQ 向量库与知识库双路检索（topK=3），结果融合后注入 LLM 上下文生成回答
- **路线智能规划** —— 通过高德地图 API 实时获取路线，结合本地缓存降低重复调用
- **流式对话推送** —— SSE 流式响应，RabbitMQ 异步解耦 + Redis Pub/Sub 驱动，支持历史消息回放
- **游客画像** —— 对话中异步提取兴趣标签、出行偏好，Redis 缓存与向量库持久化
- **语音交互** —— CosyVoice v3 流式语音合成，段落级音频块异步合并至 SSE
- **熔断降级** —— Resilience4j 熔断覆盖 LLM/TTS/ASR/路线 4 个外部 API，Redis Lua 脚本实现原子限流
- **管理后台** —— 情感趋势分析、热门问答 TopN、运营大屏，为景区管理者提供量化报告

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.x, Spring AI, Spring AI Graph |
| 数据库 | MySQL + Druid |
| 缓存 | Redis / Redis Stack（向量检索 + RediSearch） |
| 消息队列 | RabbitMQ |
| AI 服务 | 通义千问 DashScope, CosyVoice TTS |
| 外部 API | 高德地图 MCP Server |

## 项目结构

```
jingbanyou/
├── jingbanyou-admin/        # 后台管理（系统、监控、用户）
├── jingbanyou-common/       # 公共工具类
├── jingbanyou-framework/    # 框架层（安全、权限、Token）
├── jingbanyou-generator/    # 代码生成器
├── jingbanyou-manage/       # 管理端业务（景点、路线管理）
├── jingbanyou-quartz/       # 定时任务
├── jingbanyou-system/       # 系统模块
├── jingbanyou-tourist/      # 游客端核心业务（AI 对话核心）
├── sql/                     # 数据库脚本
└── docs/                    # 接口文档
```

## 快速开始

### 前置要求

- JDK 17
- Maven 3.8+
- MySQL 8.0+
- Redis 7.0+（建议使用 Redis Stack 镜像以支持向量检索）
- RabbitMQ 3.x

### 1. 克隆项目

```bash
git clone https://github.com/chen-foregone/jingbanyou-ai-tour-guide.git
```

### 2. 配置环境变量

复制环境变量模板并填入真实值：

```bash
cp .env.example .env
```

需要配置的密钥：

| 环境变量 | 说明 | 获取方式 |
|----------|------|----------|
| `DASHSCOPE_API_KEY` | 通义千问 API 密钥 | [阿里云 DashScope](https://help.aliyun.com/zh/model-studio/getting-started) |
| `AMAP_API_KEY` | 高德地图 API 密钥 | [高德开放平台](https://lbs.amap.com/) |
| `OSS_ACCESS_KEY` / `OSS_SECRET_KEY` | 阿里云 OSS 密钥 | [阿里云 RAM](https://ram.console.aliyun.com/) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 数据库连接信息 | 本机 MySQL |

### 3. 导入数据库

```bash
mysql -u root -p -e "CREATE DATABASE jingbanyou CHARACTER SET utf8mb4"
mysql -u root -p jingbanyou < sql/jingbanyou.sql
```

### 4. 启动项目

```bash
mvn clean install -DskipTests
mvn spring-boot:run -pl jingbanyou-admin
```

应用默认启动在 `http://localhost:9091`

### 5. MCP Server 配置（路线规划功能）

将 `jingbanyou-admin/src/main/resources/mcp-servers-config.template.json` 复制为 `mcp-servers-config.json`，填入真实的 `AMAP_MAPS_API_KEY` 后放入原路径。

## 核心架构

### AI 对话流程

```
用户请求 -> TouristController.chat()
         -> Spring AI Graph 状态机
             +-- 意图识别节点 -> RAG 检索（FAQ + 知识库）
             +-- 路线规划节点 -> 高德地图 API
             +-- 闲聊兜底节点 -> 直接 LLM 回复
         -> SSE 流式响应推送
```

### 异步消息链路

```
AI 执行 -> RabbitMQ -> Consumer 线程池
                    -> Redis Pub/Sub -> SSE 推送到客户端
```

## 致谢

- 本项目基于 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue)（MIT）二次开发
- 感谢 [阿里云 DashScope](https://dashscope.aliyun.com/) 提供的 AI 模型服务
- 感谢 [高德开放平台](https://lbs.amap.com/) 提供的地图服务

## 许可证

MIT License。基础框架 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue) 同样使用 MIT 协议，版权归原作者所有。

## 联系

- GitHub：https://github.com/chen-foregone
- 邮箱：13232397520@163.com
