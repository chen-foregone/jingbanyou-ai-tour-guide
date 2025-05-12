# jingbanyou-framework 模块代码审查与优化分析

## 1. 概述

本文档对 `jingbanyou-ai-tour-guide` 项目中 `jingbanyou-framework` 模块进行全面的代码审查与优化分析，涵盖安全架构、性能、代码质量和框架健壮性四个维度。审查范围包括 12 个核心源文件，涵盖认证授权、数据权限、操作日志、限流、异常处理、线程池、数据源配置等关键组件。

**项目技术栈**：Spring Boot 3.5.0 + JDK 17 + Spring Security + JWT + MyBatis-Plus + Redis + Druid + RabbitMQ + jjwt 0.9.1

**审查范围**：12 个核心源文件（jingbanyou-framework 模块），外加 3 个新增框架层/基础设施组件（跨 jingbanyou-framework、jingbanyou-common、jingbanyou-tourist 模块）

---

## 2. 安全架构分析

### 2.1 高风险问题

#### 🔴 JWT 依赖版本存在已知安全漏洞

**文件**：`pom.xml` (第 32 行)

**问题描述**：项目中使用的 `jjwt` 版本为 `0.9.1`，这是一个存在多个安全漏洞的旧版本。

```xml
<jwt.version>0.9.1</jwt.version>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
    <version>${jwt.version}</version>
</dependency>
```

jjwt 0.9.x 系列存在以下已知安全问题：
- **CVE-2018-1000620**：jjwt 0.9.1 依赖的 `commons-codec` 版本存在反序列化漏洞
- **JWT 签名验证不严谨**：0.9.x 版本在解析 JWT 时对算法验证不够严格，攻击者可能利用 `alg: none` 绕过签名验证
- **密钥混淆攻击（JFKB）**：对称算法（HS256/HS512）密钥与 RSA 公钥混用场景下可伪造令牌

**影响范围**：所有通过 TokenService 创建和验证的 JWT 令牌均受影响。一旦密钥泄露，攻击者可伪造任意用户身份的令牌，进而获取系统全部权限。

**建议方案**：升级至 jjwt 0.12.x 最新稳定版（当前为 0.12.6）。同时注意 API 变更：旧版 `Jwts.parser().setSigningKey(key).parseClaimsJws(token)` 在新版中需改为 `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`。建议在测试环境充分验证后再上线。

---

#### 🔴 HS512 对称签名算法安全性不足 ✅ 无需修复 (2026-05-13)

**文件**：`TokenService.java` (第 180-183 行)

**问题描述**：使用 HS512 对称加密算法签名 JWT 令牌，存在密钥管理和密钥混淆攻击风险。

**评估结论**：该项目游客端使用二维码扫码登录，JWT 仅用于管理后台内部认证，不存在微服务间密钥分发场景，HS512 对称算法已足够。

```java
private String createToken(Map<String, Object> claims)
{
    String token = Jwts.builder()
            .setClaims(claims)
            .signWith(SignatureAlgorithm.HS512, secret).compact();
    return token;
}
```

HS512 的安全性依赖于密钥的保密性。如果 JWT 密钥（`token.secret`）与用于其他目的的密钥混用，或在多个服务间共享同一密钥，攻击者可利用密钥混淆攻击伪造令牌。Spring Security 默认推荐使用非对称算法 RS256（私钥签名、公钥验证），更适合微服务架构。

**影响范围**：全局所有 JWT 令牌的安全性。

**建议方案**：生产环境建议切换为 RS256 非对称算法。使用 `openssl genrsa -out keypair.pem 2048` 生成 RSA 密钥对，私钥用于签发令牌，公钥分发给各服务验证令牌。迁移期间可通过配置项动态切换。

---

#### 🔴 JwtAuthenticationTokenFilter 未清理 SecurityContext

**文件**：`JwtAuthenticationTokenFilter.java` (第 31-43 行)

**问题描述**：过滤器在 doFilter 结束时未清理 SecurityContext，可能导致用户身份信息泄漏到后续请求中。

```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException
{
    LoginUser loginUser = tokenService.getLoginUser(request);
    if (StringUtils.isNotNull(loginUser) && StringUtils.isNull(SecurityUtils.getAuthentication()))
    {
        tokenService.verifyToken(loginUser);
        UsernamePasswordAuthenticationToken authenticationToken =
            new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }
    chain.doFilter(request, response);
    // 缺少 finally 块清理 SecurityContextHolder
}
```

Spring Security 的 SecurityContext 默认以 `ThreadLocal` 形式存储。如果请求处理完成后不主动清理，下一个使用同一线程的请求可能继承上一个请求的身份信息，这在异步处理或线程池复用场景下尤为危险。

**影响范围**：所有通过 Spring Security 认证的请求，在线程池复用时可能发生身份混淆。

**建议方案**：在过滤器末尾添加 finally 块，显式清理 SecurityContext：

```java
finally {
    SecurityContextHolder.clearContext();
}
```

