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

**基础路径：** `/tourist`

**认证说明：** 游客端接口无需登录，属于公开接口。

---

### 2.1 游客对话

#### POST `/tourist/chat/text` — 文字对话

**功能描述：** 游客发送文字消息，AI 导游基于 LangGraph 图执行 RAG 检索并返回回答。

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `text` | String | 是 | 游客输入的文字 |
| `sessionId` | String | 是 | 会话 ID，用于多轮对话上下文 |
| `scenicId` | Long | 否 | 景区 ID |

**请求示例：**
```json
{
  "text": "这个景点的开放时间是几点？",
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
    "answer": "该景点开放时间为 08:00-18:00...",
    "intent": "knowledge_query"
  }
}
```

**intent 返回值说明：**

| intent | 含义 |
|--------|------|
| `knowledge_query` | 景点知识查询 |
| `faq_query` | FAQ 问答匹配 |
| `route_plan` | 路线规划请求 |
| `general_chat` | 闲聊兜底 |

---

#### POST `/tourist/chat/audio` — 语音/多模态对话

**功能描述：** 支持音频文件（Voice）输入的对话接口，音频经 ASR 转文字后走与文字对话相同的 Graph 流程。

**请求参数（`multipart/form-data`）：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `audio` | File | 否 | 语音文件 |
| `text` | String | 否 | 文字（优先级高于音频，两者都有时以文字为准） |
| `sessionId` | String | 是 | 会话 ID |
| `language` | String | 否 | 语言，默认 `zh`（支持 `en`） |
| `scenicId` | Long | 否 | 景区 ID |

**响应示例：**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "answer": "欢迎来到我们的景区...",
    "intent": "general_chat"
  }
}
```

---

#### POST `/tourist/chat/end` — 结束对话

**功能描述：** 前端页面离开时调用，将 Redis 中的对话历史异步批量持久化到 MySQL `VisitorInteraction` 表。

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `sessionId` | String | 是 | 会话 ID |
| `scenicId` | Long | 否 | 景区 ID |
| `visitorId` | String | 否 | 游客标识 |

**响应示例：**
```json
{
  "code": 200,
  "msg": "对话已结束"
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

**响应字段（ScenicArea）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 景区 ID |
| `scenicName` | String | 景区名称 |
| `scenicAddress` | String | 景区地址 |
| `scenicDesc` | String | 景区介绍 |
| `openTime` | String | 开放时间 |
| `ticketInfo` | String | 门票信息 |
| `contactPhone` | String | 联系电话 |
| `officialWebsite` | String | 官网 |
| `coverImage` | String | 封面图 URL |
| `topFeatures` | List\<String\> | 功能亮点文案 |
| `quickPrompts` | List\<String\> | 快捷提问文案 |
| `starLevel` | String | 等级，如 5A |
| `status` | Integer | 状态 0-禁用 1-启用 |
| `sort` | Integer | 排序权重 |
| `createTime` | Date | 创建时间 |
| `updateTime` | Date | 更新时间 |

---

#### GET `/manage/scenic/{id}` — 查询单个景区

#### POST `/manage/scenic` — 新增景区

**请求体：** `ScenicArea` 实体字段（id 不传，由数据库自增）

#### PUT `/manage/scenic` — 修改景区

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

---

#### GET `/manage/faq/{id}` — 查询单个 FAQ
#### POST `/manage/faq` — 新增 FAQ（自动触发向量化和 FAQ RAG）
#### PUT `/manage/faq` — 修改 FAQ（自动重新向量化）
#### DELETE `/manage/faq/{id}` — 删除 FAQ

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
#### DELETE `/manage/digital-human/{id}` — 删除数字人配置
#### GET `/manage/digital-human/scenic/{scenicId}/default` — 获取景区默认数字人
#### POST `/manage/digital-human/{id}/set-default` — 设置默认数字人

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |

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

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scenicId` | Long | 是 | 景区 ID |
| `date` | String | 是 | 日期，格式 `yyyy-MM-dd` |
| `type` | String | 否 | 统计类型，默认 `daily` |

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
| `date` | LocalDate | 是 | 日期，格式 `yyyy-MM-dd` |

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
