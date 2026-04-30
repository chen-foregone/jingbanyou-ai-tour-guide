# 景伴游 AI 导游系统 接口文档

> 本文档覆盖 **jingbanyou-manage**（管理端）、**jingbanyou-tourist**（游客端）及 **jingbanyou-admin**（RuoYi-Vue 后台管理）全部接口。
>
> **更新日期：** 2026-04-29

---

## 一、基础信息

### 1.1 统一说明

| 项目 | 说明 |
|------|------|
| **Content-Type** | `application/json`（除文件上传外） |
| **字符编码** | `UTF-8` |
| **Base URL（manage）** | `/manage/*` |
| **Base URL（tourist）** | `/api/tourist/*` |
| **Base URL（admin）** | `/system/*`、`/login`、`/getInfo` 等 |
| **统一响应格式** | `{ "code": 200, "msg": "success", "data": {...} }` |
| **分页响应格式** | `{ "code": 200, "msg": "success", "rows": [...], "total": N }` |

> `TableDataInfo` 为 RuoYi 分页封装：成功 `code=200`，失败 `code=500`。

---

## 二、游客端接口（jingbanyou-tourist）

**基础路径：** `/api/tourist`

**认证说明：** 游客端接口无需登录，属于公开接口（`@Anonymous`）。

---

### 2.1 首屏初始化

#### GET `/api/tourist/bootstrap` — 前台首屏初始化数据

**功能描述：** 获取景区信息、数字人配置、欢迎语，用于前端页面初始化。

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID（Query） |

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

### 2.2 流式对话（唯一对话接口）

#### POST `/api/tourist/stream` — SSE 流式对话

**功能描述：** 游客发送消息，AI 导游通过 LangGraph 执行 RAG 检索并流式返回文本 + TTS 音频（SSE）。**不支持语音输入，需先通过 `/voice/transcribe` 将语音转为文字后再调用此接口。**

**Content-Type：** `application/json`

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `message` | String | 是 | 游客输入的文字 |
| `sessionId` | String | 是 | 会话 ID，用于多轮对话上下文 |
| `scenicId` | Long | 否 | 景区 ID |
| `visitorId` | String | 否 | 游客 ID |

**SSE 事件流说明：** 接口返回 `text/event-stream`，包含以下事件：

| 事件名 | 说明 | data 字段 |
|--------|------|----------|
| `metadata` | 意图识别结果及路线附件 | `intent`, `attachments`, `graphCostMs`, `timestamp` |
| `answer` | AI 文本回答 | `content` |
| `audio` | TTS 音频分块（Base64） | `seq`, `chunk`, `audioCostMs`, `serverTime` |
| `done` | 流结束信号 | `totalCostMs` |
| `error` | 错误信息 | `error`, `timestamp` |

**intent 返回值说明：**

| intent | 含义 |
|--------|------|
| `route_plan` | 路线规划请求 |
| `spot_question` | 景点知识查询 |
| `rag_prematch` | FAQ 直接命中（RAG 预检短路） |
| `complex_other` | 闲聊兜底 |

**intent=route_plan 时的 attachments 结构：**

```json
{
  "intent": "route_plan",
  "attachments": [
    {
      "id": "route-0",
      "title": "推荐路线描述文本",
      "summary": "适合：家庭出游 | 提示：建议上午出发",
      "duration": "约3小时"
    }
  ],
  "graphCostMs": 1234,
  "timestamp": 1712345678900
}
```

**RAG 预检命中时的 SSE 流（快速返回，无 TTS）：**

```
event: metadata
data: {"intent":"rag_prematch","graphCostMs":5,"timestamp":1712345678900}

event: answer
data: {"content":"该景点开放时间为 09:00-17:00"}

event: done
data: {"content":"","totalCostMs":15}
```

**完整对话 SSE 流（Graph 执行）：**

```
event: metadata
data: {"intent":"spot_question","graphCostMs":1200,"timestamp":1712345678900}

event: answer
data: {"content":"退思园是同里古镇的核心景点..."}

event: audio
data: {"seq":1,"chunk":"UklGRiQAAABXQVZF...","audioCostMs":50,"serverTime":1712345678950}
... (多个 audio 事件)

event: done
data: {"content":"","totalCostMs":1850}
```

---

### 2.3 会话结束

#### POST `/api/tourist/chat/end` — 结束会话并持久化

**功能描述：** 调用此接口将 Redis 中暂存的对话元数据同步写入 MySQL（`visitor_interaction` 表）。