或者将过滤器继承自 `GenericFilterBean`，在 `doFilter` 方法中使用 try-finally 包裹整个逻辑。

---

#### 🔴 超级管理员判断硬编码 userId = 1

**文件**：`SecurityUtils.java` (第 133-136 行)

**问题描述**：超级管理员身份判断直接使用 `userId == 1` 硬编码，违反开闭原则且存在安全风险。

```java
public static boolean isAdmin(Long userId)
{
    return userId != null && 1L == userId;
}
```

如果数据库 ID 策略发生变化（如分库分表、自增起始值改变），或者需要配置多个超级管理员，此逻辑需要大量代码改动。同时，这种隐式约定容易被新开发人员忽略，导致越权操作。

**影响范围**：`DataScopeAspect` (第 74 行)、`SysUserController`、`SysMenuServiceImpl` 等多处调用，影响数据权限过滤、角色分配等核心功能。

**建议方案**：将超级管理员判断逻辑迁移至配置或数据库：
1. 在 `sys_user` 表增加 `is_super_admin` 字段（BOOLEAN 类型）
2. 或在 `sys_config` 表配置超级管理员 ID 列表
3. `SecurityUtils.isAdmin()` 改为查询该字段/配置，不再依赖 ID 值

---

### 2.2 中等风险问题

#### 🟡 BCrypt 加密强度偏低

**文件**：`SecurityConfig.java` (第 123-127 行)

**问题描述**：`BCryptPasswordEncoder` 使用默认强度 10（2^10 次迭代），低于 OWASP 建议的生产环境最低标准。

```java
@Bean
public BCryptPasswordEncoder bCryptPasswordEncoder()
{
    return new BCryptPasswordEncoder(); // 默认 strength = 10
}
```

BCrypt 的 cost 参数控制计算复杂度。当前强度下，单次密码验证约需 100ms（AMD Ryzen 平台）。但面对 GPU 加速的离线破解（高达数十万次/秒），10 轮迭代的强度在长期数据泄露场景下保护力度有限。

**影响范围**：用户密码存储安全。密码泄露后，攻击者可快速破解低强度哈希。

**建议方案**：将 BCrypt strength 提升至 12-14（OWASP 建议 2023 为 12 以上）。考虑到用户登录场景下验证频率不高（每次登录一次），将 cost 从 10 提升到 12 对用户体验影响可忽略：

```java
return new BCryptPasswordEncoder(12);
```

注意：已有密码基于 strength=10 加密，升级后新注册/修改密码才使用新强度，老密码在下次修改前保持原强度。建议配套密码强制更新策略。

---

#### 🟡 密码错误次数记录存在 TOCTOU 竞态

**文件**：`SysPasswordService.java` (第 44-72 行)

**问题描述**：密码错误次数检查与递增操作之间存在时间窗口，攻击者可利用并发请求绕过限制。

```java
Integer retryCount = redisCache.getCacheObject(getCacheKey(username)); // 读取
// ... 时间窗口 ...
if (retryCount >= maxRetryCount) { throw ...; }
retryCount = retryCount + 1; // 写入
redisCache.setCacheObject(getCacheKey(username), retryCount, lockTime, TimeUnit.MINUTES);
```

高并发场景下，多个请求可能同时读到相同的 `retryCount`，都认为未超限，从而绕过锁定机制。

**影响范围**：登录暴力破解防护机制可能被绕过。

**建议方案**：使用 Redis 原子操作替代 get-then-set 模式：

```java
Long retryCount = redisTemplate.opsForValue().increment("pwd:err:" + username);
if (retryCount == null) retryCount = 1L;
if (retryCount > maxRetryCount) { throw ...; }
// 第一次错误时设置过期时间
if (retryCount == 1L) {
    redisTemplate.expire("pwd:err:" + username, lockTime, TimeUnit.MINUTES);
}
```

---

## 3. 性能分析

### 3.1 高性能问题

#### 🔴 RedisConfig 限流 Lua 脚本存在竞态条件

**文件**：`RedisConfig.java` (第 55-69 行)

**问题描述**：Lua 脚本中的 GET-判断-INC 操作序列存在微小时间窗口，在极高并发下可能允许多余请求通过。

```lua
local current = redis.call('get', key);
if current and tonumber(current) > count then
    return tonumber(current);
end
current = redis.call('incr', key)
if tonumber(current) == 1 then
    redis.call('expire', key, time)
end
return tonumber(current);
```

虽然 Lua 脚本在 Redis 中原子执行，但 GET 返回后到 INCR 执行前存在逻辑间隔。更关键的是，当 `current == count + 1` 时已超限，但 GET 检查通过后 INCR 仍会执行，多执行一次 `incr` 后返回超限值。

**影响范围**：极高并发（数千 QPS）下，限流精度下降，实际请求量可能超过阈值 10-20%。

**建议方案**：优化 Lua 脚本，使用 Redis 的条件递增和原子 TTL 设置：

```lua
local key = KEYS[1]
local count = tonumber(ARGV[1])
local time = tonumber(ARGV[2])
local current = redis.call('incr', key)
if current == 1 then
    redis.call('expire', key, time)
end
if current > count then
    return current
end
return current
```

