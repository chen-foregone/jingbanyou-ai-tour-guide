# 景伴游 AI 导游系统 接口文档 v3

> **更新日期：** 2026-05-02
> **文档版本：** v3

---

## 一、基础信息

| 项目 | 说明 |
|------|------|
| **Content-Type** | `application/json`（除文件上传外） |
| **字符编码** | `UTF-8` |
| **Base URL（tourist）** | `/api/tourist/*` |
| **Base URL（manage）** | `/manage/*` |
| **统一响应格式** | `{ "code": 200, "msg": "success", "data": {...} }` |
| **分页响应格式** | `{ "code": 200, "msg": "success", "rows": [...], "total": N }` |

---

## 二、游客端接口（/api/tourist）

**认证说明：** 游客端接口无需登录（`@Anonymous`），直接调用。

---

### 2.1 首屏初始化

#### GET `/api/tourist/bootstrap`

获取景区信息、数字人配置、欢迎语，用于前端页面初始化。

**请求参数（Query）：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |

**响应示例：**

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "scenic": {
      "id": 1,
      "scenicName": "同里古镇",
      "scenicAddress": "江苏省苏州市吴江区",
      "scenicDesc": "...",
      "openTime": "09:00-17:00",
      "ticketInfo": "以景区公示为准",
      "contactPhone": "待补充",
      "coverImage": null,
      "topFeatures": ["水乡古镇", "博物馆"],
      "quickPrompts": ["这个景区好玩吗？", "有什么特色？", "门票多少钱？"],
      "starLevel": "A级",
      "status": 1
    },
    "digitalHuman": {
      "id": 1,
      "scenicId": 1,
      "humanName": "同里古镇导游",
      "defaultGreeting": "您好！欢迎来到同里古镇，有什么可以帮您？",
      "lipSync": 1,
      "isDefault": 1
    },
    "conversation": [
      {
        "id": "assistant-welcome",
        "role": "assistant",
        "content": "您好！欢迎来到同里古镇，有什么可以帮您？"
      }
    ]
  }
}
```

---

### 2.2 流式对话（核心接口）

#### POST `/api/tourist/stream` — SSE 流式对话

游客发送文字消息，AI 导游通过 Graph 做意图识别 + 检索，然后流式返回文本段落 + TTS 音频。

**Content-Type：** `application/json`

**请求参数（JSON Body）：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `message` | String | 是 | 游客输入的文字 |
| `visitorId` | String / Number | **是** | 游客 ID（支持字符串或数字） |
| `scenicId` | Number | 否 | 景区 ID |
| `sessionId` | String | 否 | 会话 ID，同一对话框内共享。不传则系统用雪花算法自动生成 |

> **sessionId 说明：** 前端应在同一对话框内的多轮消息中复用同一个 sessionId，以便 ChatMemory 能访问完整历史上下文。首轮不传，由后端生成并在 `metadata` 事件中返回，后续轮次带上该 sessionId 即可。

**响应类型：** `text/event-stream` (SSE)

**SSE 事件类型：**

| 事件名 | 说明 | data 字段 |
|--------|------|----------|
| `metadata` | 意图识别结果 + sessionId + 路线附件 | `sessionId`, `intent`, `attachments`, `graphCostMs`, `timestamp` |
| `answer` | 短应答（pending 引导语、错误提示等） | `content` |
| `answer_fragment` | AI 回答的段落（流式，多次推送） | `content` |
| `audio` | TTS 音频分块（Base64，与 answer_fragment 交叉推送） | `seq`, `chunk`, `audioCostMs`, `serverTime` |
| `done` | 流结束信号 | `totalCostMs` |
| `error` | 错误信息 | `content`, `error`, `timestamp` |

**intent 取值：**

| intent | 含义 |
|--------|------|
| `route_plan` | 路线规划 |
| `spot_question` | 景点知识问答 |
| `complex_other` | 闲聊 / 问候 / 其他 |

**SSE 事件时序说明：**
- 总是先发 `metadata`（1 次）
- 然后是 `answer_fragment` 和 `audio` **交叉推送**（`Flux.merge`），不保证严格交替顺序
- TTS 音频在后台独立合成，音频块异步合并到 SSE 流中，不会阻塞后续段落的文本到达
- 最后发 `done`（1 次）
- 如果出错，发 `error`（1 次）

**示例 1: 景点问答（正常流）**

```sse
event: metadata
data: {"sessionId":"2050486960384393216","intent":"spot_question","graphCostMs":1209,"timestamp":1712345678900}