**Content-Type：** `application/json`

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `sessionId` | String | 是 | 会话 ID |
| `scenicId` | Long | 否 | 景区 ID |
| `visitorId` | String | 否 | 游客 ID |
| `interactionType` | String | 否 | 交互类型，默认 `text` |

**响应示例：**
```json
{ "code": 200, "msg": "success", "data": "会话已保存" }
```

---

### 2.4 语音识别

#### POST `/api/tourist/voice/transcribe` — 语音转文字（ASR）

**功能描述：** 将音频文件识别为文字，使用 DashScope Paraformer 模型。

**Content-Type：** `multipart/form-data`

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `file` | File | 是 | 音频文件（支持 mp3/wav/m4a/aac，MultipartFile） |
| `language` | String | 否 | 语言提示，默认 `zh`（Query） |

**响应示例：**
```json
{
  "code": 200,
  "msg": "success",
  "data": { "text": "我想了解一下这个景点的开放时间" }
}
```

---

### 2.5 语音合成

#### GET `/api/tourist/tts` — 文字转语音（TTS）

**功能描述：** 将文本合成为音频文件，上传至阿里云 OSS，返回访问 URL。

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `text` | String | 是 | 要合成的文本（Query） |
| `scenicId` | Long | 否 | 景区 ID（用于获取数字人音色配置，Query） |