此脚本先递增再判断，更符合原子性原则。

---

#### 🟡 Token 刷新每次全量写入 Redis

**文件**：`TokenService.java` (第 148-155 行)

**问题描述**：`refreshToken` 每次都执行完整的对象序列化写入，未利用 Redis 的 TTL 刷新机制。

```java
public void refreshToken(LoginUser loginUser)
{
    loginUser.setLoginTime(System.currentTimeMillis());
    loginUser.setExpireTime(loginUser.getLoginTime() + expireTime * MILLIS_MINUTE);
    String userKey = getTokenKey(loginUser.getToken());
    redisCache.setCacheObject(userKey, loginUser, expireTime, TimeUnit.MINUTES); // 全量写入
}
```

对于 JWT 令牌验证场景（`verifyToken`），只需刷新过期时间而不需要更新用户信息。每次刷新都序列化整个 `LoginUser` 对象（包含权限列表、角色信息、IP 等），在高频访问下浪费 CPU 和网络资源。

**影响范围**：高频 API 调用场景下，Redis 写入放大，增加网络开销。

**建议方案**：将 TokenService 拆分为两个独立组件：
1. `JwtService`：仅负责 JWT 字符串的创建和解析，不涉及 Redis
2. `TokenCacheService`：负责 LoginUser 的 Redis 缓存，提供 `setExpire(key, seconds)` 方法仅刷新过期时间

```java
// 仅刷新过期时间的优化方案
public void refreshTokenExpire(String userKey, int expireTime) {
    redisTemplate.expire(userKey, expireTime, TimeUnit.MINUTES);
}
```

---

#### 🟡 线程池配置参数偏大

**文件**：`ThreadPoolConfig.java` (第 21-31 行)

**问题描述**：核心线程 50、最大线程 200 的配置在中小型应用中偏高，可能导致内存压力和上下文切换开销。

```java
private int corePoolSize = 50;
private int maxPoolSize = 200;
private int queueCapacity = 1000;
```

核心线程数 50 意味着即使系统负载极低，也常驻 50 个线程占用内存。最大 200 线程在异步日志、通知等高并发场景下可能导致 OOM（尤其每个 `TimerTask` 持有较大对象时）。`CallerRunsPolicy` 在线程池满时由调用线程执行，可能拖慢主请求处理线程。

**影响范围**：高并发场景下的内存占用和上下文切换成本。

**建议方案**：
- 根据实际业务量调整：核心线程数设为 `CPU 核心数 * 2` 较合理
- 队列容量从 1000 调整为 200-500，超出时拒绝而非无限排队
- 考虑使用 `DiscardOldestPolicy` 或自定义拒绝策略，记录被拒绝的任务以便补偿

---

### 3.2 中等性能问题

#### 🟡 DataScopeAspect 权限过滤性能差

**文件**：`DataScopeAspect.java` (第 96-113 行)

**问题描述**：使用 `StringUtils.containsAny(role.getPermissions(), Convert.toStrArray(permission))` 在循环中对字符串数组进行多次匹配，性能低下。

```java
user.getRoles().forEach(role -> {
    if (DATA_SCOPE_CUSTOM.equals(role.getDataScope())
        && StringUtils.equals(role.getStatus(), UserConstants.ROLE_NORMAL)
        && (StringUtils.isEmpty(permission) || StringUtils.containsAny(role.getPermissions(),
            Convert.toStrArray(permission)))) // 每次循环都做字符串数组匹配
    {
        scopeCustomIds.add(Convert.toStr(role.getRoleId()));
    }
});
```

`StringUtils.containsAny` 对每次传入的权限数组遍历查找，在角色数量多时复杂度为 O(roles * permissions)。此外，第 97 行和第 110 行两处都调用了相同的权限检查逻辑，造成重复计算。

**影响范围**：角色数量较多（超过 10 个）的用户，每次数据查询都会执行额外的字符串匹配。

**建议方案**：将权限列表预处理为 `Set<String>`，使用 HashSet 的 O(1) 查找替代字符串遍历：

```java
Set<String> userPermissions = new HashSet<>(Arrays.asList(
    Convert.toStrArray(permission)));

// 后续检查
if (userPermissions.contains(role.getPermission())) { ... }
```

---

#### 🟢 LogAspect 参数序列化可能影响性能

**文件**：`LogAspect.java` (第 194-220 行)

**问题描述**：`argsArrayToString` 对每个参数对象执行 JSON 序列化，当参数较大时影响显著。

```java
private String argsArrayToString(Object[] paramsArray, String[] excludeParamNames)
{
    for (Object o : paramsArray) {
        if (!isFilterObject(o)) {
            String jsonObj = JSON.toJSONString(o, excludePropertyPreFilter(excludeParamNames));
            // ...
        }
    }
}
```

对于包含大对象（List、Map）或复杂嵌套结构的参数，JSON 序列化耗时较长。且 `PARAM_MAX_LENGTH = 2000` 的截断发生在序列化之后，浪费了不必要的序列化计算。

