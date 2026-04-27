# jingbanyou-ai-tour-guide 项目完善修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复项目中 manage 和 tourist 模块的编译错误、架构规范问题、代码质量问题。

**Architecture:** 按优先级分阶段修复：P0 编译错误 → P1 架构规范（添加Service接口）→ P2 代码质量 → P3 功能补全。每阶段完成后验证编译。

**Tech Stack:** Spring Boot, MyBatis-Plus, Spring AI, Redis, Maven

---

## 阶段一：P0 — 编译错误修复

### Task 1: 验证当前编译状态

**Files:**
- Verify: `jingbanyou-tourist/pom.xml`
- Verify: `jingbanyou-manage/pom.xml`
- Verify: `jingbanyou-admin/pom.xml`

- [ ] **Step 1: 尝试 Maven 编译**

```bash
cd C:/Users/c1342/IdeaProjects/20260402154946/jingbanyou-ai-tour-guide
JAVA_HOME="C:/Program Files/Java" mvn compile -pl jingbanyou-tourist,jingbanyou-manage -am -q
```

预期：如有错误，收集错误信息后进入修复步骤。

- [ ] **Step 2: 如有 TouristController 中 RagPrecheckResult 导入错误**

检查 `TouristController.java` 第13行是否导入了不存在的 `RagPrecheckResult`。如存在，删除该导入语句（该类已未被使用）。

- [ ] **Step 3: 验证 fastMatch 方法存在**

确认 `IRagPrecheckService` 接口中定义了 `Optional<String> fastMatch(String question, Long scenicId)` 方法，且 `RagPrecheckServiceImpl` 实现了该方法。

- [ ] **Step 4: 重新编译验证**

```bash
JAVA_HOME="C:/Program Files/Java" mvn compile -pl jingbanyou-tourist,jingbanyou-manage -am
```

预期：BUILD SUCCESS

---

## 阶段二：P1 — 架构规范（添加 Service 接口）

### Task 2: 为 ChatMemoryService 添加接口

**Files:**
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/service/IChatMemoryService.java`
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/service/ChatMemoryService.java:1` (添加 `implements IChatMemoryService`)
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/controller/TouristController.java:55` (注入接口而非实现)
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/service/impl/RagPrecheckServiceImpl.java` (如内部注入了 ChatMemoryService，需改为注入接口)

- [ ] **Step 1: 创建 IChatMemoryService 接口**

```java
package cn.edu.gdou.jingbanyou.tourist.service;

/**
 * 对话记忆服务接口
 *
 * @author jingbanyou
 */
public interface IChatMemoryService {

    /**
     * 对话结束时，异步全量同步到 MySQL
     *
     * @param sessionId 会话ID
     * @param scenicId 景区ID
     * @param visitorId 游客ID
     */
    void syncToMySQL(String sessionId, Long scenicId, String visitorId);
}
```

- [ ] **Step 2: ChatMemoryService 实现接口**

在 `ChatMemoryService.java` 类声明改为：
```java
public class ChatMemoryService implements IChatMemoryService {
```

- [ ] **Step 3: TouristController 注入改为接口**

将 `private final ChatMemoryService chatMemoryService;` 改为：
```java
private final IChatMemoryService chatMemoryService;
```

- [ ] **Step 4: 编译验证**

```bash
JAVA_HOME="C:/Program Files/Java" mvn compile -pl jingbanyou-tourist -am -q
```

---

### Task 3: 为 TranscribeService 添加接口

**Files:**
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/service/ITranscribeService.java`
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/service/TranscribeService.java:1`
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/controller/TouristController.java:54`

- [ ] **Step 1: 创建 ITranscribeService 接口**

```java
package cn.edu.gdou.jingbanyou.tourist.service;

/**
 * 语音转文字服务接口
 *
 * @author jingbanyou
 */
public interface ITranscribeService {

