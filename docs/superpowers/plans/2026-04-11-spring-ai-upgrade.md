# Spring AI 版本升级计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Spring AI 从 1.0.0 升级到 1.1.2，以使用官方内置的 `RedisChatMemoryRepository` 替代自定义实现，消除冗余代码。

**Architecture:** 通过 Spring AI Alibaba BOM 统一管理版本，确保 Spring AI、Spring AI Alibaba Extensions、Spring AI Alibaba 所有组件版本严格对齐（均为 1.1.2）。删除自定义 RedisChatMemory，使用官方 ChatMemoryRepository + MessageWindowChatMemory。

**Tech Stack:** Spring Boot 3.5.0, Spring AI 1.1.2, Spring AI Alibaba 1.1.2.2, Spring AI Alibaba Extensions 1.1.2.1, Redis Stack 6380

---

## 升级前备份

> **Step 0: 备份当前 pom.xml 和关键代码文件**

- [ ] **Step 0.1: 备份根 pom.xml**

```bash
cp pom.xml pom.xml.bak
cp jingbanyou-tourist/pom.xml jingbanyou-tourist/pom.xml.bak
cp jingbanyou-common/pom.xml jingbanyou-common/pom.xml.bak
```

---

## 文件影响地图

| 文件 | 操作 | 原因 |
|------|------|------|
| `pom.xml` | 修改 | 升级版本属性 + 添加 BOM |
| `jingbanyou-common/pom.xml` | 修改 | 升级 spring-ai-redis-store 版本（移除硬编码） |
| `jingbanyou-tourist/pom.xml` | 修改 | 添加 BOM + Redis ChatMemory Repository 依赖 |
| `jingbanyou-tourist/.../RedisChatMemory.java` | 删除 | 官方已有替代 |
| `jingbanyou-tourist/.../ChatMemoryConfig.java` | 重写 | 利用自动配置，仅保留 ChatMemory + Advisor Bean |
| `jingbanyou-tourist/.../ChatMemoryService.java` | 修改 | 适配官方 Repository API |
| `jingbanyou-admin/src/main/resources/application.yml` | 修改 | 添加官方 Redis ChatMemory 配置 |
| `jingbanyou-tourist/.../FagTool.java` | 删除 | 空类，上次确认删除 |

---

## Task 1: 修改根 pom.xml — 添加 BOM + 升级版本属性

**文件:** `pom.xml`

- [ ] **Step 1.1: 修改 properties（将 spring-ai 版本属性改为 1.1.2）**

找到：
```xml
<spring-ai-alibaba.version>1.0.0.2</spring-ai-alibaba.version>
<spring-ai.version>1.0.0</spring-ai.version>
```

替换为：
```xml
<spring-ai-alibaba.version>1.1.2.2</spring-ai-alibaba.version>
<spring-ai.version>1.1.2</spring-ai.version>
<spring-ai-alibaba-extensions.version>1.1.2.1</spring-ai-alibaba-extensions.version>
```

- [ ] **Step 1.2: 在 dependencyManagement 中添加 Spring AI Alibaba BOM（在 spring-boot-dependencies 之后）**

在 `</dependencyManagement>` 之前、`<!-- Spring AI Alibaba DashScope -->` 之前添加：

```xml
<!-- Spring AI Alibaba BOM（统一管理所有 Spring AI Alibaba 相关依赖版本） -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-bom</artifactId>
    <version>${spring-ai-alibaba.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- Spring AI BOM（统一管理所有 Spring AI 相关依赖版本） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>${spring-ai.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

- [ ] **Step 1.3: 移除旧的手动版本声明依赖（因为现在由 BOM 统一管理）**

删除 `dependencyManagement` 中的以下 3 个手动声明（已由 BOM 管理）：
- `spring-ai-alibaba-starter-dashscope`
- `spring-ai-alibaba-agent-framework`
- `spring-ai-alibaba-graph-core`
- `spring-ai-starter-model-chat-memory-repository-jdbc`

保留仅在 `dependencyManagement` 中、且不在 BOM 范围内的依赖（如 druid、mybatis-plus 等）。

- [ ] **Step 1.4: 验证 Maven 能解析所有依赖**

Run: `mvn dependency:tree -pl jingbanyou-tourist -DoutputType=text -DoutputFile=/tmp/deps.txt 2>&1 | head -100`
Expected: 无红色错误，输出依赖树

---

## Task 2: 修改 jingbanyou-common/pom.xml — 升级 spring-ai-redis-store 版本

**文件:** `jingbanyou-common/pom.xml:133-136`

- [ ] **Step 2.1: 移除硬编码版本号（由 BOM 管理）**

找到：
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-redis-store</artifactId>
    <version>1.0.0</version>
</dependency>
```