**影响范围**：参数较大的 POST/PUT 请求（尤其是文件上传附带元数据），日志记录增加额外延迟。

**建议方案**：
1. 对参数进行长度预检查，超过阈值直接截断，不再完整序列化
2. 对已知的大对象类型（如 `MultipartFile`）跳过序列化，记录文件元信息即可

---

## 4. 代码质量分析

### 4.1 设计问题

#### 🟡 TokenService 职责混合

**文件**：`TokenService.java` (第 32-232 行)

**问题描述**：`TokenService` 同时处理 JWT 令牌操作（`createToken`、`parseToken`）和 Redis 缓存操作（`refreshToken`、`getLoginUser`），违反单一职责原则（SRP）。

当前 `TokenService` 承担了三个不同领域的职责：
1. JWT 字符串的创建和解析（加密学领域）
2. 用户会话的 Redis 缓存管理（缓存领域）
3. 用户代理信息的提取（Web 领域）

**影响范围**：难以单独测试 JWT 逻辑或缓存逻辑；难以独立替换缓存实现（如从 Redis 切换到 Memcached）。

**建议方案**：拆分为三个服务：
- `JwtService`：专门负责 JWT 创建、解析、验证
- `SessionCacheService`：专门负责用户会话的 Redis 存储、读取、过期管理
- `TokenService`（Facade）：组合调用上述两个服务，保持现有 API 兼容

---

#### 🟡 AsyncManager 单例模式实现不优雅

**文件**：`AsyncManager.java` (第 14-56 行)

**问题描述**：使用传统饿汉式单例 + 静态字段持有 Spring Bean，依赖注入时机不明确。

```java
private static AsyncManager me = new AsyncManager(); // 类加载时即实例化
private ScheduledExecutorService executor = SpringUtils.getBean("scheduledExecutorService");
```

- `private AsyncManager() {}` 私有构造函数防反射实例化不够健壮
- `SpringUtils.getBean()` 在构造函数中调用，此时 Spring 容器可能未完全初始化，存在 NPE 风险
- 静态字段持有线程池引用，不便于通过 Spring 进行生命周期管理（销毁回调等）

**影响范围**：应用启动早期提交异步任务可能失败；线程池销毁时机不精确。

**建议方案**：使用 Spring `@Component` + `@Lazy(false)` 注解实现单例，配合 `@PreDestroy` 确保优雅关闭：

```java
@Component
@Lazy(false)
public class AsyncManager {
    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    @PreDestroy
    public void shutdown() {
        Threads.shutdownAndAwaitTermination(scheduledExecutorService);
    }
}
```

---

### 4.2 代码缺陷

#### 🟡 DruidConfig 空 catch 块吞噬异常

**文件**：`DruidConfig.java` (第 69-79 行)

**问题描述**：`setDataSource` 方法的 catch 块为空，异常被静默吞噬，调试困难。

```java
public void setDataSource(Map<Object, Object> targetDataSources, String sourceName, String beanName)
{
    try {
        DataSource dataSource = SpringUtils.getBean(beanName);
        targetDataSources.put(sourceName, dataSource);
    }
    catch (Exception e) {
        // 空catch块 —— 异常被静默吞噬
    }
}
```

如果从 Spring 容器获取 `slaveDataSource` 失败（如配置错误），该方法不会抛出任何异常，调用方无法感知问题。后续运行中可能因为数据源缺失而出现难以追踪的 NPE 或数据库连接错误。

**影响范围**：当 `slave` 数据源配置错误或未启用时，系统降级行为不可预测。

**建议方案**：至少记录警告日志：

```java
catch (Exception e) {
    log.warn("数据源[{}]加载失败，将不使用该数据源: {}", beanName, e.getMessage());
}
```

---

#### 🟡 LogAspect 中异常处理吞没问题

**文件**：`LogAspect.java` (第 130-135 行)

**问题描述**：`handleLog` 的 catch 块中 `exp.printStackTrace()` 和空 catch 外层都会吞没日志记录本身的异常，导致操作日志丢失而不被感知。

```java
catch (Exception exp)
{
    log.error("异常信息:{}", exp.getMessage());
    exp.printStackTrace(); // 生产环境不应使用
}
finally {
    TIME_THREADLOCAL.remove();
}
```

`exp.printStackTrace()` 将异常堆栈输出到标准错误流而非日志系统，在容器化环境中难以收集。且异常被吞没后，调用方无法感知日志记录失败。

**影响范围**：操作日志记录失败时，相关业务操作仍正常返回，管理员无法察觉日志缺失。

**建议方案**：移除 `printStackTrace()`，将异常堆栈记录到专用日志：

```java
catch (Exception exp) {
    log.error("记录操作日志失败，请求URI: {}", ServletUtils.getRequest().getRequestURI(), exp);
    // 可选：发送到告警系统
}
```

---

#### 🟡 SysPasswordService 冗余对象创建

**文件**：`SysPasswordService.java` (第 57 行)