    /**
     * 将音频转换为文字
     *
     * @param audioData 音频字节数据
     * @param fileName 文件名（用于推断格式）
     * @param language 语言提示
     * @return 识别的文字
     */
    String transcribe(byte[] audioData, String fileName, String language);
}
```

- [ ] **Step 2: TranscribeService 实现接口**

```java
public class TranscribeService implements ITranscribeService {
```

- [ ] **Step 3: TouristController 注入改为接口**

```java
private final ITranscribeService transcribeService;
```

- [ ] **Step 4: 编译验证**

```bash
JAVA_HOME="C:/Program Files/Java" mvn compile -pl jingbanyou-tourist -am -q
```

---

### Task 4: 为 TtsService 添加接口

**Files:**
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/service/ITtsService.java`
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/service/TtsService.java:1`
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/controller/TouristController.java:56`

- [ ] **Step 1: 创建 ITtsService 接口**

```java
package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import reactor.core.publisher.Flux;

/**
 * 语音合成服务接口
 *
 * @author jingbanyou
 */
public interface ITtsService {

    /**
     * 流式合成语音
     *
     * @param text 合成文本
     * @param digitalHuman 数字人配置
     * @return 音频字节流
     */
    Flux<byte[]> streamAudio(String text, DigitalHumanConfig digitalHuman);

    /**
     * 合成语音（文件方式）
     *
     * @param text 合成文本
     * @param digitalHuman 数字人配置
     * @return 音频文件访问路径
     */
    String synthesize(String text, DigitalHumanConfig digitalHuman);
}
```

- [ ] **Step 2: TtsService 实现接口**

```java
public class TtsService implements ITtsService {
```

- [ ] **Step 3: TouristController 注入改为接口**

```java
private final ITtsService ttsService;
```

- [ ] **Step 4: 编译验证**

```bash
JAVA_HOME="C:/Program Files/Java" mvn compile -pl jingbanyou-tourist -am -q
```

---

### Task 5: 为 ProfileVectorStoreService 添加接口

**Files:**
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/service/IProfileVectorStoreService.java`
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/service/ProfileVectorStoreService.java:1`
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/graph/node/ProfileLoaderNode.java`
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/graph/node/ProfileUpdaterNode.java`

- [ ] **Step 1: 创建 IProfileVectorStoreService 接口**

```java
package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.tourist.pojo.VisitorProfile;
import cn.edu.gdou.jingbanyou.tourist.service.ProfileVectorStoreService.SimilarProfile;
import java.util.List;

/**
 * 用户画像向量存储服务接口
 *
 * @author jingbanyou
 */
public interface IProfileVectorStoreService {

    /**
     * 将游客画像偏好存入向量库
     *
     * @param profile 游客画像
     */
    void saveProfile(VisitorProfile profile);

    /**
     * 从向量库检索相似历史偏好
     *
     * @param visitorId 游客ID
     * @param query 语义查询
     * @return 匹配的历史偏好列表
     */
    List<SimilarProfile> retrieveSimilarProfiles(String visitorId, String query);

    /**
     * 根据 visitorId 删除向量库中的记录
     *
     * @param visitorId 游客ID
     */
    void deleteProfile(String visitorId);
}
```

- [ ] **Step 2: ProfileVectorStoreService 实现接口**

```java
public class ProfileVectorStoreService implements IProfileVectorStoreService {
```

- [ ] **Step 3: ProfileLoaderNode 注入改为接口**

将 `private final ProfileVectorStoreService profileVectorStoreService;` 改为注入 `IProfileVectorStoreService`。

- [ ] **Step 4: ProfileUpdaterNode 注入改为接口**

将 `private final ProfileVectorStoreService profileVectorStoreService;` 改为注入 `IProfileVectorStoreService`。

- [ ] **Step 5: 编译验证**

```bash
JAVA_HOME="C:/Program Files/Java" mvn compile -pl jingbanyou-tourist -am -q
```

---

## 阶段三：P2 — 代码质量修复

### Task 6: 修复 VisitorAnalysisServiceImpl 中 FocusPoints 存储格式

**Files:**
- Modify: `jingbanyou-manage/src/main/java/cn/edu/gdou/jingbanyou/manage/service/impl/VisitorAnalysisServiceImpl.java:58`

- [ ] **Step 1: 注入 ObjectMapper 或使用已有序列化工具**

检查类中是否已有 `ObjectMapper`。如果没有，添加依赖注入。

- [ ] **Step 2: 修改 setFocusPoints 为 JSON 序列化**

将第58行：
```java
analysis.setFocusPoints(focusPoints.toString());
```
改为：
```java
analysis.setFocusPoints(objectMapper.writeValueAsString(focusPoints));
```

- [ ] **Step 3: 编译验证**

```bash
JAVA_HOME="C:/Program Files/Java" mvn compile -pl jingbanyou-manage -am -q
```

---

### Task 7: 修复 manage pom.xml 编译器版本

**Files:**
- Modify: `jingbanyou-manage/pom.xml`

- [ ] **Step 1: 检查父 POM 的 Java 版本**

读取父 POM 确认 `<java.version>` 值（预期为17）。

- [ ] **Step 2: 修改 manage pom.xml**

将 `<source>15</source><target>15</target>` 改为：
```xml
<maven.compiler.source>${java.version}</maven.compiler.source>
<maven.compiler.target>${java.version}</maven.compiler.target>
```
或直接删除这两行，继承父 POM 配置。

- [ ] **Step 3: 编译验证**

```bash
JAVA_HOME="C:/Program Files/Java" mvn compile -pl jingbanyou-manage -am -q
```

---

### Task 8: 清理未使用的 RagPrecheckResult POJO

**Files:**
- Delete: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/graph/pojo/RagPrecheckResult.java`
- Verify: 整个项目无引用（通过全局搜索确认）

- [ ] **Step 1: 搜索确认无引用**

```bash
grep -r "RagPrecheckResult" --include="*.java" C:/Users/c1342/IdeaProjects/20260402154946/jingbanyou-ai-tour-guide/jingbanyou-tourist/src
```

预期：无结果（除自身文件）

- [ ] **Step 2: 删除文件**

- [ ] **Step 3: 编译验证**

```bash
JAVA_HOME="C:/Program Files/Java" mvn compile -pl jingbanyou-tourist -am -q
```

---

### Task 9: 完善 ChatMemoryService 的对话类型处理

**Files:**
- Modify: `jingbanyou-manage/src/main/java/cn/edu/gdou/jingbanyou/manage/mapper/VisitorInteractionMapper.java`（确认 `interaction_type` 字段）
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/service/ChatMemoryService.java:46`

- [ ] **Step 1: 确认 VisitorInteraction 实体支持 voice 类型**

读取 `VisitorInteraction.java` 确认 `interactionType` 字段存在且支持 "voice" 值。

- [ ] **Step 2: ChatMemoryService 同步时区分 text/voice**

当前实现中 `record.setInteractionType("text")` 是硬编码的。由于 Redis ChatMemory 中无法区分 text/voice，建议：
- 方案A：从 sessionId 或消息内容中推断
- 方案B：保持 text（当前简单合理，voice 类型交互数据来源较少）

如选择方案A，可在方法参数中增加 `interactionType` 参数，由调用方指定。

- [ ] **Step 3: 编译验证**

```bash
JAVA_HOME="C:/Program Files/Java" mvn compile -pl jingbanyou-tourist -am -q
```

---

## 阶段四：P3 — 功能补全

### Task 10: 景点删除时联动清理 Redis 向量

**Files:**
- Modify: `jingbanyou-manage/src/main/java/cn/edu/gdou/jingbanyou/manage/service/impl/ScenicAreaServiceImpl.java`
- Create: `jingbanyou-manage/src/main/java/cn/edu/gdou/jingbanyou/manage/service/IScenicAreaService.java` 中添加删除接口（如果需要扩展）

- [ ] **Step 1: 注入 VectorStore**

在 `ScenicAreaServiceImpl` 中注入 `faqVectorStore` 和 `knowledgeVectorStore`。

- [ ] **Step 2: 重写删除逻辑（使用 MyBatis-Plus 的 IsDeleted 逻辑或修改 deleteById）**

在删除景区前，清理该景区下所有 FAQ 和知识库的 Redis 向量：
- 查询该 scenicId 下的所有 Faq，提取 vectorId，批量删除
- 查询该 scenicId 下的所有 KnowledgeChunk，提取 vectorId，批量删除

注意：此操作涉及复杂的联锁删除，需在事务中处理。

- [ ] **Step 3: 编译验证**

```bash
JAVA_HOME="C:/Program Files/Java" mvn compile -pl jingbanyou-manage -am -q
```

---

### Task 11: 补全测试用例

**Files:**
- Modify: `jingbanyou-tourist/src/test/java/cn/edu/gdou/jingbanyou/tourist/ChatTest.java`
- Modify: `jingbanyou-tourist/src/test/java/cn/edu/gdou/jingbanyou/tourist/AiFeatureTest.java`

- [ ] **Step 1: 补全 ChatTest.chatWithModel()**

为 `ChatTest` 添加有意义的测试用例，例如：
```java
@Test
void chatWithModel() {
    // 测试基本对话流程
    // 需要 mock ChatClient 或使用 @SpringBootTest
}
```

- [ ] **Step 2: 补全 AiFeatureTest 的 ASR 测试注释**

在 TODO 处补充说明：需要提供真实音频文件或 mock AudioTranscriptionModel。

- [ ] **Step 3: 运行测试验证**

```bash
JAVA_HOME="C:/Program Files/Java" mvn test -pl jingbanyou-tourist -q
```

---

## 阶段五：最终验证

### Task 12: 全模块编译 + 二次检查

**Files:**
- 全项目

- [ ] **Step 1: 完整编译**

```bash
JAVA_HOME="C:/Program Files/Java" mvn clean compile -pl jingbanyou-admin,jingbanyou-manage,jingbanyou-tourist -am
```

预期：BUILD SUCCESS，无 error

- [ ] **Step 2: 单元测试**

```bash
JAVA_HOME="C:/Program Files/Java" mvn test -pl jingbanyou-tourist,jingbanyou-manage -q
```

预期：所有测试通过（或无测试失败）

- [ ] **Step 3: 二次代码审查**

使用 code-review 技能对修改的文件进行审查：
- `IChatMemoryService.java` / `ChatMemoryService.java`
- `ITranscribeService.java` / `TranscribeService.java`
- `ITtsService.java` / `TtsService.java`
- `IProfileVectorStoreService.java` / `ProfileVectorStoreService.java`
- `VisitorAnalysisServiceImpl.java`

---

## 依赖关系

```
Task 1 (编译验证)
  ↓
Task 2,3,4,5 (Service接口) — 可并行
  ↓
Task 6,7,8,9 (代码质量) — 可并行
  ↓
Task 10,11 (功能补全) — 可并行
  ↓
Task 12 (最终验证)
```

## 预估工作量

| 阶段 | 任务数 | 优先级 |
|------|--------|--------|
| P0 编译修复 | 1 | 🔴 阻塞 |
| P1 架构规范 | 4 | 🟡 重要 |
| P2 代码质量 | 4 | 🟢 一般 |
| P3 功能补全 | 2 | 🟢 可选 |
| 最终验证 | 1 | 🔴 必做 |
