# 景伴游 AI 导游系统 接口文档

> 本文档覆盖 **jingbanyou-manage**（管理端）、**jingbanyou-tourist**（游客端）及 **jingbanyou-admin**（RuoYi-Vue 后台管理）全部接口。

---

## 一、基础信息

### 1.1 统一说明

| 项目 | 说明 |
|------|------|
| **Content-Type** | `application/json`（除文件上传外） |
| **字符编码** | `UTF-8` |
| **Base URL（manage）** | `/manage/*` |
| **Base URL（tourist）** | `/tourist/*` |
| **Base URL（admin）** | `/system/*`、`/login`、`/getInfo` 等 |
| **统一响应格式** | `{ "code": 200, "msg": "success", "data": {...} }` |
| **分页响应格式** | `{ "code": 200, "msg": "success", "rows": [...], "total": N }` |

> `TableDataInfo` 为 RuoYi 分页封装：成功 `code=200`，失败 `code=500`。

---

## 二、游客端接口（jingbanyou-tourist）

**基础路径：** `/api/tourist`

**认证说明：** 游客端接口无需登录，属于公开接口。

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
      "scenicName": "白云山风景区",
      "scenicAddress": "广东省广州市白云区",
      "scenicDesc": "...",
      "openTime": "08:00-18:00",
      "ticketInfo": "门票60元",
      "contactPhone": "020-12345678",
      "coverImage": "https://...",
      "topFeatures": ["智能导览", "AI讲解"],
      "quickPrompts": ["开放时间", "门票价格", "推荐路线"],
      "starLevel": "5A",
      "status": 1,
      "createTime": "2026-04-01 10:00:00"
    },
    "digitalHuman": {
      "id": 1,
      "scenicId": 1,
      "humanName": "小景",
      "avatarImage": "https://...",
      "defaultGreeting": "欢迎来到白云山...",
      "modelJsonUrl": "https://...",
      "ttsVoiceCode": "longhua",
      "sampleAudioUrl": "https://..."
    },
    "conversation": [
      {
        "id": "assistant-welcome",
        "role": "assistant",
        "content": "欢迎来到白云山风景区，我是您的专属 AI 导游..."
      }
    ]
  }
}
```

---

### 2.2 对话

#### POST `/api/tourist/chat` — 文字对话

**功能描述：** 游客发送文字消息，AI 导游基于 LangGraph 执行 RAG 检索并返回回答，同时合成语音。

**请求体（application/json）：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `message` | String | 是 | 游客输入的文字 |
| `sessionId` | String | 是 | 会话 ID，用于多轮对话上下文 |
| `scenicId` | Long | 否 | 景区 ID |

**请求示例：**
```json
{
  "message": "这个景点的开放时间是几点？",
  "sessionId": "sess_abc123",
  "scenicId": 1
}
```

**响应示例：**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "reply": {
      "id": "assistant-1712345678900",
      "role": "assistant",
      "content": "该景点开放时间为 08:00-18:00...",
      "attachments": []
    },
    "intent": "spot_question",
    "voice": {
      "audioUrl": "https://java-ai-c-z-h.oss-cn-beijing.aliyuncs.com/dkd-images/tts/sess_abc123/1712345678900.mp3"
    }
  }
}
```

**intent 返回值说明：**

| intent | 含义 |
|--------|------|
| `route_plan` | 路线规划请求 |
| `spot_question` | 景点知识查询 |
| `complex_other` | 闲聊兜底 |

**intent=route_plan 时的 attachments 结构：**

```json
{
  "attachments": [
    {
      "id": "routes-1712345678900",
      "type": "routes",
      "eyebrow": "路线推荐",
      "title": "为您推荐以下游览路线",
      "meta": "3 条路线",
      "items": [
        {
          "id": "route-0",
          "title": "精华路线",
          "summary": "适合：家庭出游 | 提示：建议上午出发",
          "duration": "约3小时",
          "tags": ["家庭出游", "经典打卡"]
        }
      ]
    }
  ]
}
```

---

### 2.3 语音合成

#### POST `/api/tourist/tts` — 文字转语音（TTS）

