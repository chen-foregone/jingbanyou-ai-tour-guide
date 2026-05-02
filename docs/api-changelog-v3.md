# 接口文档变更记录 v3（前端适配指南）

> **文档版本：** v3（2026-05-02）
> **对比版本：** v2（2026-04-29）

---

## 变更概述

| 类型 | 数量 |
|------|------|
| 接口签名变更 | 1（`/stream` 请求参数） |
| SSE 事件变更 | 2（`answer` → `answer_fragment`，文本与音频交叉推送） |
| intent 移除 | 1（`rag_prematch`） |
| 新增接口 | 2（会话列表、会话详情） |
| Bug 修复 | 2 |

---

## 一、`POST /api/tourist/stream` — SSE 流式对话（重大变更）

### 请求参数变更

| 参数 | 旧版 (v2) | 新版 (v3) | 前端需要改什么 |
|------|----------|----------|---------------|
| `message` | String, 必填 | String, 必填 | **不变** |
| `sessionId` | String, 必填 | String, **可选** | **改为可选**。不传则由服务端雪花算法生成。同一对话框内应复用 sessionId 以保持上下文连续性 |
| `visitorId` | String, 可选 | String / Number, **必填** | **必须传**，支持字符串或数字 |
| `scenicId` | Long, 可选 | Number, 可选 | **不变**（但类型兼容数字/字符串） |

**新版请求体示例（首轮不传 sessionId，由后端生成）：**

```json
{
  "message": "这里有什么好玩的吗",
  "visitorId": 1,
  "scenicId": 1
}
```

**续接对话示例（传入上轮返回的 sessionId，保持上下文）：**

```json
{
  "message": "在东门",
  "visitorId": 1,
  "scenicId": 1,
  "sessionId": "2050486960384393216"
}
```

### SSE 事件变更

| 事件 | 旧版 (v2) | 新版 (v3) | 前端需要改什么 |
|------|----------|----------|---------------|
| `metadata` | 无 `sessionId` 字段 | **新增 `sessionId` 字段** | 前端需要从这个事件中获取并保存 `sessionId`，后续用于 `/chat/end`、`/conversation/{sessionId}` 等接口 |
| `answer` | 唯一的回答事件 | **仅用于短应答**（pending 引导语、错误兜底） | 保留兼容，但正常对话中不会出现 |
| `answer_fragment` | **不存在** | **段落级回答，多次推送** | **新增监听**，这是正常对话中的文本事件，每次推送一个段落 |
| `audio` | 跟随 `answer` 之后推送 | 与 `answer_fragment` **交叉推送**（`Flux.merge`），顺序不确定 | **去除**"text 完成后才有 audio"的假设，`audio` 可能在任意时刻到达 |

### metadata 事件数据变更

```json
{
  "sessionId": "2050486960384393216",   // ← 新增！前端需要保存
  "intent": "spot_question",
  "attachments": [...],                  // 仅 route_plan 时有
  "graphCostMs": 1209,
  "timestamp": 1712345678900
}
```

### intent 值变更

| 旧版有 | 新版 | 说明 |
|--------|------|------|
| `rag_prematch` | **已移除** | FAQ 命中已合并到 HybridRetrievalNode 中，不再使用独立 intent |

新版仅三种 intent：`route_plan`、`spot_question`、`complex_other`

---

## 二、新增接口

### `GET /api/tourist/conversation/list` — 会话历史列表

**用途：** 展示某个游客的历史对话记录列表。

**请求参数（Query）：**

| 参数 | 类型 | 必填 | 默认值 |
|------|------|------|--------|
| `visitorId` | String | 是 | — |
| `scenicId` | Long | 否 | — |
| `page` | int | 否 | 1 |
| `size` | int | 否 | 10 |

**响应结构：** `{ code, msg, data: { list: [...], total, page, size } }`

### `GET /api/tourist/conversation/{sessionId}` — 会话详情

**用途：** 查看某个会话的完整对话记录（对话轮次列表）。

**响应结构：** `{ code, msg, data: { sessionId, visitorId, turns: [{ role, content, time }, ...], ... } }`

---

## 三、Bug 修复

| 问题 | 修复 |
|------|------|
| `visitorId` 传数字时报 `ClassCastException` | 服务端兼容处理，数字和字符串均可 |
| `interaction_type` 列数据过长导致 MySQL 写入失败 | 统一使用 `"text"` 类型 |

---