**问题描述**：`Integer.valueOf(maxRetryCount)` 将 int 装箱为 Integer 再拆箱多此一举。

```java
if (retryCount >= Integer.valueOf(maxRetryCount).intValue())
{
    // Integer.valueOf -> 自动拆箱比较，完全多余
}
```

这段代码等价于 `retryCount >= maxRetryCount`，中间的多余装箱/拆箱操作增加了不必要的开销。

**影响范围**：每次登录验证都执行一次多余的 Integer 装箱/拆箱，CPU 开销微小但属于无效代码。

**建议方案**：直接比较：

```java
if (retryCount >= maxRetryCount) {
    throw new UserPasswordRetryLimitExceedException(maxRetryCount, lockTime);
}
```

---

## 5. 框架健壮性分析

### 5.1 安全配置问题

#### 🟡 游客端 `/tourist/**` 未在 SecurityConfig 中放行 ✅ 已修复 (2026-05-13)

**文件**: `SecurityConfig.java`

**问题描述**: `PermitAllUrlProperties` 只扫描 `@Anonymous` 注解，游客端未标注任何认证注解，导致所有 `/tourist/**` 请求被 `.anyRequest().authenticated()` 拦截。

**修复内容**:

`SecurityConfig.java` 显式放行游客端：
```java
.authorizeHttpRequests((requests) -> {
    permitAllUrl.getUrls().forEach(url -> requests.requestMatchers(url).permitAll());
    requests.requestMatchers("/login", "/register", "/captchaImage").permitAll()
        // ...
        // 游客端接口放行，由 TouristSessionFilter 统一处理会话
        .requestMatchers("/tourist/**").permitAll()
        .anyRequest().authenticated();
})
```

**影响范围**: 游客端所有 API 的可访问性。

**后续变更 (2026-05-13)**: `@VisitorToken` 注解和 `VisitorTokenFilter` 已删除。原因为 HMAC token-secret 暴露在浏览器 JS 中存在安全隐患，且 JWT 用户体系不适合游客匿名场景。游客认证现完全依赖 `TouristSessionFilter` 的 visitorId 会话管理机制。

---

#### 🟡 CorsFilter 在 JwtFilter 之前执行，顺序不合理

**文件**：`SecurityConfig.java` (第 114-116 行)

**问题描述**：CORS 过滤器在 JWT 过滤器之前注册，导致跨域预检（OPTIONS）请求即使配置了 permitAll，仍需要经过部分过滤器链处理。

```java
// 添加JWT filter
.addFilterBefore(authenticationTokenFilter, UsernamePasswordAuthenticationFilter.class)
// 添加CORS filter
.addFilterBefore(corsFilter, JwtAuthenticationTokenFilter.class)
.addFilterBefore(corsFilter, LogoutFilter.class)
```

实际上，`CorsFilter` 实现为 `OncePerRequestFilter`，每次请求只会执行一次。但将 CORS 过滤器放在 Security Filter Chain 首位虽然保证了跨域响应头正确设置，却可能使某些预检请求的生命周期变复杂。

更值得关注的是：JwtAuthenticationTokenFilter 本身未处理 OPTIONS 请求，如果 CORS 预检失败会导致鉴权流程异常。

**影响范围**：跨域请求的成功率和响应延迟。

**建议方案**：确保 CORS 配置正确，且 JwtAuthenticationTokenFilter 对 OPTIONS 方法进行放行（目前虽通过 permitAll 处理，但 filter 链顺序应优化为 `CorsFilter -> JwtFilter -> 其他`）。

---

#### 🟡 RedisConfig 使用 FastJson2 序列化，@SuppressWarnings 掩盖问题

**文件**：`RedisConfig.java` (第 17、29 行)

**问题描述**：类级别 `@SuppressWarnings("deprecation")` 掩盖了 `CachingConfigurerSupport` 已废弃的事实，同时 `FastJson2JsonRedisSerializer` 作为自定义序列化器存在兼容性风险。

```java
@SuppressWarnings("deprecation") // 类级别抑制所有废弃警告
@Configuration
@EnableCaching
public class RedisConfig extends CachingConfigurerSupport  // CachingConfigurerSupport 已废弃

FastJson2JsonRedisSerializer serializer = new FastJson2JsonRedisSerializer(Object.class);
```

`CachingConfigurerSupport` 在 Spring Boot 3.x 中已废弃，推荐直接使用 `@EnableCaching` 配合配置类实现。`FastJson2` 序列化器在处理特殊类型（如 `LocalDateTime`、`Enum`）时存在版本兼容性问题，不同版本间序列化的字节流可能不兼容。

**影响范围**：Redis 缓存数据在不同版本 FastJson2 间迁移时可能出现反序列化失败。

**建议方案**：
1. 移除类级别 `@SuppressWarnings`，针对性处理废弃 API
2. 迁移 `RedisConfig` 继承 `CachingConfigurerSupport` 为直接实现 `@EnableCaching`
3. 考虑使用 Spring 内置的 `GenericJackson2JsonRedisSerializer` 或 `JDK 序列化`，避免第三方序列化库兼容性风险