替换为：
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-redis-store</artifactId>
</dependency>
```

- [ ] **Step 2.2: 添加 spring-ai-bom 引用（让 BOM 生效到 common 模块）**

在 `<parent>` 之后、`<dependencies>` 之前添加：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## Task 3: 修改 jingbanyou-tourist/pom.xml — 添加 Redis ChatMemory Repository 依赖

**文件:** `jingbanyou-tourist/pom.xml`

- [ ] **Step 3.1: 添加 spring-ai-bom 引用**

在 `<parent>` 之后、`<dependencies>` 之前添加：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

- [ ] **Step 3.2: 将 spring-ai-starter-model-chat-memory-repository-jdbc 替换为 redis 版本**

找到：
```xml
<!-- Spring AI JDBC ChatMemory（冷存储，MySQL 持久化） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
</dependency>
```

替换为：
```xml
<!-- Spring AI Redis ChatMemory Repository（热缓存，Redis Stack 6380） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-redis</artifactId>
</dependency>

<!-- Spring AI JDBC ChatMemory Repository（冷存储，MySQL 持久化） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
</dependency>
```

- [ ] **Step 3.3: 移除重复的 spring-ai-alibaba-graph-core 依赖**

找到并删除（与 parent pom 中的声明重复）：
```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-graph-core</artifactId>
</dependency>
```

- [ ] **Step 3.4: 验证依赖树中 Redis ChatMemory Repository 存在**

Run: `mvn dependency:tree -pl jingbanyou-tourist 2>&1 | grep -E "chat-memory|redis-store"`
Expected: 应显示 `spring-ai-starter-model-chat-memory-repository-redis` 和 `spring-ai-redis-store`

---

## Task 4: 简化 ChatMemoryConfig — 利用官方自动配置（仅保留自定义 Bean）

**文件:** `jingbanyou-tourist/src/main/java/.../config/ChatMemoryConfig.java`

> **重要发现：** Spring AI 1.1.x 的 `spring-ai-autoconfigure-model-chat-memory-redis` 已包含自动配置（连接 Redis、使用 JedisPooled）。只需在 YAML 配置连接参数，Spring 会自动创建 `RedisChatMemoryRepository` 和 `ChatMemory` Bean。ChatMemoryConfig 只需注入并暴露给其他 Bean 使用。

- [ ] **Step 4.1: 重写 ChatMemoryConfig，利用自动配置，仅显式声明需要定制的 Bean**

将整个文件内容替换为：

```java
package cn.edu.gdou.jingbanyou.tourist.config;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆配置（基于官方 RedisChatMemoryRepository 自动配置 + Redis Stack）
 *
 * <p>spring-ai-autoconfigure-model-chat-memory-redis 已自动创建：
 *   - RedisChatMemoryRepository（使用 JedisPooled 连接 Redis Stack 6380）
 *   - ChatMemory（MessageWindowChatMemory，默认 maxMessages=20）
 * <p>本配置类用于：
 *   1. 复用自动配置的 RedisChatMemoryRepository
 *   2. 提供 ChatMemory Bean（限制上下文窗口大小）
 *   3. 提供 MessageChatMemoryAdvisor（自动管理对话历史）
 * <p>Redis 连接参数在 application.yml 中配置（见 Task 6）
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * MessageWindowChatMemory：限制上下文窗口大小（默认保留最近 20 条消息）
     * Spring Boot 自动配置已创建此 Bean，此处重新声明以覆盖 maxMessages 配置
     */
    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    /**
     * MessageChatMemoryAdvisor：自动管理对话历史的 Advisor
     * 调用时通过 .advisors(ctx -> ctx.param(ChatMemory.CONVERSATION_ID, sessionId)) 指定会话
     */
    @Bean
    public MessageChatMemoryAdvisor chatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}