## 四、前端迁移指南

### 4.1 `/stream` 调用方式

**旧版：**

```javascript
const response = await fetch('/api/tourist/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    message: '你好',
    sessionId: 'my-session-123',  // ← 前端自己管理
    visitorId: '1',
    scenicId: 1
  })
});
```

**新版：**

```javascript
// 首轮：不传 sessionId，由服务端生成
const response = await fetch('/api/tourist/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    message: '怎么去退思园',
    visitorId: 1,        // ← 必传！支持数字或字符串
    scenicId: 1
    // sessionId 不传，首轮由服务端生成
  })
});

// 续接（同一对话框内）：传入上轮 metadata 中返回的 sessionId
const response2 = await fetch('/api/tourist/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    message: '东门',
    visitorId: 1,
    scenicId: 1,
    sessionId: currentSessionId  // ← 复用！保持上下文连续
  })
});
```

### 4.2 SSE 流解析

```javascript
const reader = response.body.getReader();
const decoder = new TextDecoder();
let sessionId = null;  // ← 从 metadata 事件中获取
let buffer = '';

while (true) {
  const { done, value } = await reader.read();
  if (done) break;

  buffer += decoder.decode(value, { stream: true });
  const lines = buffer.split('\n');
  buffer = lines.pop(); // 保留未完成的行

  let currentEvent = null;
  for (const line of lines) {
    if (line.startsWith('event: ')) {
      currentEvent = line.slice(7).trim();
    } else if (line.startsWith('data: ')) {
      const data = JSON.parse(line.slice(6));

      switch (currentEvent) {
        case 'metadata':
          sessionId = data.sessionId;  // ← 保存 sessionId
          handleMetadata(data);
          break;
        case 'answer_fragment':        // ← 新事件名！
          appendText(data.content);
          break;
        case 'answer':                 // ← 仅 pending/兜底情况
          appendText(data.content);
          break;
        case 'audio':
          playAudioChunk(data.seq, data.chunk);  // ← 与文本交叉到达
          break;
        case 'done':
          finishStream(data.totalCostMs);
          break;
        case 'error':
          showError(data.error);
          break;
      }
    }
  }
}
```

### 4.3 关键的架构变化

1. **sessionId 交由服务端管理** — 不再需要前端生成或跟踪 sessionId。首次从 `metadata` 事件获取后保存，用于：
   - `POST /api/tourist/chat/end`（结束会话）
   - `GET /api/tourist/conversation/{sessionId}`（查看详情）

2. **文本和音频不再串行** — `answer_fragment` 和 `audio` 事件可能交叉到达。UI 需要同时处理：
   - 文本到达 → 追加显示
   - 音频到达 → 加入播放队列（按 `seq` 排序播放）

3. **`answer` vs `answer_fragment`** — 正常对话中只会出现 `answer_fragment`（可能多次）。`answer` 事件仅在两种情况出现：
   - 路线 pending 时的引导语（如"请告诉我您的起点和终点"）
   - 检索失败时的兜底回答

### 4.4 对话结束

```javascript
// 会话结束时调用
await fetch('/api/tourist/chat/end', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    sessionId: sessionId,  // ← 从 metadata 事件中获取的
    scenicId: 1,
    visitorId: 1
  })
});
```

### 4.5 加载历史会话列表

```javascript
const res = await fetch(
  `/api/tourist/conversation/list?visitorId=1&scenicId=1&page=1&size=10`
);
const { data } = await res.json();
// data.list: 会话列表
// data.total: 总数
```

### 4.6 加载会话详情

```javascript
const res = await fetch(`/api/tourist/conversation/${sessionId}`);
const { data } = await res.json();
// data.turns: [{ role, content, time }, ...]
```

---

## 五、总结

前端需要改动的内容按优先级排序：

| 优先级 | 改动 | 原因 |
|--------|------|------|
| **P0** | `visitorId` 改为必传 | 否则接口直接返回 error |
| **P0** | 移除请求中的 `sessionId` | 参数已废弃 |
| **P0** | 监听 `metadata` 事件获取 `sessionId` | 后续接口依赖 |
| **P0** | 监听 `answer_fragment` 事件（原 `answer`） | 否则看不到 AI 回复 |
| **P1** | 文本和音频解耦显示 | 交叉推送，不能假设顺序 |
| **P2** | 接入 `/conversation/list` 和 `/conversation/{sessionId}` | 新增功能 |