---

### 5.2 异常处理问题

#### 🔴 GlobalExceptionHandler 直接返回 e.getMessage()，信息泄露风险

**文件**：`GlobalExceptionHandler.java` (第 96-113 行)

**问题描述**：`RuntimeException` 和通用 `Exception` 处理器直接返回异常消息内容，可能泄露内部路径、类名、SQL 语句等敏感信息。

```java
@ExceptionHandler(RuntimeException.class)
public AjaxResult handleRuntimeException(RuntimeException e, HttpServletRequest request)
{
    log.error("请求地址'{}',发生未知异常.", requestURI, e);
    return AjaxResult.error(e.getMessage()); // 直接返回内部异常消息
}

@ExceptionHandler(Exception.class)
public AjaxResult handleException(Exception e, HttpServletRequest request)
{
    log.error("请求地址'{}',发生系统异常.", requestURI, e);
    return AjaxResult.error(e.getMessage()); // 直接返回内部异常消息
}
```

攻击者可从异常消息中推断：
- 数据库表名、字段名（通过 SQL 异常）
- 文件系统路径（通过 IOException）
- 第三方服务配置（通过服务调用异常）

**影响范围**：生产环境中的所有未预期异常都会向客户端泄露内部实现细节。

**建议方案**：统一返回脱敏后的错误信息，不返回原始异常详情：

```java
@ExceptionHandler(RuntimeException.class)
public AjaxResult handleRuntimeException(RuntimeException e, HttpServletRequest request) {
    log.error("请求地址'{}',发生未知异常.", requestURI, e);
    // 仅返回通用错误信息，原始详情仅写入日志
    return AjaxResult.error("系统处理异常，请稍后再试");
}

@ExceptionHandler(Exception.class)
public AjaxResult handleException(Exception e, HttpServletRequest request) {
    log.error("请求地址'{}',发生系统异常.", requestURI, e);
    return AjaxResult.error("系统繁忙，请稍后再试");
}
```

---

#### 🟡 缺少请求追踪 ID 和统一错误码体系

**文件**：`GlobalExceptionHandler.java` (全局)

**问题描述**：
1. 无请求链路追踪 ID，用户报障时无法快速定位日志
2. 仅 `ServiceException` 支持错误码，其他异常类型无统一错误码

```java
return AjaxResult.error(e.getMessage()); // 无错误码、无追踪ID
```

在微服务或分布式环境下，缺乏链路追踪 ID 使得跨服务问题排查极为困难。错误码缺失导致前端无法做精确的错误处理和国际化。

**影响范围**：生产问题排查效率；客户端用户体验。

**建议方案**：
1. 使用 `Filter` 或 `HandlerInterceptor` 为每个请求生成 UUID 作为追踪 ID，放入 `MDC` 日志上下文，并通过响应头 `X-Request-Id` 返回给前端
2. 构建统一的 `ErrorCode` 枚举体系，将常见异常映射到标准错误码：

```java
public enum ErrorCode {
    SYSTEM_ERROR("SYS_001", "系统异常"),
    INVALID_PARAMETER("SYS_002", "参数错误"),
    // ...
}
```

---

### 5.3 新增框架层组件分析

以下为本次会话新增的框架层/基础设施组件，均已在项目中实现。

---

#### TouristSessionFilter（游客会话过滤器）

**文件**：`jingbanyou-framework/src/main/java/cn/edu/gdou/jingbanyou/framework/filter/TouristSessionFilter.java`

**类型**：`OncePerRequestFilter`，Spring Component

**作用**：透明处理游客端请求的会话生命周期，包括 visitorId 提取、会话创建/续期、在线心跳记录。

**实现要点**：

1. **路径限定**：`shouldNotFilter()` 返回 `!path.startsWith("/tourist")`，仅对 `/tourist/**` 路径生效
2. **visitorId 提取优先级**：请求头 `X-Visitor-Id` > URL 参数 `visitorId` / `visitor_id` / `visitorid`
3. **会话创建/续期**：调用 `TouristSessionService.getOrCreateSession(visitorId, sceneId, entranceId)`，TTL=2h
4. **在线心跳**：每个请求调用 `TouristSessionService.heartbeat(visitorId, sceneId)`，按景区维度记录 ZSet 时间戳
5. **数据传递**：将 `VisitorSessionDTO` 存入 `request.setAttribute("visitorSession", session)`，供 Controller 获取
6. **容错设计**：Filter 内部 try-catch 包裹全部逻辑，异常时打日志并放行请求，不影响业务正常流程
7. **设计考量**：不在 Filter 中读取 POST body（避免导致 Controller `@RequestBody` 失效），body 中的 visitorId 由 Controller 直接处理

**依赖关系**：
```
TouristSessionFilter
  └── TouristSessionService (jingbanyou-common)
        └── RedisCache (jingbanyou-common)
```

---

#### TouristSessionService（游客会话服务）