```

> **说明：** 不再需要手动创建 `RedisConnectionFactory` 和 `StringRedisTemplate`，因为 `spring-ai-starter-model-chat-memory-repository-redis` 内部已自动配置 `JedisPooled` 连接到 Redis Stack。

---

## Task 5: 修改 ChatMemoryService — 适配官方 API

**文件:** `jingbanyou-tourist/src/main/java/.../service/ChatMemoryService.java`

- [ ] **Step 5.1: 替换 import**

找到：
```java
import cn.edu.gdou.jingbanyou.tourist.config.RedisChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
```

替换为：
```java
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
```

- [ ] **Step 5.2: 替换注入的字段**

找到：
```java
private final RedisChatMemory redisChatMemory;
private final VisitorInteractionMapper visitorInteractionMapper;
```

替换为：
```java
private final RedisChatMemoryRepository chatMemoryRepository;
private final VisitorInteractionMapper visitorInteractionMapper;
```

- [ ] **Step 5.3: 替换 syncToMySQL 方法体中的调用**

找到：
```java
List<Message> msgs = redisChatMemory.get(sessionId, -1);
if (msgs == null || msgs.isEmpty()) return;

for (int i = 0; i + 1 < msgs.size(); i += 2) {
    if (msgs.get(i).getText() != null) {
        VisitorInteraction record = new VisitorInteraction();
        record.setSessionId(sessionId);
        record.setScenicId(scenicId);
        record.setVisitorId(visitorId);
        record.setUserQuestion(msgs.get(i).getText());
        record.setAiAnswer(msgs.get(i + 1).getText());
        record.setInteractionType("text");
        visitorInteractionMapper.insert(record);
    }
}
// 清除 Redis 缓存
redisChatMemory.clear(sessionId);
```

替换为：
```java
// 从 Redis 读取全部历史消息
List<Message> msgs = chatMemoryRepository.findByConversationId(sessionId);
if (msgs == null || msgs.isEmpty()) return;

// 每 user+assistant 配对 → 一条 VisitorInteraction
for (int i = 0; i + 1 < msgs.size(); i += 2) {
    if (msgs.get(i).getText() != null) {
        VisitorInteraction record = new VisitorInteraction();
        record.setSessionId(sessionId);
        record.setScenicId(scenicId);
        record.setVisitorId(visitorId);
        record.setUserQuestion(msgs.get(i).getText());
        record.setAiAnswer(msgs.get(i + 1).getText());
        record.setInteractionType("text");
        visitorInteractionMapper.insert(record);
    }
}
// 清除 Redis 缓存
chatMemoryRepository.deleteByConversationId(sessionId);
```

> **注意：** `findByConversationId` 和 `deleteByConversationId` 方法签名已通过 Spring AI 1.1.x API 搜索确认（与 Cassandra/CosmosDB 版本一致）。

---

## Task 6: 修改 application.yml — 添加官方 Redis ChatMemory 配置

**文件:** `jingbanyou-admin/src/main/resources/application.yml`

- [ ] **Step 6.1: 在 spring.ai 节点下添加 Redis ChatMemory Repository 配置**

找到 `spring.ai` 配置块，在 `mcp` 配置之后添加：

```yaml
    # Spring AI 官方 Redis ChatMemory Repository 配置
    # JedisPooled 自动配置，连接 Redis Stack 6380
    chat:
      memory:
        repository:
          redis:
            host: localhost
            port: 6380
            key-prefix: "chat:memory:"
            time-to-live: "24h"
```

完整 `spring.ai` 配置块应为：
```yaml
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:sk-25a0e981c714438e85fe067faaf64295}
      chat:
        options:
          model: qwen-turbo
          temperature: 0.7
          top-p: 0.9
      embedding:
        enabled: true
        options:
          model: text-embedding-v2
      mcp:
        client:
          stdio:
            servers-configuration: classpath:/mcp-servers-config.json
          toolcallback:
            enabled: true
    # Spring AI 官方 Redis ChatMemory Repository 配置
    chat:
      memory:
        repository:
          redis:
            host: localhost
            port: 6380
            key-prefix: "chat:memory:"
            time-to-live: "24h"