**功能描述：** 将文本合成为 MP3 音频，上传至阿里云 OSS，返回访问 URL。

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `text` | String | 是 | 要合成的文本（Query） |
| `scenicId` | Long | 是 | 景区 ID（用于获取数字人音色配置，Query） |
| `sessionId` | String | 是 | 会话 ID（用于生成文件名，Query） |

**响应示例：**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "audioUrl": "https://java-ai-c-z-h.oss-cn-beijing.aliyuncs.com/dkd-images/tts/sess_abc123/1712345678900.mp3"
  }
}
```

**OSS 存储路径：** `dkd-images/tts/{sessionId}/{timestamp}.mp3`

---

### 2.4 语音识别

#### POST `/api/tourist/voice/transcribe` — 语音转文字（ASR）

**功能描述：** 将音频文件识别为文字，使用 DashScope Paraformer 模型。

**Content-Type：** `multipart/form-data`

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `file` | File | 是 | 音频文件（支持 mp3/wav/m4a/aac，MultipartFile） |
| `language` | String | 否 | 语言提示，默认 `zh`（Query） |

**响应示例：**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "text": "我想了解一下这个景点的开放时间"
  }
}
```

---

## 三、管理端接口（jingbanyou-manage）

**基础路径：** `/manage`

**权限说明：** 所有接口需要 `admin` 或 `scenic_admin` 角色，标注在类级别：

```java
@PreAuthorize("@ss.hasRole('admin') or @ss.hasRole('scenic_admin')")
```

---

### 3.1 景区管理

#### GET `/manage/scenic/list` — 景区列表（分页）

> 注意：列表和详情接口返回的是 `ScenicAreaVO`，不是 `ScenicArea`。

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
| `createTime` | Date | 创建时间，格式 `yyyy-MM-dd HH:mm:ss` |

---

#### GET `/manage/scenic/{id}` — 查询单个景区

> 返回 `ScenicAreaVO`，字段同上。

#### POST `/manage/scenic` — 新增景区
#### PUT `/manage/scenic` — 修改景区

**请求体（ScenicArea）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | Long | 条件 | 景区 ID，更新时必填 |
| `scenicName` | String | 是 | 景区名称 |
| `scenicAddress` | String | 是 | 景区地址 |
| `scenicDesc` | String | 否 | 景区总体介绍 |
| `openTime` | String | 否 | 开放时间 |
| `ticketInfo` | String | 否 | 门票信息 |
| `contactPhone` | String | 否 | 联系电话 |
| `officialWebsite` | String | 否 | 官方网站 |
| `coverImage` | String | 否 | 封面图 URL |
| `topFeatures` | List\<String\> | 否 | 功能亮点文案数组 |
| `quickPrompts` | List\<String\> | 否 | 快捷提问文案数组 |
| `starLevel` | String | 否 | 景区等级，如 5A |
| `status` | Integer | 否 | 状态 0-禁用 1-启用 |
| `sort` | Integer | 否 | 排序权重 |

#### DELETE `/manage/scenic/{id}` — 删除景区

#### POST `/manage/scenic/import` — Excel 批量导入景区数据

**功能描述：** 读取 Excel 文件，`attraction_name` → 景区名，`attraction_content` → 景区介绍（写入知识库）。