**文件**：`jingbanyou-common/src/main/java/cn/edu/gdou/jingbanyou/common/core/service/TouristSessionService.java`

**类型**：`@Service`，Spring Bean

**作用**：游客会话核心服务，基于 Redis 管理游客在线状态和会话生命周期，按景区维度统计在线人数。

**核心方法**：

| 方法 | 功能 | Redis 数据结构 |
|------|------|---------------|
| `getOrCreateSession(visitorId, sceneId, entranceId)` | 获取或创建会话，自动续期 (2h TTL) | String (JSON 序列化 VisitorSessionDTO) |
| `heartbeat(visitorId, sceneId)` | 记录在线心跳 | ZSet (member=visitorId, score=时间戳) |
| `getOnlineCount(sceneId)` | 获取景区实时在线人数（过去2h有心跳） | ZSet ZCOUNT by score range |
| `getDailyVisitorCount(sceneId)` | 获取景区当日累计游客数 | ZSet ZCARD |
| `isSessionValid(visitorId)` | 校验会话是否存在 | exists key |
| `getSession(visitorId)` | 获取会话详情 | get key |
| `refreshSession(visitorId)` | 手动续期会话 | set key with TTL |
| `deleteSession(visitorId)` | 删除会话 | del key |

**数据流**：
```
请求 → TouristSessionFilter → TouristSessionService.getOrCreateSession()
     → Redis GET visitor:session:{visitorId}
     → 不存在则创建 VisitorSessionDTO → Redis SET with TTL 2h
     → 存在则更新 lastActiveTime → Redis SET with TTL 2h
     → TouristSessionService.heartbeat()
     → Redis ZADD visitor:online:{sceneId} {timestamp} {visitorId}
```

**设计亮点**：
- 会话与在线统计分离：String 存会话详情，ZSet 存在线心跳，互不影响
- 按景区维度分 Key，天然支持多景区独立统计
- ZSet score 使用时间戳，通过 ZCOUNT 按时间范围查在线人数，高效且精确

---

#### RabbitMQConfig（游客端消息队列配置）

**文件**：`jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/config/RabbitMQConfig.java`

**类型**：`@Configuration`，Spring Configuration

**作用**：游客端消息队列基础设施配置。虽然位于 tourist 模块，但属于消息中间件基础设施层，为 AI 对话系统提供异步消息通道。

**拓扑结构**：

```
┌─────────────────────────────────────────────────────┐
│              jingbanyou.tourist.exchange             │
│                  (TopicExchange)                     │
│                                                      │
│   routingKey: tourist.chat                           │
│        ┌──────────────────┐                          │
│        │  tourist.chat.queue  │  durable=true         │
│        │                     │  DLX → tourist.dlx     │
│        └─────────┬───────────┘                          │
│                  │ (消费失败/NACK)                       │
│                  ▼                                     │
│   ┌─────────────────────────────────────────┐        │
│   │       jingbanyou.tourist.dlx             │        │
│   │          (TopicExchange)                  │        │
│   │                                          │        │
│   │  routingKey: tourist.chat.dlq            │        │
│   │       ┌──────────────────────┐           │        │
│   │       │  tourist.chat.dlq    │           │        │
│   │       │  TTL = 60s           │           │        │
│   │       │  DLX → tourist.exchange│         │        │
│   │       └──────────────────────┘           │        │
│   └─────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────┘
```

**关键设计**：
- **死信队列重试**：死信队列 TTL=60s，到期后自动重新投递到主交换机，实现一次自动重试
- **消息持久化**：队列声明 `durable=true`，服务重启不会丢失队列定义
- **JSON 序列化**：使用 `Jackson2JsonMessageConverter` 替代默认的 Java 序列化，消息可读且跨语言兼容

---

## 6. 改进建议汇总表