```

- [ ] **Step 6.2: 删除旧注释**

删除：
```yaml
    # Spring AI Chat Memory（RedisChatMemoryRepository 使用 JedisPooled 直连 Redis Stack 6380）
    # ChatMemoryConfig 中已手动配置连接，无需在此处配置
```

---

## Task 7: 删除废弃文件

**文件:**
- `jingbanyou-tourist/src/main/java/.../tool/FagTool.java`
- `jingbanyou-tourist/src/main/java/.../config/RedisChatMemory.java`

- [ ] **Step 7.1: 删除 FagTool.java**

```bash
rm jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/tool/FagTool.java
```

- [ ] **Step 7.2: 删除自定义 RedisChatMemory.java**

```bash
rm jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/config/RedisChatMemory.java
```

---

## Task 8: 验证编译

- [ ] **Step 8.1: Maven 编译（跳过测试）**

Run: `mvn clean compile -DskipTests 2>&1`
Expected: BUILD SUCCESS（无红色错误）

- [ ] **Step 8.2: 如果编译失败，逐个修复**

常见失败原因：
1. **API 不存在**：`RedisChatMemoryRepository` 方法名可能与预期不同 → 查看实际 JAR 中的接口
2. **BOM 冲突**：某些依赖被多个 BOM 管理 → 使用 `<exclusion>` 排除
3. **ChatMemory 接口变化**：检查 `MessageChatMemoryAdvisor` 是否仍接受 `ChatMemory` 参数

Run: `mvn clean compile -pl jingbanyou-tourist -am -DskipTests 2>&1 | tail -50`

---

## Task 9: 启动验证

- [ ] **Step 9.1: 启动应用，观察日志**

Run: 启动 `jingbanyou-admin` 模块
Expected: 控制台无红色异常，Redis 连接成功

- [ ] **Step 9.2: 验证对话功能**

1. 发起一次对话请求
2. 检查 Redis 6380 中是否生成了 `chat:memory:` 前缀的 key
3. 调用 `/tourist/chat/end` 验证异步同步到 MySQL

---

## 降级回滚方案

如果升级失败，执行以下步骤回滚：

1. 恢复备份的 pom.xml 文件
2. 恢复 RedisChatMemory.java 和 FagTool.java（如果有备份）
3. 恢复 application.yml 中的配置

```bash
# 回滚
cp pom.xml.bak pom.xml
cp jingbanyou-tourist/pom.xml.bak jingbanyou-tourist/pom.xml
cp jingbanyou-common/pom.xml.bak jingbanyou-common/pom.xml
```

---

## 版本依赖对照表

| 组件 | 升级前 | 升级后 | 备注 |
|------|--------|--------|------|
| Spring Boot | 3.5.0 | 3.5.0 | 不变 |
| Spring AI | 1.0.0 | 1.1.2 | 主要升级 |
| Spring AI Alibaba | 1.0.0.2 | 1.1.2.2 | 主要升级 |
| spring-ai-redis-store | 1.0.0 | 1.1.2 | 由 BOM 管理 |
| spring-ai-redis-chat-memory | 无 | 1.1.2 | 新增 |
| spring-ai-alibaba-graph-core | 1.0.0.2 | 1.1.2.2 | 由 BOM 管理 |

## 风险清单

| 风险 | 级别 | 应对 |
|------|------|------|
| Spring AI Alibaba 1.1.2.2 与其他组件不兼容 | 中 | 使用 BOM 统一版本 |
| `spring-ai-redis-store` 版本与 `spring-ai-alibaba` 冲突 | 中 | BOM 解决版本传递 |
| Graph API 变化 | 低 | Alibaba Graph Core 1.1.2.2 兼容 |
| `spring-ai-starter-model-chat-memory-repository-redis` 内部使用 JedisPooled，与 Spring Data Redis（6379）端口冲突 | 中 | 使用独立 host/port 配置（6380），与 ProfileVectorStoreConfig 保持一致 |