**响应示例：**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "audioUrl": "https://java-ai-c-z-h.oss-cn-beijing.aliyuncs.com/dkd-images/tts/..."
  }
}
```

#### GET `/api/tourist/tts/{filename}` — 获取 TTS 音频文件

**功能描述：** 访问已合成的 TTS 音频文件。

**Content-Type：** 返回 `audio/wav`

---

## 三、管理端接口（jingbanyou-manage）

**基础路径：** `/manage`

**权限说明：** 所有接口需要 `admin` 或 `scenic_admin` 角色，JWT Token 认证。

---

### 3.1 景区管理

#### GET `/manage/scenic/list` — 景区列表（分页）

**响应字段（ScenicAreaVO）：**

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
| `topFeatures` | List\<String\> | 功能亮点文案 |
| `quickPrompts` | List\<String\> | 快捷提问文案 |
| `starLevel` | String | 等级，如 5A |
| `status` | Integer | 状态 0-禁用 1-启用 |
| `createTime` | Date | 创建时间 |

#### GET `/manage/scenic/{id}` — 查询单个景区
#### POST `/manage/scenic` — 新增景区
#### PUT `/manage/scenic` — 修改景区
#### DELETE `/manage/scenic/{id}` — 删除景区（级联清理关联向量）
#### POST `/manage/scenic/import` — Excel 批量导入景区数据
#### GET `/manage/scenic/importTemplate` — 下载导入模板

---

### 3.2 景点管理

#### GET `/manage/spot/list` — 景点列表（分页）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `scenicId` | Long | 否 | 景区 ID（过滤） |

**响应字段（ScenicSpot）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 景点 ID |
| `scenicId` | Long | 所属景区 ID |
| `spotName` | String | 景点名称 |
| `spotType` | String | 类型：人文/自然/体验 |
| `spotLocation` | String | 位置 |
| `spotDesc` | String | 景点描述 |
| `visitDuration` | Integer | 建议游览时长（分钟） |
| `suitableSeason` | String | 适宜季节 |
| `sort` | Integer | 排序权重 |
| `status` | Integer | 状态 0-禁用 1-启用 |

#### GET `/manage/spot/{id}` — 查询景点详情
#### POST `/manage/spot` — 新增景点
#### PUT `/manage/spot` — 修改景点
#### DELETE `/manage/spot/{id}` — 删除景点（级联清理关联数据）

---

### 3.3 路线管理

#### GET `/manage/route/list` — 路线列表（分页）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `scenicId` | Long | 否 | 景区 ID（过滤） |

**响应字段（TourRoute）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 路线 ID |
| `scenicId` | Long | 所属景区 ID |
| `routeName` | String | 路线名称 |
| `routeType` | String | 类型：精华线路/深度游/亲子游 |
| `routeTime` | Integer | 建议时长（分钟） |
| `routeDesc` | String | 路线描述 |
| `suitableCrowd` | String | 适宜人群 |
| `difficultyLevel` | Integer | 难度等级 1-5 |
| `viewCount` | Integer | 浏览次数 |
| `bookCount` | Integer | 预约次数 |
| `rating` | Double | 评分 |
| `status` | Integer | 状态 0-禁用 1-启用 |

#### GET `/manage/route/{id}` — 查询路线详情
#### POST `/manage/route` — 新增路线
#### PUT `/manage/route` — 修改路线
#### DELETE `/manage/route/{id}` — 删除路线（级联清理关联数据）

---

### 3.4 FAQ 管理

#### GET `/manage/faq/list` — FAQ 列表（分页）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `scenicId` | Long | 否 | 景区 ID（过滤） |

**响应字段（Faq）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | FAQ ID |
| `scenicId` | Long | 所属景区 ID |
| `question` | String | 用户问题 |
| `answer` | String | 回答内容 |
| `answerType` | String | 回答类型：text/rich/html |
| `clickCount` | Integer | 被咨询次数 |
| `helpfulCount` | Integer | 点赞数 |
| `unhelpfulCount` | Integer | 踩数 |
| `vectorId` | String | 向量 ID（Redis） |
| `similarityThreshold` | Double | 相似度阈值，默认 0.85 |
| `creator` | Long | 创建人用户 ID |
| `status` | Integer | 状态 0-禁用 1-启用 |

#### GET `/manage/faq/{id}` — 查询单个 FAQ
#### POST `/manage/faq` — 新增 FAQ（自动触发向量化和 FAQ RAG）
#### PUT `/manage/faq` — 修改 FAQ（自动重新向量化）
#### DELETE `/manage/faq/{id}` — 删除 FAQ（同步清理 Redis 向量）
#### GET `/manage/faq/match` — 智能匹配相似问题（RAG 检索）
#### GET `/manage/faq/hot` — 热门问答 TOP
#### POST `/manage/faq/{id}/helpful` — 点赞
#### POST `/manage/faq/{id}/unhelpful` — 点踩

**POST/PUT 请求体（Faq）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 所属景区 ID |
| `question` | String | 是 | 用户问题 |
| `answer` | String | 是 | 回答内容 |
| `answerType` | String | 否 | 回答类型：text/rich/html |
| `spotId` | Long | 否 | 关联景点 ID |
| `similarQuestions` | String | 否 | 相似问法（JSON 数组） |
| `similarityThreshold` | Double | 否 | 相似度阈值，默认 0.85 |
| `status` | Integer | 否 | 状态 0-禁用 1-启用 |

---

### 3.5 知识库管理

#### GET `/manage/knowledge/list` — 知识库文档列表（分页）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `scenicId` | Long | 否 | 景区 ID（过滤） |

**响应字段（KnowledgeDoc）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 文档 ID |
| `scenicId` | Long | 所属景区 ID |
| `docTitle` | String | 文档标题 |
| `docType` | String | 类型：introduction/guide/strategy/announcement |
| `docContent` | String | 完整内容 |
| `chunkCount` | Integer | 切片数量 |
| `vectorized` | Integer | 是否已向量化 0-否 1-是 |
| `embeddingModel` | String | Embedding 模型版本 |
| `viewCount` | Integer | 被引用次数 |
| `creator` | Long | 创建人用户 ID |
| `status` | Integer | 状态 0-禁用 1-启用 |

#### GET `/manage/knowledge/{id}` — 查询知识文档
#### POST `/manage/knowledge` — 新增知识文档（`creator` 自动填充当前用户）
#### PUT `/manage/knowledge` — 修改知识文档
#### DELETE `/manage/knowledge/{id}` — 删除文档（同步清理 Redis 向量 + MySQL chunk）
#### POST `/manage/knowledge/{id}/vectorize` — 单文档向量化
#### POST `/manage/knowledge/vectorize/batch` — 全量批量向量化
#### POST `/manage/knowledge/vectorize/batch/{scenicId}` — 按景区批量向量化

**POST/PUT 请求体（KnowledgeDocRequest）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 所属景区 ID |
| `docTitle` | String | 是 | 文档标题 |
| `docType` | String | 是 | 类型：introduction/guide/strategy/announcement |
| `docContent` | String | 是 | 文档内容 |
| `docSummary` | String | 否 | 文档摘要 |
| `status` | Integer | 否 | 状态 0-禁用 1-启用 |

---

### 3.6 数字人配置管理

#### GET `/manage/digital-human/list` — 数字人列表（分页）

**响应字段（DigitalHumanConfig）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 配置 ID |
| `scenicId` | Long | 所属景区 ID |
| `humanName` | String | 数字人名称 |
| `defaultGreeting` | String | 默认问候语 |
| `modelJsonUrl` | String | Live2D 模型 JSON 地址 |
| `landscapeHeight` | BigDecimal | 横屏高度 |
| `portraitHeight` | BigDecimal | 竖屏高度 |
| `scaleX` | BigDecimal | X 缩放 |
| `offsetX` | BigDecimal | X 偏移 |
| `offsetY` | BigDecimal | Y 偏移 |
| `idleMotionGroup` | String | 空闲动作组 |
| `tapMotionGroup` | String | 点击动作组 |
| `ttsVoiceCode` | String | TTS 音色代码 |
| `lipSync` | Integer | 口型同步 0-关闭 1-开启 |
| `isDefault` | Integer | 是否默认 0-否 1-是 |

#### GET `/manage/digital-human/{id}` — 查询数字人详情
#### GET `/manage/digital-human/scenic/{scenicId}/default` — 获取景区默认数字人
#### POST `/manage/digital-human` — 新增数字人配置
#### PUT `/manage/digital-human` — 修改数字人配置
#### DELETE `/manage/digital-human/{id}` — 删除数字人配置
#### POST `/manage/digital-human/{id}/set-default` — 设置默认数字人

---

### 3.7 运营数据统计

#### GET `/manage/stats/today-overview` — 今日实时概览

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID（Query） |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "overview": {
      "totalInteractions": 0,
      "uniqueVisitors": 0,
      "avgResponseTimeMs": 0,
      "avgSatisfaction": 0.0
    }
  }
}
```