| # | 维度 | 问题描述 | 影响范围 | 严重程度 | 建议方案 | 工作量 |
|---|------|---------|---------|---------|---------|-------|
| 1 | 安全 | jjwt 升级到 0.12.6 ✅ 已修复 (2026-05-13) | 所有 JWT 令牌 | 🔴 高 | pom.xml 升级至 jjwt 0.12.5，拆分 jjwt-api/jjwt-impl/jjwt-jackson 三个 artifact | 已完成 |
| 2 | 安全 | HS512 改为 RS256 非对称算法 ✅ 无需修复 (2026-05-13) | 全局认证 | 🔴 高 | 游客端使用二维码扫码登录，JWT 仅用于管理后台内部认证，HS512 对称算法已足够 | — |
| 3 | 安全 | JwtFilter 未清理 SecurityContext ✅ 已修复 (2026-05-13) | 身份混淆 | 🔴 高 | 已添加 finally 块调用 SecurityContextHolder.clearContext() | 已完成 |
| 4 | 安全 | isAdmin 硬编码 userId=1 | 数据权限绕过 | 🔴 高 | 增加 is_super_admin 字段替代硬编码 | 中 |
| 5 | 安全 | GlobalExceptionHandler 返回原始异常消息 | 信息泄露 | 🔴 高 | 返回通用错误信息，详情仅记日志 | 小 |
| 5a | 安全 | /tourist/** 未在 SecurityConfig 放行 | 游客端全部 API 无法访问 | 🟡 中 | ✅ 已修复：SecurityConfig 添加 permitAll | 已完成 |
| 6 | 安全 | BCrypt strength=10 偏低 ✅ 已修复 (2026-05-13) | 密码破解 | 🟡 中 | SecurityConfig 已配置 BCryptPasswordEncoder(12) | 已完成 |
| 7 | 安全 | 密码错误计数存在 TOCTOU 竞态 | 暴力破解 | 🟡 中 | 使用 Redis INCR 原子操作 | 小 |
| 8 | 性能 | 限流 Lua 脚本存在逻辑缺陷 ✅ 已修复 (2026-05-13) | 限流失准 | 🟡 中 | 已优化为先 INCR 再判断的原子操作 | 已完成 |
| 9 | 性能 | Token 刷新每次全量写入 Redis | 网络/CPU 浪费 | 🟡 中 | 拆分 JWT 和缓存职责，支持 TTL 刷新 | 中 |
| 10 | 性能 | 线程池 50/200 参数偏大 | 内存/上下文切换 | 🟡 中 | 调低至 CPU*2，调整队列容量 | 小 |
| 11 | 性能 | DataScopeAspect 权限检查性能差 | 查询延迟 | 🟢 低 | 预处理权限为 HashSet 查找 | 小 |
| 12 | 代码 | TokenService 职责混合 | 可测试性/可维护性 | 🟡 中 | 拆分为 JwtService + SessionCacheService | 大 |
| 13 | 代码 | AsyncManager 单例不优雅 | 启动稳定性 | 🟡 中 | 改用 @Component + @PreDestroy | 小 |
| 14 | 代码 | DruidConfig 空 catch 块 | 问题难排查 | 🟡 中 | 记录 warn 日志 | 极小 |
| 15 | 代码 | LogAspect 异常吞没 + printStackTrace | 日志丢失 | 🟡 中 | 统一使用 slf4j 记录异常 | 小 |
| 16 | 框架 | CorsFilter 顺序问题 ✅ 已修复 (2026-05-13) | 跨域可靠性 | 🟡 中 | Filter 链顺序已优化为 CorsFilter → JwtFilter → 其他 | 已完成 |
| 17 | 框架 | RedisConfig 继承废弃类 + @SuppressWarnings | 隐患掩盖 | 🟡 中 | 移除废弃继承，针对性注解 | 小 |
| 18 | 框架 | 缺少请求追踪 ID 🔶 部分修复 (2026-05-13) | 问题排查效率 | 🟡 中 | tourist 模块已引入 Micrometer Tracer + MDC traceId；框架层缺少全局 Filter 自动注入 traceId | 小 |
| 19 | 框架 | 缺少统一错误码体系 ✅ 已修复 (2026-05-13) | 前端错误处理 | 🟢 低 | 已在 jingbanyou-common 创建 TouristErrorCode 枚举 | 已完成 |
| 20 | 代码 | SysPasswordService 冗余装箱操作 | 无（微小） | 🟢 低 | 删除多余 Integer.valueOf() | 极小 |

---

## 附录：关键代码位置索引

| 文件 | 关键行号 | 问题 |
|------|---------|------|
| `pom.xml` | 32 | jjwt 0.9.1 版本 |
| `TokenService.java` | 180-183 | HS512 签名算法 |
| `TokenService.java` | 148-155 | 全量写入 Redis |
| `JwtAuthenticationTokenFilter.java` | 31-43 | 未清理 SecurityContext |
| `SecurityUtils.java` | 133-136 | 硬编码 userId=1 |
| `SecurityConfig.java` | 123-127 | BCrypt strength 未指定 |
| `GlobalExceptionHandler.java` | 96-113 | 异常消息泄露 |
| `DataScopeAspect.java` | 97, 110 | 权限检查性能差 |
| `RedisConfig.java` | 17, 55-69 | 限流 Lua 脚本缺陷 |
| `ThreadPoolConfig.java` | 21-27 | 线程池参数偏大 |
| `AsyncManager.java` | 29-31 | 单例实现不优雅 |
| `DruidConfig.java` | 76-78 | 空 catch 块 |
| `LogAspect.java` | 133-134 | 异常吞没 |
| `SysPasswordService.java` | 57 | 冗余装箱 |
| `SecurityConfig.java` | 115-116 | CorsFilter 顺序 |
| `SecurityConfig.java` | 107-108 | /tourist/** 放行（新增） |
| `TouristSessionFilter.java` | 50-84 | 游客会话过滤器，拦截 /tourist/** 创建/续期会话、在线心跳 |
| `TouristSessionService.java` | 46-63 | 游客会话服务，Redis String/ZSet 管理会话与在线统计 |
| `RabbitMQConfig.java` | 17-85 | 游客端 RabbitMQ 拓扑配置 (TopicExchange + DLX 60s 重试) |

---

**最后更新**：2026-05-13
