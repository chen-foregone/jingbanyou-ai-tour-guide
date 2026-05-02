# 接口文档变更记录

**文档版本：** v2（2026-04-29）
**对比版本：** v1（2026-04-14）
**变更类型：** 功能调整、接口移除、新增接口、Bug 修复

---

## 一、游客端接口变更（/api/tourist）

### 1.1 接口移除

| 原接口 | 方法 | 移除原因 |
|--------|------|---------|
| `POST /api/tourist/chat` | 阻塞式对话 | 已被 `/stream` SSE 流式接口替代，支持更高并发 |

### 1.2 接口新增

| 新接口 | 方法 | 说明 |
|--------|------|------|
| `POST /api/tourist/stream` | SSE 流式对话 | 替代原 `chat()` 接口，支持流式文本 + TTS 音频，兼容 RAG 预检短路 |
| `POST /api/tourist/chat/end` | 会话结束 | 新增，将 Redis 暂存的对话元数据同步写入 MySQL |
| `GET /api/tourist/tts/{filename}` | TTS 音频访问 | 新增，访问已合成的 TTS 音频文件 |

### 1.3 接口变更

#### `/api/tourist/chat` → `/api/tourist/stream`

| 变更项 | 旧版 | 新版 |
|--------|------|------|
| 响应方式 | 阻塞 JSON | SSE 流（多个事件） |
| TTS 音频 | 随响应返回 URL | 流式推送 Base64 chunk |
| 事件类型 | 无 | `metadata` / `answer` / `audio` / `done` / `error` |
| RAG 预检 | 无 | 支持短路快速返回 |
| 路线附件 | `attachments` 字段 | 纳入 `metadata` 事件 |
| 错误处理 | 统一 error 响应 | 错误纳入 SSE 流 |

#### `/api/tourist/tts`

| 变更项 | 旧版 | 新版 |
|--------|------|------|
| `sessionId` 参数 | 必填 | 已移除 |
| `scenicId` 参数 | 必填 | 改为可选 |

#### `/api/tourist/bootstrap`

| 变更项 | 旧版 | 新版 |
|--------|------|------|
| `digitalHuman` 字段 | 包含 `avatarImage`, `ttsVoiceCode`, `sampleAudioUrl`, `modelJsonUrl` | 精简为 `id`, `scenicId`, `humanName`, `defaultGreeting`, `lipSync`, `isDefault` |

---

## 二、管理端接口变更（/manage）

### 2.1 Bug 修复（不影响接口签名，但改变行为）

| 接口 | 变更 | 说明 |
|------|------|------|
| `POST /manage/faq` | `creator` 字段自动填充 | 修复：新增时 `creator` 字段不再报错，改为自动从 JWT Token 获取当前用户 ID 并填充 |
| `POST /manage/knowledge` | `creator` 字段自动填充 | 同上 |

### 2.2 新增接口

| 新接口 | 方法 | 说明 |
|--------|------|------|
| `GET /manage/spot/list` | 景点列表 | 新增 scenicId 可选过滤参数 |
| `GET /manage/route/list` | 路线列表 | 新增 scenicId 可选过滤参数 |
| `GET /manage/knowledge/list` | 知识库列表 | 新增 scenicId 可选过滤参数 |

### 2.3 接口路径修正

| 旧路径 | 新路径 | 说明 |
|--------|--------|------|
| （文档有误） | `/manage/digital-human/list` | 路径中连字符修正 |

---

## 三、文档结构变更

| 变更 | 说明 |
|------|------|
| 新增 3.2 景点管理（ScenicSpot）章节 | 原文档缺失，补充完整 CRUD 接口文档 |
| 新增 3.3 路线管理（TourRoute）章节 | 原文档缺失，补充完整 CRUD 接口文档 |
| 游客端 SSE 事件流详细说明 | 新增 `metadata` / `answer` / `audio` / `done` / `error` 五种事件及 data 格式 |
| RAG 预检短路机制说明 | 新增 RAG 直接命中 FAQ 时的快速返回 SSE 流示例 |
| `intent` 枚举新增 | 新增 `rag_prematch` 值，表示 FAQ 直接命中 |

---

## 四、变更汇总

| 类型 | 数量 |
|------|------|
| 接口移除 | 1 |
| 接口新增 | 5 |
| 接口行为变更 | 4 |
| Bug 修复 | 2 |
| 文档补充 | 4 |
| **合计变更** | **16 项** |

---

## 五、迁移指南

### 前端适配 `/stream` SSE 接口

**旧版（chat）：**
```javascript
const res = await fetch('/api/tourist/chat', { method: 'POST', body: JSON.stringify({...}) });
const data = await res.json();
// data.reply.content, data.voice.audioUrl
```

**新版（stream）：**
```javascript
const res = await fetch('/api/tourist/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ message, sessionId, scenicId })
});
const reader = res.body.getReader();
while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  const text = new TextDecoder().decode(value);
  const lines = text.split('\n').filter(l => l.startsWith('data:'));
  for (const line of lines) {
    const data = JSON.parse(line.replace('data:', ''));
    if (data.event === 'metadata') { /* intent, attachments */ }
    if (data.event === 'answer') { /* content */ }
    if (data.event === 'audio') { /* chunk (base64) */ }
    if (data.event === 'done') { /* totalCostMs */ }
  }
}
```