event: answer_fragment
data: {"content":"同里古镇有退思园、珍珠塔景园、崇本堂等景点。"}

event: audio
data: {"seq":1,"chunk":"UklGRiQAAABXQVZF...","audioCostMs":50,"serverTime":1712345678950}

event: answer_fragment
data: {"content":"还有水乡婚俗和走三桥祈福等特色体验。"}

event: audio
data: {"seq":2,"chunk":"UklGRqAAAABXQVZF...","audioCostMs":120,"serverTime":1712345679020}

event: done
data: {"content":"","totalCostMs":5912}
```

**示例 2: 路线 pending（引导用户补充信息）**

```sse
event: metadata
data: {"sessionId":"2050487037073047552","intent":"route_plan","graphCostMs":520,"timestamp":1712345678900}

event: answer
data: {"content":"请告诉我您的起点和终点，我可以为您规划路线。"}

event: done
data: {"content":"","totalCostMs":530}
```

**示例 3: 检索失败兜底**

```sse
event: metadata
data: {"sessionId":"2050487037073047552","intent":"complex_other","graphCostMs":800,"timestamp":1712345678900}

event: answer
data: {"content":"抱歉，暂时无法生成回复。"}

event: done
data: {"content":"","totalCostMs":810}
```

**示例 4: 路线规划（带 attachments）**

```sse
event: metadata
data: {"sessionId":"2050487037073047552","intent":"route_plan","attachments":[{"id":"route-0","title":"推荐路线描述文本","summary":"适合：家庭出游 | 提示：建议上午出发","duration":"约3小时"}],"graphCostMs":3500,"timestamp":1712345678900}

event: answer_fragment
data: {"content":"为您推荐以下路线："}

event: audio
data: {"seq":1,"chunk":"...","audioCostMs":80,"serverTime":1712345678980}

event: answer_fragment
data: {"content":"休闲路线：从南门进入，途经退思园..."}

event: audio
data: {"seq":2,"chunk":"...","audioCostMs":150,"serverTime":1712345679130}

event: done
data: {"content":"","totalCostMs":8500}
```

---

### 2.3 会话结束

#### POST `/api/tourist/chat/end`

会话结束时调用，将 Redis 中的对话元数据持久化到 MySQL。

**请求参数（JSON Body）：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `sessionId` | String | 是 | 会话 ID（来自 `/stream` 返回的 metadata.sessionId） |
| `scenicId` | Number | 否 | 景区 ID |
| `visitorId` | String / Number | 否 | 游客 ID |
| `interactionType` | String | 否 | 交互类型，默认 `"text"` |

**响应示例：**
```json
{ "code": 200, "msg": "success", "data": "会话已保存" }
```

---

### 2.4 语音识别

#### POST `/api/tourist/voice/transcribe` — 语音转文字

**Content-Type：** `multipart/form-data`

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `file` | File | 是 | 音频文件（mp3/wav/m4a/aac） |
| `language` | String | 否 | 语言，默认 `"zh"`（Query） |

**响应示例：**
```json
{ "code": 200, "msg": "success", "data": { "text": "我想了解一下这个景点的开放时间" } }
```

---

### 2.5 语音合成

#### GET `/api/tourist/tts` — 文本转语音（非流式）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `text` | String | 是 | 合成文本（Query） |
| `scenicId` | Long | 否 | 景区 ID，用于匹配数字人音色（Query） |

**响应示例：**
```json
{ "code": 200, "msg": "success", "data": { "audioUrl": "https://..." } }
```

#### GET `/api/tourist/tts/{filename}` — 获取 TTS 音频文件

返回 `audio/wav` 二进制流。

---

### 2.6 会话历史

#### GET `/api/tourist/conversation/list` — 会话列表

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `visitorId` | String | 是 | 游客 ID（Query） |
| `scenicId` | Long | 否 | 景区 ID 过滤（Query） |
| `page` | int | 否 | 页码，默认 `1`（Query） |
| `size` | int | 否 | 每页条数，默认 `10`（Query） |

**响应示例：**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [
      {
        "sessionId": "2050486960384393216",
        "scenicId": 1,
        "title": "你好",
        "firstMessage": "你好",
        "lastMessage": "欢迎来到我们景区...",
        "turnCount": 1,
        "intentType": "complex_other",
        "emotionDetected": "neutral",
        "startTime": "2026-05-02 16:06:02",
        "endTime": "2026-05-02 16:06:09",
        "durationMs": 7000
      }
    ],
    "total": 15,
    "page": 1,
    "size": 10
  }
}
```

