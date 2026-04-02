# 🚀 快速开始指南 - 5 分钟运行第一个 AI Demo

## 前置条件检查

确保以下软件已安装：

```bash
java -version    # 应显示 JDK 17 或更高版本
mvn -version     # 应显示 Maven 3.8+
```

---

## 步骤 1：获取 API Key（2 分钟）

### 方式一：阿里云百炼平台（推荐）

1. 访问：https://bailian.console.aliyun.com/
2. 使用阿里云账号登录
3. 点击"密钥管理" → "创建新密钥"
4. 复制生成的 API Key（格式：`sk-xxxxxxxxxxxxxxxx`）

### 方式二：使用环境变量

```bash
# Windows PowerShell
$env:DASHSCOPE_API_KEY="sk-your-api-key-here"

# Linux/Mac
export DASHSCOPE_API_KEY="sk-your-api-key-here"
```

---

## 步骤 2：配置项目（1 分钟）

编辑 `jingbanyou-ai/src/main/resources/application.yml`：

```yaml
spring:
  ai:
    dashscope:
      api-key: sk-your-actual-api-key-here  # 替换为你的真实 API Key
```

**⚠️ 重要提醒：**
- 不要将真实的 API Key 提交到 Git！
- 生产环境请使用环境变量或配置中心

---

## 步骤 3：编译项目（1 分钟）

在项目根目录执行：

```bash
cd C:\Users\c1342\IdeaProjects\20260402154946\jingbanyou-ai-tour-guide
mvn clean install -pl jingbanyou-ai -am
```

解释：
- `-pl jingbanyou-ai`: 只编译 jingbanyou-ai 模块
- `-am`: 同时编译依赖的模块（common, framework 等）

---

## 步骤 4：运行测试（1 分钟）

```bash
cd jingbanyou-ai
mvn test
```

如果看到类似输出，说明集成成功：

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 步骤 5：启动应用验证

### 方式一：作为独立应用启动

暂时无法在若依主应用中直接集成时，可以单独启动 AI 模块：

```bash
cd jingbanyou-ai
mvn spring-boot:run
```

应用将在 `http://localhost:8081` 启动。

### 方式二：集成到若依主应用

将 AI 模块添加到 `jingbanyou-admin` 的依赖中：

```xml
<!-- jingbanyou-admin/pom.xml -->
<dependency>
    <groupId>cn.edu.gdou</groupId>
    <artifactId>jingbanyou-ai</artifactId>
</dependency>
```

然后在 `RuoYiApplication.java` 所在包下添加扫描路径：

```java
@SpringBootApplication(scanBasePackages = {
    "com.ruoyi",
    "cn.edu.gdou.jingbanyou.ai"  // 添加此行
})
public class RuoYiApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuoYiApplication.class, args);
    }
}
```

---

## 步骤 6：测试 API 接口

使用 Postman 或 curl 测试：

### 测试智能问答

```bash
curl -X POST http://localhost:8081/ai/chat/ask \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "question=请介绍一下这个景区"
```

预期响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": "这是一个示例回答..."
}
```

### 测试路线推荐

```bash
curl -X POST http://localhost:8081/ai/chat/recommend \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "interest=历史文化&duration=120"
```

---

## ✅ 验证清单

完成以上步骤后，检查是否都成功：

- [ ] API Key 已配置
- [ ] Maven 编译无错误
- [ ] 单元测试通过
- [ ] 应用成功启动
- [ ] API 接口能正常响应

---

## 🐛 常见问题排查

### 问题 1：编译失败 - 找不到 Spring AI 依赖

**解决方案：**
确认 `pom.xml` 中已添加 Spring Milestones 仓库：

```xml
<repositories>
    <repository>
        <id>spring-milestones</id>
        <url>https://repo.spring.io/milestone</url>
    </repository>
</repositories>
```

### 问题 2：运行时提示 API Key 无效

**解决方案：**
1. 检查 API Key 是否正确（注意不要有多余空格）
2. 确认账户有可用额度
3. 尝试使用 `qwen-turbo` 模型（免费额度更多）

### 问题 3：端口冲突

**解决方案：**
修改 `application.yml` 中的端口号：

```yaml
server:
  port: 8082  # 改为其他未被占用的端口
```

---

## 📝 下一步

基础环境搭建完成后，可以继续：

1. **部署 ChromaDB 向量数据库**（用于 RAG 知识库）
2. **实现完整的 RAG 问答流程**
3. **集成语音识别和合成**
4. **开发数字人形象驱动**

详细教程请参考后续章节。

---

## 🎉 恭喜！

您已经成功完成了 Spring AI Alibaba 的基础集成！

现在您可以开始开发 AI 数字人的核心功能了。
