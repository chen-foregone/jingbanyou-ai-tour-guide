# JingbanYou AI Module - 景伴游 AI 数字人核心模块

## 📦 模块概述

本模块基于 **Spring AI Alibaba** 框架，为景区导览系统提供 AI 数字人核心能力。

### 核心技术栈

- **AI 框架**: Spring AI Alibaba 1.0.0.2
- **大模型**: 通义千问 Qwen (通过阿里云 DashScope)
- **向量数据库**: ChromaDB
- **RAG**: 检索增强生成
- **语音识别**: Whisper (待集成)
- **语音合成**: CosyVoice (待集成)
- **数字人驱动**: SadTalker + Live2D (待集成)

---

## 🚀 快速开始

### 1. 环境准备

#### JDK 要求
- JDK 17+ (推荐 JDK 21)

#### Maven 配置
确保 Maven 3.8+ 已安装

#### API Key 申请
1. 访问阿里云百炼平台：https://bailian.console.aliyun.com/
2. 注册并登录
3. 创建应用获取 API Key
4. 新用户赠送 6 个月免费额度

#### ChromaDB 部署（可选）
```bash
# Docker 方式启动 ChromaDB
docker run -p 8000:8000 chromadb/chroma:latest
```

### 2. 配置环境变量

在 `application.yml` 所在目录创建 `.env` 文件：

```bash
DASHSCOPE_API_KEY=sk-your-actual-api-key
CHROMA_HOST=localhost
CHROMA_PORT=8000
```

或者直接在 `application.yml` 中修改配置。

### 3. 编译项目

在项目根目录执行：

```bash
cd jingbanyou-ai
mvn clean install
```

### 4. 运行测试

```bash
mvn test
```

---

## 📁 目录结构

```
jingbanyou-ai/
├── src/main/java/cn/edu/gdou/jingbanyou/ai/
│   ├── config/                    # 配置类
│   │   ├── AiConfig.java          # Spring AI 核心配置
│   │   ├── VectorStoreConfig.java # ChromaDB 配置
│   │   └── DigitalHumanConfig.java # 数字人服务配置
│   ├── controller/                # REST 控制器
│   │   ├── ChatController.java    # 对话接口
│   │   └── KnowledgeController.java # 知识库管理接口
│   ├── service/                   # 业务逻辑层
│   │   ├── DigitalHumanService.java # 数字人核心服务
│   │   ├── RagService.java        # RAG 检索服务
│   │   └── VoiceService.java      # 语音处理服务
│   ├── repository/                # 数据访问层
│   │   ├── entity/                # 实体类
│   │   └── mapper/                # MyBatis Mapper
│   ├── rag/                       # RAG 相关
│   │   ├── DocumentProcessor.java # 文档处理
│   │   └── Retriever.java         # 检索策略
│   └── integration/               # 外部服务集成
│       ├── llm/                   # 大模型调用
│       ├── tts/                   # TTS 服务
│       └── stt/                   # STT 服务
├── src/main/resources/
│   └── application.yml            # 配置文件
└── pom.xml                        # Maven 依赖配置
```

---

## 🔧 核心功能

### 1. RAG 智能问答

基于向量数据库的检索增强生成（RAG）技术，实现准确的景区知识问答。

```java
// 使用示例
String answer = digitalHumanService.answerWithRag("请介绍一下这个景区的历史");
```

### 2. 个性化路线推荐

根据游客兴趣和可用时间，智能推荐游览路线。

```java
String route = digitalHumanService.recommendRoute("历史文化", 120);
```

### 3. 多轮对话上下文

支持连续对话，保持上下文连贯性。

### 4. 知识库管理

支持上传、更新、删除景区知识文档。

---

## 📝 API 接口

### 智能问答

**POST** `/ai/chat/ask`

请求参数：
- `question` (String): 用户问题

响应示例：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": "这是一个美丽的景区..."
}
```

### 路线推荐

**POST** `/ai/chat/recommend`

请求参数：
- `interest` (String): 游客兴趣
- `duration` (Integer): 可用时间（分钟）

---

## 🛠️ 开发指南

### 添加新的 AI 功能

1. 在 `service` 包下创建 Service 类
2. 注入 `ChatClient` 或 `VectorStore`
3. 编写业务逻辑
4. 在 `controller` 包下创建对应的 REST 接口

### 自定义 Prompt 模板

在 `rag/prompt` 目录下创建 Prompt 模板类：

```java
public class GuidePrompt {
    public static final String SYSTEM_PROMPT = """
        你是一个专业的景区导游助手。请基于以下背景知识回答问题：
        {context}
        
        如果知识库中没有相关信息，请诚实地告诉游客。
        """;
}
```

---

## ⚠️ 注意事项

### 安全提醒

1. **API Key 保护**
   - 切勿将 API Key 提交到 Git 仓库
   - 使用环境变量或配置中心管理敏感信息

2. **生产环境配置**
   - 使用 Nacos/Apollo 等配置中心
   - 启用 HTTPS
   - 配置 CORS 跨域限制

### 性能优化建议

1. **向量检索缓存**
   - 对常见问题建立缓存
   - 使用 Redis 缓存热点数据

2. **流式响应**
   - 对于长文本生成，使用 SSE 流式输出
   - 提升用户体验

3. **并发控制**
   - 合理设置连接池大小
   - 使用限流器防止 API 超限

---

## 🐛 常见问题

### Q1: 依赖下载失败？

A: Spring AI 未发布到 Maven 中央仓库，需添加 Spring Milestones 仓库：

```xml
<repository>
    <id>spring-milestones</id>
    <url>https://repo.spring.io/milestone</url>
</repository>
```

### Q2: API Key 无效？

A: 
1. 检查 API Key 是否正确复制
2. 确认账户有可用额度
3. 检查模型名称是否匹配

### Q3: ChromaDB 连接失败？

A:
1. 确认 ChromaDB 服务已启动
2. 检查端口映射是否正确
3. 查看防火墙设置

---

## 📚 参考资料

- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [Spring AI Alibaba GitHub](https://github.com/alibaba/spring-ai-alibaba)
- [阿里云百炼平台](https://help.aliyun.com/zh/model-studio/)
- [ChromaDB 文档](https://docs.trychroma.com/)

---

## 📄 许可证

本项目遵循与父项目相同的许可证。

---

## 👥 联系方式

如有问题，请提交 Issue 或联系开发团队。