#### GET `/api/tourist/conversation/{sessionId}` — 会话详情

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `sessionId` | String | 是 | 会话 ID |

**响应示例：**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "sessionId": "2050486960384393216",
    "visitorId": "1",
    "scenicId": 1,
    "humanId": 1,
    "title": "你好",
    "startTime": "2026-05-02 16:06:02",
    "endTime": "2026-05-02 16:06:09",
    "intentType": "complex_other",
    "emotionDetected": "neutral",
    "emotionConfidence": 0.5,
    "durationMs": 7000,
    "turnCount": 1,
    "interactionType": "text",
    "turns": [
      { "role": "user", "content": "你好", "time": "2026-05-02 16:06:02" },
      { "role": "assistant", "content": "你好！欢迎来到我们景区，有什么可以帮你的吗？", "time": "2026-05-02 16:06:03" }
    ]
  }
}
```

---

## 三、管理端接口（/manage）

**权限说明：** 所有接口需要 `admin` 或 `scenic_admin` 角色，JWT Token 认证（Header: `Authorization: Bearer <token>`）。

---

### 3.1 景区管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/manage/scenic/list` | 景区列表（分页） |
| GET | `/manage/scenic/{id}` | 查询单个景区 |
| POST | `/manage/scenic` | 新增景区 |
| PUT | `/manage/scenic` | 修改景区 |
| DELETE | `/manage/scenic/{id}` | 删除景区（级联清理向量数据） |
| POST | `/manage/scenic/import` | Excel 批量导入景区 |
| GET | `/manage/scenic/importTemplate` | 下载导入模板 |

**景区实体字段（ScenicAreaVO）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 景区 ID |
| `scenicName` | String | 景区名称 |
| `scenicAddress` | String | 景区地址 |
| `scenicDesc` | String | 景区介绍 |
| `openTime` | String | 开放时间 |
| `ticketInfo` | String | 门票信息 |
| `contactPhone` | String | 联系电话 |
| `coverImage` | String | 封面图 URL |
| `topFeatures` | List\<String\> | 亮点文案 |
| `quickPrompts` | List\<String\> | 快捷提问文案 |
| `starLevel` | String | 等级 |
| `status` | Integer | 状态 0-禁用 1-启用 |
| `createTime` | Date | 创建时间 |

---

### 3.2 景点管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/manage/spot/list` | 景点列表（分页，可选 `scenicId` 过滤） |
| GET | `/manage/spot/{id}` | 查询景点详情 |
| POST | `/manage/spot` | 新增景点 |
| PUT | `/manage/spot` | 修改景点 |
| DELETE | `/manage/spot/{id}` | 删除景点 |

---

### 3.3 路线管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/manage/route/list` | 路线列表（分页，可选 `scenicId` 过滤） |
| GET | `/manage/route/{id}` | 查询路线详情 |
| POST | `/manage/route` | 新增路线 |
| PUT | `/manage/route` | 修改路线 |
| DELETE | `/manage/route/{id}` | 删除路线 |

---

