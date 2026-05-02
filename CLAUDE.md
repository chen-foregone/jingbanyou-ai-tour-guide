# 精伴游 AI 导游项目

## 项目路径
`C:\Users\c1342\IdeaProjects\20260402154946\jingbanyou-ai-tour-guide`

## 技术栈
- Spring Boot 3.5.0 + JDK 17
- Spring AI Alibaba Graph（对话引擎）
- Redis Stack（向量存储）
- MySQL + MyBatis-Plus
- DashScope（qwen-turbo / cosyvoice / paraformer）

## 模块结构
- `jingbanyou-admin` — 管理后台入口（8081端口）
- `jingbanyou-manage` — AI 业务管理层（景区/景点/路线/FAQ/知识库/数字人）
- `jingbanyou-tourist` — 游客端（对话/RAG/TTS/ASR）
- `jingbanyou-common` — 通用模块
- `jingbanyou-framework` — 框架层
- `jingbanyou-system` — 系统模块

## 游客端 API
- `GET /api/tourist/bootstrap` — 首屏初始化
- `POST /api/tourist/stream` — SSE 流式对话
- `POST /api/tourist/chat/end` — 会话结束
- `POST /api/tourist/voice/transcribe` — 语音转文字
- `GET /api/tourist/tts` — TTS 合成

## 编译
```bash
cd C:\Users\c1342\IdeaProjects\20260402154946\jingbanyou-ai-tour-guide
JAVA_HOME="C:/Program Files/Java" mvn compile
```