#### GET `/manage/stats/weekly-stats` — 本周运营数据统计
#### GET `/manage/stats/hot-questions` — 热门问答 TOP
#### POST `/manage/stats/generate` — 手动触发统计任务

---

### 3.8 游客感受度分析

#### GET `/manage/analysis/emotion-trend` — 情感趋势数据

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |
| `startDate` | String | 是 | 开始日期 `yyyy-MM-dd` |
| `endDate` | String | 是 | 结束日期 `yyyy-MM-dd` |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "trend": [
      { "date": "2026-04-14", "positiveCount": 445, "neutralCount": 50, "negativeCount": 0 }
    ]
  }
}
```

#### GET `/manage/analysis/focus-points` — 游客关注点 TOP
#### GET `/manage/analysis/satisfaction-trend` — 满意度趋势
#### POST `/manage/analysis/generate-daily-report` — AI 生成日报分析
#### GET `/manage/analysis/{id}` — 查看历史分析报告

---

## 四、RuoYi-Vue 后台管理接口

**认证说明：** 所有 `/system/*` 接口需携带有效 JWT Token。

### 4.1 认证登录（Auth）

#### GET `/captchaImage` — 获取验证码

**权限：** 无需认证（匿名接口）

#### POST `/login` — 用户登录

**权限：** 无需认证（匿名接口）

**请求参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | String | 是 | 用户名 |
| `password` | String | 是 | 密码 |
| `code` | String | 条件 | 验证码（`captchaEnabled=true` 时必填） |
| `uuid` | String | 条件 | 验证码 UUID（`captchaEnabled=true` 时必填） |

**响应示例：**
```json
{ "code": 200, "msg": "success", "token": "eyJhbGciOiJIUzUxMiJ9..." }
```

> 后续请求 Header：`Authorization: Bearer <token>`

#### POST `/logout` — 退出登录
#### GET `/getInfo` — 获取当前用户信息
#### GET `/getRouters` — 获取用户路由菜单

### 4.2 角色管理（Role）

**基础路径：** `/system/role`，所需权限 `system:role:*`

接口：列表、详情、新增、修改、删除、修改数据权限、修改状态、下拉选择、部门树、授权用户管理。

### 4.3 菜单管理（Menu）

**基础路径：** `/system/menu`，所需权限 `system:menu:*`

### 4.4 用户管理（User）

**基础路径：** `/system/user`，所需权限 `system:user:*`

### 4.5 个人信息（Profile）

**基础路径：** `/system/user/profile`

接口：查看个人信息、修改个人信息、重置个人密码、上传头像。

---

## 五、权限认证机制详解

### 5.1 RuoYi-Vue 认证流程

```
前端 → POST /login → SysLoginService → TokenService.createToken() → 返回 JWT
前端 → 后续请求 Header:Authorization:Bearer <token>
     → JwtAuthenticationTokenFilter → SecurityContextHolder
```

### 5.2 manage 模块统一鉴权

```java
@PreAuthorize("@ss.hasRole('admin') or @ss.hasRole('scenic_admin')")
@RestController
@RequestMapping("/manage/knowledge")
public class KnowledgeDocController { ... }
```

### 5.3 前端请求规范

```
Authorization: Bearer <token>
Content-Type: application/json  (POST/PUT)
Content-Type: multipart/form-data  (文件上传)
```

---

## 六、公共响应码说明

| code | 说明 |
|------|------|
| `200` | 操作成功 |
| `401` | 未认证（Token 失效或未提供） |
| `403` | 无权限 |
| `500` | 服务器内部错误 |