### 3.4 FAQ 管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/manage/faq/list` | FAQ 列表（分页，可选 `scenicId` 过滤） |
| GET | `/manage/faq/{id}` | 查询单个 FAQ |
| POST | `/manage/faq` | 新增 FAQ（自动向量化） |
| PUT | `/manage/faq` | 修改 FAQ（自动重新向量化） |
| DELETE | `/manage/faq/{id}` | 删除 FAQ（同步清理 Redis 向量） |
| GET | `/manage/faq/match` | 智能匹配相似问题 |
| GET | `/manage/faq/hot` | 热门问答 TOP N |
| POST | `/manage/faq/{id}/helpful` | 点赞 |
| POST | `/manage/faq/{id}/unhelpful` | 点踩 |

**FAQ 请求体字段：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |
| `question` | String | 是 | 问题 |
| `answer` | String | 是 | 回答 |
| `answerType` | String | 否 | 类型：text/rich/html |
| `spotId` | Long | 否 | 关联景点 |
| `similarQuestions` | String | 否 | 相似问法 JSON 数组 |
| `similarityThreshold` | Double | 否 | 相似度阈值，默认 0.85 |
| `status` | Integer | 否 | 状态 |

---

### 3.5 知识库管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/manage/knowledge/list` | 文档列表（分页，可选 `scenicId` 过滤） |
| GET | `/manage/knowledge/{id}` | 查询文档详情 |
| POST | `/manage/knowledge` | 新增文档 |
| PUT | `/manage/knowledge` | 修改文档 |
| DELETE | `/manage/knowledge/{id}` | 删除文档（同步清理 Redis 向量） |
| POST | `/manage/knowledge/{id}/vectorize` | 单文档向量化 |
| POST | `/manage/knowledge/vectorize/batch` | 全量批量向量化 |
| POST | `/manage/knowledge/vectorize/batch/{scenicId}` | 按景区批量向量化 |

**文档请求体（KnowledgeDocRequest）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |
| `docTitle` | String | 是 | 文档标题 |
| `docType` | String | 是 | introduction/guide/strategy/announcement |
| `docContent` | String | 是 | 文档内容 |
| `docSummary` | String | 否 | 摘要 |
| `status` | Integer | 否 | 状态 |

---

### 3.6 数字人配置管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/manage/digital-human/list` | 数字人列表（分页） |
| GET | `/manage/digital-human/{id}` | 查询数字人详情 |
| GET | `/manage/digital-human/scenic/{scenicId}/default` | 获取景区默认数字人 |
| POST | `/manage/digital-human` | 新增数字人配置 |
| PUT | `/manage/digital-human` | 修改数字人配置 |
| DELETE | `/manage/digital-human/{id}` | 删除数字人 |
| POST | `/manage/digital-human/{id}/set-default` | 设为默认 |

---

### 3.7 运营数据统计

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/manage/stats/today-overview` | 今日实时概览（`scenicId` 必填） |
| GET | `/manage/stats/weekly-stats` | 本周运营统计 |
| GET | `/manage/stats/hot-questions` | 热门问答 TOP N |
| POST | `/manage/stats/generate` | 手动触发统计任务 |

---

### 3.8 游客分析

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/manage/analysis/emotion-trend` | 情感趋势（需 `scenicId`, `startDate`, `endDate`） |
| GET | `/manage/analysis/focus-points` | 游客关注点 TOP |
| GET | `/manage/analysis/satisfaction-trend` | 满意度趋势 |
| POST | `/manage/analysis/generate-daily-report` | AI 生成日报 |
| GET | `/manage/analysis/{id}` | 查看历史分析报告 |

---

## 四、RuoYi-Vue 后台管理接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/captchaImage` | 获取验证码（匿名） |
| POST | `/login` | 用户登录（匿名） |
| POST | `/logout` | 退出登录 |
| GET | `/getInfo` | 获取当前用户信息 |
| GET | `/getRouters` | 获取路由菜单 |
| * | `/system/role/*` | 角色管理（权限 `system:role:*`） |
| * | `/system/menu/*` | 菜单管理（权限 `system:menu:*`） |
| * | `/system/user/*` | 用户管理（权限 `system:user:*`） |
| * | `/system/user/profile/*` | 个人信息管理 |

---

## 五、公共响应码

| code | 说明 |
|------|------|
| `200` | 操作成功 |
| `401` | 未认证 |
| `403` | 无权限 |
| `500` | 服务器内部错误 |