**Content-Type：** `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | File | 是 | `.xlsx` 或 `.xls` 文件 |

**响应示例：**
```json
{
  "code": 200,
  "msg": "success",
  "data": "成功导入 3 条景区数据"
}
```

#### GET `/manage/scenic/importTemplate` — 下载导入模板

---

### 3.2 知识库管理

#### GET `/manage/knowledge/list` — 知识库文档列表（分页）

**响应字段（KnowledgeDoc）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 文档 ID |
| `scenicId` | Long | 所属景区 ID |
| `docTitle` | String | 文档标题 |
| `docType` | String | 类型：讲解词/文史资料/攻略/公告 |
| `docContent` | String | 完整内容 |
| `docSummary` | String | 自动摘要 |
| `docFile` | String | 附件地址 |
| `fileSize` | Long | 文件大小（字节） |
| `wordCount` | Integer | 字数 |
| `chunkCount` | Integer | 切片数量 |
| `vectorized` | Integer | 是否已向量化 0-否 1-是 |
| `embeddingModel` | String | Embedding 模型版本 |
| `viewCount` | Integer | 被引用次数 |
| `status` | Integer | 状态 0-禁用 1-启用 |

---

#### GET `/manage/knowledge/{id}` — 查询知识文档
#### POST `/manage/knowledge` — 新增知识文档
#### PUT `/manage/knowledge` — 修改知识文档

**请求体（KnowledgeDocRequest）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | Long | 条件 | 文档 ID，更新时必填 |
| `scenicId` | Long | 是 | 所属景区 ID |
| `docTitle` | String | 是 | 文档标题 |
| `docType` | String | 是 | 类型：讲解词/文史资料/攻略/公告 |
| `docContent` | String | 是 | 文档内容 |
| `docSummary` | String | 否 | 文档摘要 |
| `status` | Integer | 否 | 状态 0-禁用 1-启用 |

#### DELETE `/manage/knowledge/{id}` — 删除知识文档（同步清理 Redis 向量 + MySQL chunk）

#### POST `/manage/knowledge/{id}/vectorize` — 单文档向量化（RAG）

将指定文档内容切分为 chunk，调用 Embedding 模型生成向量存入 Redis。

#### POST `/manage/knowledge/vectorize/batch` — 全量批量向量化

处理所有 `vectorized=0` 的文档。

#### POST `/manage/knowledge/vectorize/batch/{scenicId}` — 按景区批量向量化

---

### 3.3 FAQ 管理

#### GET `/manage/faq/list` — FAQ 列表（分页）

**响应字段（Faq）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | FAQ ID |
| `scenicId` | Long | 所属景区 ID |
| `question` | String | 用户问题 |
| `questionKeywords` | String | 问题关键词 |
| `answer` | String | 回答内容 |
| `answerType` | String | 回答类型：text/rich/html |
| `spotId` | Long | 关联景点 ID（可选） |
| `similarQuestions` | String | 相似问法（JSON 数组） |
| `clickCount` | Integer | 被咨询次数 |
| `helpfulCount` | Integer | 点赞数 |
| `unhelpfulCount` | Integer | 踩数 |
| `vectorId` | String | 向量 ID |
| `similarityThreshold` | Double | 相似度阈值 |
| `status` | Integer | 状态 0-禁用 1-启用 |
| `createTime` | Date | 创建时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `updateTime` | Date | 更新时间，格式 `yyyy-MM-dd HH:mm:ss` |

---

#### GET `/manage/faq/{id}` — 查询单个 FAQ
#### POST `/manage/faq` — 新增 FAQ（自动触发向量化和 FAQ RAG）
#### PUT `/manage/faq` — 修改 FAQ（自动重新向量化）
#### DELETE `/manage/faq/{id}` — 删除 FAQ

**POST/PUT 请求体（Faq）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | Long | 条件 | FAQ ID，更新时必填 |
| `scenicId` | Long | 是 | 所属景区 ID |
| `question` | String | 是 | 用户问题 |
| `questionKeywords` | String | 否 | 问题关键词 |
| `answer` | String | 是 | 回答内容 |
| `answerType` | String | 否 | 回答类型：text/rich/html |
| `spotId` | Long | 否 | 关联景点 ID |
| `similarQuestions` | String | 否 | 相似问法（JSON 数组） |
| `similarityThreshold` | Double | 否 | 相似度阈值 |
| `status` | Integer | 否 | 状态 0-禁用 1-启用 |

#### GET `/manage/faq/match` — 智能匹配相似问题（RAG 检索）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |
| `question` | String | 是 | 用户问题 |

#### GET `/manage/faq/hot` — 热门问答 TOP

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |
| `limit` | Integer | 否 | 返回数量，默认 10 |

#### POST `/manage/faq/{id}/helpful` — 点赞
#### POST `/manage/faq/{id}/unhelpful` — 点踩

---

### 3.4 数字人配置管理

#### GET `/manage/digital-human/list` — 数字人列表（分页）

**响应字段（DigitalHumanConfig）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 配置 ID |
| `scenicId` | Long | 所属景区 ID |
| `humanName` | String | 数字人名称 |
| `avatarImage` | String | 人物头像 URL |
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
| `sampleAudioUrl` | String | 音色试听音频 |
| `appearanceConfig` | String | 外观扩展配置（JSON） |
| `voiceConfig` | String | 语音合成扩展参数（JSON） |
| `lipSync` | Integer | 口型同步 0-关闭 1-开启 |
| `isDefault` | Integer | 是否默认 0-否 1-是 |

---

#### GET `/manage/digital-human/{id}` — 查询数字人详情
#### POST `/manage/digital-human` — 新增数字人配置
#### PUT `/manage/digital-human` — 修改数字人配置

**请求体（DigitalHumanConfig）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | Long | 条件 | 配置 ID，更新时必填 |
| `scenicId` | Long | 是 | 所属景区 ID |
| `humanName` | String | 是 | 数字人名称 |
| `avatarImage` | String | 否 | 人物头像 URL |
| `defaultGreeting` | String | 否 | 默认问候语 |
| `modelJsonUrl` | String | 是 | Live2D 模型 .model3.json 地址（前端加载模型必须） |
| `landscapeHeight` | BigDecimal | 否 | 横屏模型显示高度 |
| `portraitHeight` | BigDecimal | 否 | 竖屏模型显示高度 |
| `scaleX` | BigDecimal | 否 | X 方向缩放 |
| `offsetX` | BigDecimal | 否 | X 方向偏移 |
| `offsetY` | BigDecimal | 否 | Y 方向偏移 |
| `idleMotionGroup` | String | 否 | 空闲动作组名 |
| `tapMotionGroup` | String | 否 | 点击动作组名 |
| `ttsVoiceCode` | String | 否 | TTS 音色代码 |
| `sampleAudioUrl` | String | 否 | 音色试听音频地址 |
| `appearanceConfig` | String | 否 | 外观扩展配置（JSON） |
| `voiceConfig` | String | 否 | 语音合成扩展参数（JSON） |
| `lipSync` | Integer | 否 | 口型同步 0-关闭 1-开启 |
| `isDefault` | Integer | 否 | 是否默认 0-否 1-是 |

#### DELETE `/manage/digital-human/{id}` — 删除数字人配置
#### GET `/manage/digital-human/scenic/{scenicId}/default` — 获取景区默认数字人
#### POST `/manage/digital-human/{id}/set-default` — 设置默认数字人

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID（Query） |

---

### 3.5 数据大屏统计

#### GET `/manage/stats/today-overview` — 今日实时概览

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |

**响应：** 包含今日游客交互次数、AI 回答数、FAQ 咨询数等核心指标。

#### GET `/manage/stats/weekly-stats` — 本周运营数据统计

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |

#### GET `/manage/stats/hot-questions` — 热门问答 TOP（N）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |
| `limit` | Integer | 否 | 默认 10 |

#### POST `/manage/stats/generate` — 手动触发统计任务

> 注意：参数通过 **Query 参数**传递，不是 Request Body。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID（Query） |
| `date` | String | 是 | 日期，格式 `yyyy-MM-dd`（Query） |
| `type` | String | 否 | 统计类型，默认 `daily`（Query） |

---

### 3.6 游客感受度分析

#### GET `/manage/analysis/emotion-trend` — 情感趋势数据

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |
| `startDate` | String | 是 | 开始日期 `yyyy-MM-dd` |
| `endDate` | String | 是 | 结束日期 `yyyy-MM-dd` |

#### GET `/manage/analysis/focus-points` — 游客关注点 TOP（N）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |
| `limit` | Integer | 否 | 默认 10 |

#### GET `/manage/analysis/satisfaction-trend` — 满意度趋势

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |
| `days` | Integer | 否 | 统计天数，默认 30 |

#### POST `/manage/analysis/generate-daily-report` — AI 生成日报分析

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |
| `date` | LocalDate | 是 | 日期，格式 `yyyy-MM-dd`，使用 `@DateTimeFormat` 注解解析 |

#### GET `/manage/analysis/{id}` — 查看历史分析报告

---

## 四、RuoYi-Vue 后台管理接口

**认证说明：** 所有 `/system/*` 接口需携带有效 JWT Token。

### 4.1 认证登录（Auth）

#### GET `/captchaImage` — 获取验证码

**权限：** 无需认证（匿名接口）

**响应：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `captchaEnabled` | Boolean | 验证码开关 |
| `uuid` | String | 验证码唯一标识（后续登录需携带） |
| `img` | String | Base64 编码的验证码图片 |

> 当 `captchaEnabled=false` 时，仅返回 `captchaEnabled`，不返回 `uuid` 和 `img`。

---

#### POST `/login` — 用户登录

**权限：** 无需认证（匿名接口）

**请求参数（LoginBody）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | String | 是 | 用户名 |
| `password` | String | 是 | 密码（登录后服务端验证） |
| `code` | String | 条件 | 验证码（`captchaEnabled=true` 时必填） |
| `uuid` | String | 条件 | 验证码 UUID（`captchaEnabled=true` 时必填） |

**请求示例：**
```json
{
  "username": "admin",
  "password": "admin123",
  "code": "a1b2",
  "uuid": "3d2f1a0e-b3c4-..."
}
```

**响应示例：**
```json
{
  "code": 200,
  "msg": "success",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

> 后续请求需在 Header 中携带：`Authorization: Bearer <token>`

---

#### POST `/logout` — 退出登录

**权限：** 需要认证

**响应示例：**
```json
{
  "code": 200,
  "msg": "退出成功"
}
```

---

#### GET `/getInfo` — 获取当前用户信息

**权限：** 需要认证

**响应字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `user` | SysUser | 用户信息 |
| `roles` | Set\<String\> | 角色标识集合，如 `["admin","scenic_admin"]` |
| `permissions` | Set\<String\> | 权限标识集合，如 `["system:user:list"]` |
| `isDefaultModifyPwd` | Boolean | 是否提示修改初始密码 |
| `isPasswordExpired` | Boolean | 密码是否过期 |

---

#### GET `/getRouters` — 获取用户路由菜单

**权限：** 需要认证

**响应：** 前端路由树结构，用于动态菜单渲染。

---

### 4.2 角色管理（Role）

**基础路径：** `/system/role`

**所需权限：** `system:role:*`

#### GET `/system/role/list` — 角色列表（分页）

**权限：** `system:role:list`

#### GET `/system/role/{roleId}` — 查询角色详情

**权限：** `system:role:query`

#### POST `/system/role` — 新增角色

**权限：** `system:role:add`

#### PUT `/system/role` — 修改角色

**权限：** `system:role:edit`

#### DELETE `/system/role/{roleIds}` — 删除角色（批量）

**权限：** `system:role:remove`

#### PUT `/system/role/dataScope` — 修改角色数据权限

**权限：** `system:role:edit`

#### PUT `/system/role/changeStatus` — 修改角色状态

**权限：** `system:role:edit`

**请求体：**
```json
{
  "roleId": 1,
  "status": "0"
}
```

#### GET `/system/role/optionselect` — 角色下拉选择列表

**权限：** `system:role:query`

#### GET `/system/role/deptTree/{roleId}` — 角色部门树

**权限：** `system:role:query`

#### GET `/system/role/authUser/allocatedList` — 已分配用户列表

**权限：** `system:role:list`

#### GET `/system/role/authUser/unallocatedList` — 未分配用户列表

**权限：** `system:role:list`

#### PUT `/system/role/authUser/cancel` — 取消用户授权

**权限：** `system:role:edit`

#### PUT `/system/role/authUser/cancelAll` — 批量取消授权

**权限：** `system:role:edit`

#### PUT `/system/role/authUser/selectAll` — 批量授权用户

**权限：** `system:role:edit`

---

### 4.3 菜单管理（Menu）

**基础路径：** `/system/menu`

#### GET `/system/menu/list` — 菜单列表

**权限：** `system:menu:list`

#### GET `/system/menu/{menuId}` — 查询菜单详情

**权限：** `system:menu:query`

#### GET `/system/menu/treeselect` — 菜单下拉树

#### GET `/system/menu/roleMenuTreeselect/{roleId}` — 角色菜单树（用于角色授权）

#### POST `/system/menu` — 新增菜单

**权限：** `system:menu:add`

#### PUT `/system/menu` — 修改菜单

**权限：** `system:menu:edit`

#### PUT `/system/menu/updateSort` — 保存菜单排序

**权限：** `system:menu:edit`

**请求体：**
```json
{
  "menuIds": "1,2,3",
  "orderNums": "1,2,3"
}
```

#### DELETE `/system/menu/{menuId}` — 删除菜单

**权限：** `system:menu:remove`

---

### 4.4 用户管理（User）

**基础路径：** `/system/user`

#### GET `/system/user/list` — 用户列表（分页）

**权限：** `system:user:list`

#### GET `/system/user/{userId}` — 查询用户详情

**权限：** `system:user:query`

#### POST `/system/user` — 新增用户

**权限：** `system:user:add`

#### PUT `/system/user` — 修改用户

**权限：** `system:user:edit`

#### DELETE `/system/user/{userIds}` — 删除用户

**权限：** `system:user:remove`

#### PUT `/system/user/resetPwd` — 重置用户密码

**权限：** `system:user:edit`

#### PUT `/system/user/changeStatus` — 修改用户状态

**权限：** `system:user:edit`

#### POST `/system/user/importData` — 导入用户数据

**权限：** `system:user:import`

#### POST `/system/user/export` — 导出用户数据

**权限：** `system:user:export`

#### GET `/system/user/authRole/{userId}` — 授权角色回显

#### PUT `/system/user/authRole` — 授权角色

---

### 4.5 个人信息（Profile）

**基础路径：** `/system/user/profile`

#### GET `/system/user/profile` — 个人信息

**权限：** 已登录用户

**响应：** 当前登录用户信息 + 角色组 + 岗位组

#### PUT `/system/user/profile` — 修改个人信息

**权限：** 已登录用户

**请求体（部分字段）：**

| 字段 | 说明 |
|------|------|
| `nickName` | 昵称 |
| `email` | 邮箱 |
| `phonenumber` | 手机号 |
| `sex` | 性别 |

#### PUT `/system/user/profile/updatePwd` — 重置个人密码

**权限：** 已登录用户

**请求体：**
```json
{
  "oldPassword": "旧密码",
  "newPassword": "新密码"
}
```

#### POST `/system/user/profile/avatar` — 上传头像

**权限：** 已登录用户

**Content-Type：** `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `avatarfile` | File | 是 | 头像图片文件 |

---

## 五、权限认证机制详解

### 5.1 RuoYi-Vue 认证流程

```
┌─────────┐         ┌──────────────┐         ┌────────────────────┐
│  前端   │  1.POST  │  /captchaImage│        │  CaptchaController  │
│         │ ───────►│              │────────►│  生成验证码写入Redis │
└─────────┘         └──────────────┘         └────────────────────┘
                                                                      │
                                                                      ▼
┌─────────┐         ┌──────────────┐         ┌────────────────────┐
│  前端   │  2.POST  │   /login     │        │  SysLoginController │
│         │ ───────►│              │────────►│  调用 SysLoginService│
└─────────┘         └──────────────┘         └────────────────────┘
                                                                      │
                                              ┌──────────────────────┘
                                              ▼
                    ┌──────────────────────────────────────────┐
                    │  SysLoginService.login()                 │
                    │  1. validateCaptcha() — Redis 验证验证码   │
                    │  2. loginPreCheck()  — 前置校验          │
                    │  3. AuthenticationManager.authenticate()  │
                    │     → 调用 UserDetailsServiceImpl         │
                    │     → loadUserByUsername() 查询数据库    │
                    │     → 密码 BCryptPasswordEncoder 比对     │
                    │  4. recordLoginInfo()  — 记录登录信息     │
                    │  5. tokenService.createToken() — 生成JWT │
                    └──────────────────────────────────────────┘
                                                                      │
                                                                      ▼
┌─────────┐         ┌──────────────┐         ┌────────────────────┐
│  前端   │  返回    │   token      │        │  JwtAuthenticationTokenFilter│
│         │◄────────│              │         │  (每次请求执行)           │
└─────────┘         └──────────────┘         └────────────────────┘
                                                                      │
        ┌───────────────────────────────────────────────────────────────┘
        ▼
┌─────────────────────────────────────────────────────────┐
│  JwtAuthenticationTokenFilter.doFilterInternal()       │
│  1. 从 Header:Authorization 提取 Bearer Token         │
│  2. tokenService.getLoginUser(request) — 解密获取     │
│     LoginUser（含 userId/deptId/permissions/user）   │
│  3. tokenService.verifyToken() — 验证过期/黑名单     │
│  4. UsernamePasswordAuthenticationToken              │
│  5. SecurityContextHolder.setAuthentication()         │
└─────────────────────────────────────────────────────────┘
```

### 5.2 权限注解使用方式

项目启用 `@EnableMethodSecurity`，支持以下注解：

#### `@PreAuthorize` — 方法级权限校验

```java
// 方式一：权限校验（用户需持有该权限标识）
@PreAuthorize("@ss.hasPermi('system:user:list')")

// 方式二：角色校验（用户需属于该角色）
@PreAuthorize("@ss.hasRole('admin')")

// 方式三：角色 OR 关系
@PreAuthorize("@ss.hasRole('admin') or @ss.hasRole('scenic_admin')")

// 方式四：角色 AND 关系
@PreAuthorize("@ss.hasRole('admin') and @ss.hasRole('scenic_admin')")
```

#### manage 模块统一鉴权（类级别）

```java
@PreAuthorize("@ss.hasRole('admin') or @ss.hasRole('scenic_admin')")
@RestController
@RequestMapping("/manage/knowledge")
public class KnowledgeDocController { ... }
```

#### admin 模块权限树（以 SysMenuController 为例）

| 权限标识 | 接口 | 说明 |
|----------|------|------|
| `system:menu:list` | GET `/menu/list` | 查看菜单列表 |
| `system:menu:query` | GET `/menu/{id}` | 查看菜单详情 |
| `system:menu:add` | POST `/menu` | 新增菜单 |
| `system:menu:edit` | PUT `/menu` + `/updateSort` + `/changeStatus` | 修改菜单 |
| `system:menu:remove` | DELETE `/menu/{id}` | 删除菜单 |

### 5.3 前端请求规范

**登录后所有请求 Header 必须包含：**

```
Authorization: Bearer <token>
```

**token 获取方式：** 登录成功后从响应 `data.token` 字段提取。

**请求示例（curl）：**
```bash
curl -X GET "http://localhost:8080/system/user/list" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### 5.4 权限核心类

| 类 | 位置 | 作用 |
|----|------|------|
| `JwtAuthenticationTokenFilter` | jingbanyou-framework | 每次请求解析 JWT Token，写入 SecurityContext |
| `SysLoginService` | jingbanyou-framework | 登录核心逻辑（验证码/密码校验/Token生成） |
| `TokenService` | jingbanyou-framework | Token 创建、刷新、验证、缓存管理 |
| `SysPermissionService` | jingbanyou-framework | 加载用户角色和菜单权限 |
| `LoginUser` | jingbanyou-common | 登录用户身份模型（implements UserDetails） |
| `SecurityConfig` | jingbanyou-framework | Spring Security 配置（CSRF/CORS/FilterChain） |
| `LogoutSuccessHandlerImpl` | jingbanyou-framework | 退出登录处理（删除Redis缓存/记录日志） |
| `AuthenticationEntryPointImpl` | jingbanyou-framework | 未认证请求统一响应 |

---

## 六、公共响应码说明

| code | 说明 |
|------|------|
| `200` | 操作成功 |
| `401` | 未认证（Token 失效或未提供） |
| `403` | 无权限（权限/角色校验不通过） |
| `500` | 服务器内部错误 |

---

## 七、公共请求头

| Header | 说明 |
|--------|------|
| `Authorization` | `Bearer <token>`，登录后所有请求必需 |
| `Content-Type` | `application/json`，POST/PUT 请求必需 |
| `Content-Type` | `multipart/form-data`，文件上传必需 |
