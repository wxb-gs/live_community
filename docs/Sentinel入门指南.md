# Sentinel 入门指南

## 一句话概括

Sentinel 是阿里巴巴开源的"流量防卫兵"——它站在你的服务入口，实时监控进来的请求，一旦流量超过你设定的阈值，立刻执行限流、熔断、降级等保护动作。你可以把它理解为微服务世界的"水坝闸门"：平时全开，水位高了就关小，溃坝前直接断流。

---

## 1. Sentinel 在项目中到底干了什么

以我们这个 `spring_cloud_test` 项目为例：

```
Client
  │  GET /api/upload/presigned?fileName=test.jpg&contentType=image/jpeg
  ▼
┌──────────────────────────────────────────────────────────────┐
│ gateway :8080                                                │
│                                                              │
│   UploadController.presignedUrl()                            │
│     │                                                        │
│     ├── @SentinelResource(value="upload-presigned",          │
│     │       blockHandler="rateLimitFallback")                │
│     │                                                        │
│     │   ① 请求进来，Sentinel 检查 "upload-presigned" 这个     │
│     │      资源的 QPS 是否超过阈值                             │
│     │                                                        │
│     │   ② 如果没超过 → 正常执行 Dubbo 调用 upload-service     │
│     │      如果超过了  → 走 rateLimitFallback()，返回 429     │
│     │                                                        │
│     └──────────────────────────────────────────┐             │
│                                                ▼             │
│     public Result<?> rateLimitFallback(...) {                 │
│         return Result.error(429, "Too many requests...");    │
│     }                                                        │
└──────────────────────────────────────────────────────────────┘
```

**步骤拆解：**

1. **请求到达** — 用户请求 `/api/upload/presigned`
2. **Sentinel 拦截** — `@SentinelResource` 注解让 Sentinel 在方法执行前插入检查逻辑
3. **规则匹配** — Sentinel 找到名为 `upload-presigned` 的资源配置，判断当前 QPS 是否超过限制
4. **放行 or 阻断** — 没超过 → 正常调用 Dubbo；超过了 → 走 `rateLimitFallback` 降级方法
5. **降级返回** — Fallback 方法返回 `429 Too Many Requests`，不会让请求堆积压垮服务

**关键认知：Sentinel 是防御性中间件，它不处理正常流量，只在异常情况下介入。正常运行时不增加任何额外逻辑。**

---

## 2. Sentinel 的核心能力全景图

```
                     ┌─────────────────────┐
                     │     Sentinel        │
                     │   流量防卫兵         │
                     └──────────┬──────────┘
         ┌──────────────────────┼──────────────────────┐
         │                      │                      │
    ┌────▼────┐           ┌────▼────┐           ┌─────▼─────┐
    │  限流    │           │  熔断    │           │  系统保护  │
    │ Flow    │           │ Degrade │           │  System   │
    └────┬────┘           └────┬────┘           └─────┬─────┘
         │                      │                      │
    ┌────▼────┐           ┌────▼────┐           ┌─────▼─────┐
    │ QPS/线程 │           │ 慢调用比 │           │  Load/CPU  │
    │ 数/并发  │           │ 异常比例 │           │  RT/QPS   │
    └─────────┘           └─────────┘           └───────────┘
```

本项目的 `@SentinelResource(value = "upload-presigned", blockHandler = "rateLimitFallback")` 使用了**限流**能力中的 QPS 控制。

---

## 3. 限流（Flow Control）详解

限流是 Sentinel 最核心的功能：**控制每秒通过的请求数，超出的直接拒绝。**

### 3.1 限流模式

```
                    ┌────────────────────────┐
                    │      限流模式           │
                    └───────────┬────────────┘
            ┌───────────────────┼───────────────────┐
            │                   │                   │
      ┌─────▼─────┐      ┌─────▼─────┐      ┌─────▼─────┐
      │  直接模式  │      │  关联模式  │      │  链路模式  │
      │  (默认)    │      │           │      │           │
      └─────┬─────┘      └─────┬─────┘      └─────┬─────┘
            │                   │                   │
    限制当前资源自身      当关联资源达到阈值      只限制从某个入口
    例如：presigned URL     时限制当前资源。         来的请求。
    的 QPS 不能超过 50     例如：写操作的 QPS       例如：/api/upload
                          过高时限制读操作。       从 gateway 进来的
                                                  限制 50 QPS, 从
                                                  内部进来的不限制。
```

**本项目用的是直接模式**：限制 `upload-presigned` 这个资源自身的 QPS。

### 3.2 限流算法

Sentinel 的限流效果有三种，对应不同的滑动窗口算法：

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. 快速失败（默认）—— 滑动窗口计数法                              │
│                                                                  │
│  时间轴：├─────┼─────┼─────┼─────┼─────┤                         │
│          0    0.2   0.4   0.6   0.8   1.0 (秒)                   │
│                                                                  │
│  第 0.0s  第 0.2s  第 0.4s  第 0.6s  第 0.8s  第 1.0s            │
│  5 个请求  8 个请求  6 个请求  10个请求  8 个请求  4 个请求         │
│                                                                  │
│  窗口大小 = 1秒，每个小窗口 = 0.2秒                               │
│  当前 QPS = 5+8+6+10+8 = 37                                      │
│  如果阈值是 50，则通过；如果新请求让 QPS 超过 50，则拒绝。         │
│                                                                  │
│  特点：精确计数，滚动窗口，拒绝超出的请求                          │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│ 2. Warm Up（预热）—— 令牌桶算法变体                               │
│                                                                  │
│  QPS ↑                                                           │
│  50 │                      ╭────── 最终稳定在 50 QPS               │
│     │                   ╭──╯                                      │
│  16 │──────────────────╯                                          │
│     │   预热期（30秒）                                              │
│     └────────────────────────────────────────→ 时间                │
│                                                                  │
│  适用场景：秒杀开始瞬间，系统需要预热，不能一上来就扛满 QPS。       │
│  冷启动因子 = 1/3 → 初期 QPS = 阈值 / 3，逐步提升。               │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│ 3. 排队等待 —— 漏桶算法                                           │
│                                                                  │
│  请求 → ┌──────────┐ → ┌────┐ → 出队处理                          │
│         │ 排队队列  │   │漏桶│    (匀速)                           │
│         └──────────┘   └────┘                                    │
│                                                                  │
│  超过队列容量的请求直接拒绝，队列内的请求匀速通过。                  │
│  适用场景：消息队列类型的处理，允许一定延迟换取不丢失请求。         │
└──────────────────────────────────────────────────────────────────┘
```

### 3.3 本项目中的限流代码

```java
// gateway/src/main/java/com/example/gateway/controller/UploadController.java

@GetMapping("/presigned")
@SentinelResource(
    value = "upload-presigned",            // 资源名，规则配置的 key
    blockHandler = "rateLimitFallback"     // 被限流时调用的降级方法
)
public Result<PresignedUrlResponse> presignedUrl(
        @RequestParam String fileName,
        @RequestParam String contentType) {
    PresignedUrlRequest req = new PresignedUrlRequest(fileName, contentType);
    PresignedUrlResponse resp = uploadRpcService.generatePresignedUrl(req);
    return Result.ok(resp);
}

// blockHandler 方法签名必须和原方法一致，多一个 BlockException 参数
public Result<?> rateLimitFallback(
        String fileName, String contentType, BlockException ex) {
    return Result.error(429, "Too many requests, please try again later");
}
```

### 3.4 限流规则配置

有两种方式配置规则：

**方式一：代码硬编码（本项目用的方式）**

```java
// 在应用启动时初始化规则
@PostConstruct
public void initFlowRules() {
    List<FlowRule> rules = new ArrayList<>();
    FlowRule rule = new FlowRule();
    rule.setResource("upload-presigned");   // 对应 @SentinelResource 的 value
    rule.setGrade(RuleConstant.FLOW_GRADE_QPS);  // 按 QPS 限制
    rule.setCount(50);                      // 阈值 50 QPS
    rules.add(rule);
    FlowRuleManager.loadRules(rules);
}
```

**方式二：Sentinel Dashboard 控制台动态配置（生产推荐）**

```
打开 Sentinel Dashboard → 簇点链路 → 找到 "upload-presigned"
  → 流控 → 新增规则：
    - 资源名：upload-presigned
    - 阈值类型：QPS
    - 阈值：50
    - 流控模式：直接
    - 流控效果：快速失败
```

Dashboard 修改规则后实时生效，无需重启应用。

---

## 4. 熔断降级（Circuit Breaking）详解

熔断和限流经常被混淆，但它们是两种不同的保护机制：

| | 限流 (Flow Control) | 熔断 (Circuit Breaking) |
|------|-----|------|
| **触发条件** | 流量超过阈值 | 下游服务出错/变慢 |
| **保护对象** | 保护自己不被冲垮 | 保护自己不把下游打垮，同时快速失败 |
| **动作** | 拒绝超出阈值的请求 | 暂时停止调用该下游，直接返回降级结果 |
| **恢复** | 下一秒自动恢复（滑动窗口滑过） | 需要等待熔断时间窗口后探测恢复 |
| **类比** | 景区限流（人数到了关门） | 电路跳闸（短路了先断掉，过会儿合闸试试） |

### 4.1 熔断策略

```
┌───────────────────────────────────────────────────────────────────┐
│ 策略一：慢调用比例（SLOW_REQUEST_RATIO）                            │
│                                                                   │
│  条件：statIntervalMs 内请求数 ≥ 最小请求数，且慢调用比例 > 阈值    │
│                                                                   │
│  示例：1 秒内 10 个请求，3 个超过 500ms（慢调用阈值）                │
│  慢调用比例 = 3/10 = 30% > 20%（配置阈值）→ 熔断！                  │
│                                                                   │
│  熔断后：后续请求直接走 fallback，不调用远程服务                      │
│  熔断时长过后：放一个请求探测，成功就关闭熔断，失败就继续熔断          │
└───────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────────┐
│ 策略二：异常比例（ERROR_RATIO）                                     │
│                                                                   │
│  条件：statIntervalMs 内请求数 ≥ 最小请求数，且异常比例 > 阈值       │
│                                                                   │
│  示例：1 秒内 10 个请求，4 个抛出异常                                │
│  异常比例 = 4/10 = 40% > 30%（配置阈值）→ 熔断！                    │
└───────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────────┐
│ 策略三：异常数（ERROR_COUNT）                                       │
│                                                                   │
│  条件：统计时长内异常数 > 阈值                                      │
│                                                                   │
│  示例：1 分钟内出现 5 个异常 → 熔断！                               │
└───────────────────────────────────────────────────────────────────┘
```

### 4.2 本项目中可以加的熔断示例

以 `note-service` 的 `createDraft` 为例，如果依赖的 `leaf-service` 变慢或宕机：

```java
@DubboService
public class NoteRpcServiceImpl implements NoteRpcService {

    // 原方法：直接调 leaf-service 获取 ID，leaf 挂了就抛异常
    @Override
    @SentinelResource(
        value = "createDraft",
        fallback = "createDraftFallback",      // fallback ≠ blockHandler
        blockHandlerClass = NoteBlockHandler.class
    )
    public CreateDraftResponse createDraft(CreateDraftRequest request) {
        IdResponse idResp = leafRpcService.generateSegmentId("note");
        return noteService.createDraft(request, idResp.getId());
    }

    // fallback 处理所有异常（包括 Dubbo 调用异常、业务异常）
    public CreateDraftResponse createDraftFallback(
            CreateDraftRequest request, Throwable t) {
        // 降级方案：返回一个抖机灵的草稿 ID，让用户稍后重试
        return new CreateDraftResponse(-1L, "FAILED");
    }
}
```

`blockHandler` vs `fallback` 的区别：

| | blockHandler | fallback |
|------|-------------|----------|
| **触发场景** | 仅限流/熔断/系统保护 | 所有异常（含 block + 业务异常） |
| **参数签名** | 最后多一个 `BlockException` | 最后多一个 `Throwable` |
| **优先级** | 先生效（被 block 时不走 fallback） | block 和异常都会触发 |

### 4.3 熔断状态机

```
          正常调用成功
    ┌─────────────────────────┐
    │                         │
    ▼                         │
┌───────┐   触发熔断    ┌──────┴──────┐   熔断窗口结束    ┌──────────┐
│ CLOSED │───────────→│    OPEN     │────────────────→│ HALF-OPEN │
│ (正常)  │            │  (拒绝所有)  │                  │ (探测中)   │
└───────┘             └─────────────┘                  └─────┬─────┘
                                                            │
                                          ┌─────────────────┼─────────┐
                                          │                 │         │
                                     放一个请求         探测成功    探测失败
                                     探测下游       → 回到 CLOSED  → 回到 OPEN
```

这个三段状态和断路器原理完全一致。

---

## 5. 灰度发布（Grayscale Routing）详解

灰度发布在 Sentinel 中并不是独立的功能，而是结合 **流控规则 + 来源/参数路由** 实现的。

### 5.1 什么是灰度发布

```
                     ┌─────────────┐
                     │   Load      │
                     │  Balancer   │
                     └──────┬──────┘
                            │
          ┌─────────────────┼─────────────────┐
          │ 90% 流量        │                 │ 10% 流量
          ▼                 │                 ▼
  ┌──────────────┐          │        ┌──────────────┐
  │ note-service │          │        │ note-service │
  │   v1.0       │          │        │   v2.0 (灰度) │
  │  (稳定版本)   │          │        │  (新版本)     │
  └──────────────┘          │        └──────────────┘
                                                  │
                                          只有内测用户/
                                          特定 Header 的
                                          请求才路由到这
```

核心思想：**新版本先给一小部分用户用，验证没问题再全量发布。**

### 5.2 本项目中实现灰度的方案

方案一：**通过 Header 参数路由（推荐）**

```java
@GetMapping("/detail")
@SentinelResource(value = "note-detail")
public Result<NoteDetailResponse> getNoteDetail(
        @RequestParam Long noteId,
        @RequestHeader(value = "X-Version", required = false) String version) {

    NoteDetailResponse resp;

    // 灰度逻辑：header 带 X-Version: v2 的请求走新实现
    if ("v2".equals(version)) {
        resp = noteRpcService.getNoteDetailV2(noteId);   // 新的 Dubbo 接口
    } else {
        resp = noteRpcService.getNoteDetail(noteId);     // 老的 Dubbo 接口
    }
    return Result.ok(resp);
}
```

方案二：**通过 Sentinel Authority 规则按来源控制**

Sentinel 的授权规则可以限制"谁可以访问"，配合灰度场景黑/白名单：

```
规则：authority
  资源：note-detail
  流控应用：gray-user-group    ← 白名单
  授权类型：白名单

效果：只有请求来源应用名为 gray-user-group 的请求能访问
```

方案三：**结合 Nacos 配置动态控制灰度比例**

```
Nacos 中存储灰度配置：
  gray.release.percentage = 10    # 10% 流量走灰度

应用代码读取该配置，用 hash(userId) % 100 < 10 来决定是否走灰度链路。
修改 Nacos 配置后自动刷新，不发版即可调整灰度比例。
```

这种方案可以实现：
- 0% → 10% → 50% → 100% 逐步放量
- 出问题秒级回滚到 0%（只需在 Nacos 改一个数字）

### 5.3 Dubbo 层面的灰度路由

Dubbo 3.x 自带标签路由能力，可以不写代码实现灰度：

```yaml
# 灰度版本的 Provider 打上标签
dubbo:
  provider:
    tag: gray          # 标记这是灰度实例
```

```yaml
# Consumer 端通过 RpcContext 传递灰度标签
RpcContext.getClientAttachment().setAttachment("dubbo.tag", "gray");
```

这样灰度请求自动路由到灰度实例，普通请求路由到普通实例。

---

## 6. 对比：Sentinel vs Hystrix vs Resilience4j

这是面试高频题。三者都是流量防护工具，但设计代差明显。

| 特性 | **Sentinel** | **Hystrix** | **Resilience4j** |
|------|-----------|------------|-------------------|
| **维护状态** | 活跃维护（阿里 + Apache） | **已停维**（Netflix 2018 年宣布） | 活跃维护 |
| **限流** | ✅ QPS/线程数/关联/链路/预热/排队 | ❌ 只支持线程池/信号量隔离 | ✅ RateLimiter |
| **熔断** | ✅ 慢调用/异常比例/异常数 | ✅ 基于时间窗口的错误比例 | ✅ 基于滑动窗口的错误比例 |
| **系统保护** | ✅ Load/CPU/RT 自适应 | ❌ | ❌ |
| **控制台** | ✅ Sentinel Dashboard（实时监控+规则管理） | ✅ Hystrix Dashboard（仅监控） | ❌ 无内置（需接 Prometheus） |
| **规则持久化** | ✅ Nacos/Apollo/ZK 等 | ❌ 内存（重启丢失） | ❌ 需自行实现 |
| **动态规则** | ✅ Dashboard 实时推送 | ❌ 需改配置重启 | ❌ 需自行实现 |
| **线程模型** | 基于信号量（无额外线程开销） | 基于线程池（有额外开销） | 基于信号量 |
| **资源模型** | 资源 URL / 方法名任意字符串 | Command Key + Group Key | 方法引用 / 函数式 |
| **注解支持** | `@SentinelResource` | `@HystrixCommand` | `@RateLimiter` / `@CircuitBreaker` / `@Bulkhead` |
| **生态绑定** | Spring Cloud Alibaba | Spring Cloud Netflix | 无绑定，任意 Java 项目 |
| **适合场景** | Spring Cloud Alibaba / Dubbo 体系 | 遗留 Netflix OSS 项目、存量 Hystrix 迁移 | 纯断路器需求、新项目 |

### 选型建议

```
你的技术栈是 Spring Cloud Alibaba？
  → 直接用 Sentinel。同门产品，性能比 Hystrix 好，功能比 Resilience4j 全。

你有存量项目用的是 Hystrix？
  → 尽快迁移。Hystrix 2018 年停维，不再有安全补丁。

你只需要熔断、不需要限流 / 控制台 / 动态规则？
  → Resilience4j 更轻量。Sentinel 功能多但依赖也重。

你的系统要求极致低延迟？
  → Sentinel 或 Resilience4j（都是信号量模型）。Hystrix 的线程池模式有上下文切换开销。
```

---

## 7. 面试高频问题

### Q1: Sentinel 的限流和熔断有什么区别？

**答：**

| | 限流 | 熔断 |
|------|------|------|
| **触发条件** | QPS/线程数超过阈值 | 下游出错率/慢调用比例超过阈值 |
| **保护目标** | 保护自己 | 保护下游 + 快速失败 |
| **生效方式** | 拒绝超出阈值的请求 | 暂时不调下游，直接走 fallback |
| **恢复方式** | 下一秒自动恢复 | 等待熔断窗口后探测 |

一句话：**限流是"我这里扛不住了别来了"，熔断是"下游挂了别去了"。**

### Q2: Sentinel 和 Hystrix 的线程模型有什么不同？

**答：**

- **Hystrix** 默认用线程池隔离：每个依赖（如 note-service）分配一个独立线程池。优点是完全隔离，某个下游慢了不会耗尽主线程。缺点是线程上下文切换有开销，线程池数量多了内存压力大。
- **Sentinel** 用信号量（计数器）隔离：请求来计数 +1，处理完 -1，计数器超过阈值就拒绝。优点是无额外线程开销、低延迟。缺点是被调用的下游如果无限阻塞，计数器不会释放。

两者的取舍：Hystrix 更隔离但更重，Sentinel 更轻量但依赖下游超时设置。

### Q3: blockHandler 和 fallback 的区别？什么时候用哪个？

**答：**

```java
@SentinelResource(
    value = "myResource",
    blockHandler = "handleBlock",   // 只处理 BlockException（限流/熔断/系统保护）
    fallback = "handleFallback"     // 处理所有异常（含 BlockException + 业务异常）
)
public Result<?> myMethod() { ... }

// blockHandler：参数签名最后多一个 BlockException
public Result<?> handleBlock(BlockException ex) {
    return Result.error(429, "被限流了");
}

// fallback：参数签名最后多一个 Throwable
public Result<?> handleFallback(Throwable t) {
    return Result.error(500, "出错了");
}
```

**优先级**：如果同时配置，`blockHandler` 先生的效。BlockException 不会触发 fallback（除非只配了 fallback）。

**实践中**：对外接口配 `blockHandler` 返回友好错误码（429），内部调用配 `fallback` 兜底。

### Q4: Sentinel 的规则存在哪？重启会不会丢？

**答：**

默认存在 JVM 内存中，**重启就丢了**。

生产环境必须持久化到外部数据源：

```yaml
spring:
  cloud:
    sentinel:
      datasource:
        ds1:
          nacos:
            server-addr: localhost:8848
            data-id: sentinel-flow-rules
            group-id: DEFAULT_GROUP
            rule-type: flow        # 持久化限流规则
        ds2:
          nacos:
            server-addr: localhost:8848
            data-id: sentinel-degrade-rules
            group-id: DEFAULT_GROUP
            rule-type: degrade     # 持久化熔断规则
```

配置好以上后，规则存在 Nacos 中 → 重启不丢 → Dashboard 修改规则也会同步写 Nacos。

### Q5: Sentinel 对业务性能有多大影响？

**答：**

Sentinel 的性能开销极低：

- 每个资源只维护一个滑动窗口计数器（内存操作）
- 没有线程上下文切换（信号量模型）
- 官方压测：Sentinel 在单机 25 万 QPS 时才产生可测量的开销
- 对绝大多数场景（< 10 万 QPS），Sentinel 的开销可以忽略不计

**一句话：除非你做到淘宝级别的流量，否则不用关心 Sentinel 本身的性能。**

### Q6: 如何实现灰度发布的动态切换？

**答：**

核心思路是"配置驱动 + 流量染色"：

```
1. Nacos 中存灰度比例 gray.percentage=20%

2. 请求进来时，根据 userId hash % 100 < 20 决定走灰度还是稳定版本

3. 需要放量时，在 Nacos 控制台把 20% 改成 50%（秒级生效）

4. 出问题时秒级把值改回 0%，所有流量瞬间切回稳定版本
```

实现上可以：
- 应用层：通过 Nacos Config 动态读取比例 + `hash(userId) % 100` 分流
- Dubbo 层：通过 Dubbo 标签路由 + RpcContext 传递灰度标签
- 网关层：通过 Spring Cloud Gateway 的路由规则 + Header 匹配分流

### Q7: 什么场景下适合用 Sentinel，什么场景不需要？

**答：**

**需要 Sentinel 的场景：**

- 流量不可控（C 端用户直接访问的接口）
- 有秒杀/大促等流量尖峰
- 下游依赖多，某个依赖变慢可能引发雪崩
- 需要动态调整限流阈值、灰度放量

**不需要 Sentinel 的场景：**

- 纯内部服务、调用量可控（内部管理后台）
- 只有 2-3 个微服务，调用链路很短
- 已经有 K8s Istio / Envoy 做了流量管理
- 只是个人项目/学习项目，流量对系统没有威胁

**本项目只给对外接口 `presignedUrl` 加上限流是合理的——那是用户直接访问的入口，内部 Dubbo 调用暂时不需要限流。**

---

## 8. 动手实验

### 8.1 验证限流效果

```bash
# 用 ab（Apache Bench）并发请求网关，看限流是否生效

# 正常 QPS 下：
ab -n 100 -c 10 "http://localhost:8080/api/upload/presigned?fileName=test.jpg&contentType=image/jpeg"
# → 全部 200 OK

# 如果配置了 QPS=10，并发 20 压测：
ab -n 200 -c 20 "http://localhost:8080/api/upload/presigned?fileName=test.jpg&contentType=image/jpeg"
# → 部分 429 Too many requests, please try again later
```

### 8.2 在 Sentinel Dashboard 中查看

```
1. 下载 Dashboard
   https://github.com/alibaba/Sentinel/releases
   下载 sentinel-dashboard-1.8.8.jar

2. 启动
   java -jar sentinel-dashboard-1.8.8.jar --server.port=8088

3. 打开 http://localhost:8088
   用户名/密码：sentinel/sentinel

4. 看到实时 QPS 曲线、限流拦截次数
5. 在 "流控规则" 中动态调整阈值，观察效果
```

---

## 9. 常见误区

| 误区 | 实际 |
|------|------|
| "Sentinel 加上了就自动限流" | 不会。需要显式配置规则（代码或 Dashboard），没有规则等于不设防 |
| "限流 = 熔断" | 不是。限流保护自己、熔断保护下游，两者的触发条件和生效方式不同 |
| "Sentinel 拖慢性能" | 不会。纯内存计数器，无额外线程开销，单机 25 万 QPS 才可测 |
| "规则重启就没了" | 默认是的。生产必须配 Nacos/Apollo 持久化数据源 |
| "Dashboard 是必需的" | 不是。规则可以硬编码在代码中，Dashboard 只是方便管理和可视化 |
| "Sentinel 只能用在 Spring Cloud" | 不是。Sentinel 是纯 Java 库，任何 Java 项目都能用，Dubbo 和 WebFlux 也都支持 |

---

## 延伸阅读

- [Sentinel 官方文档](https://sentinelguard.io/zh-cn/docs/introduction.html)
- [Sentinel GitHub](https://github.com/alibaba/Sentinel)
- [Sentinel 在生产环境的实践](https://sentinelguard.io/zh-cn/docs/operation.html)
- [Spring Cloud Alibaba Sentinel 集成](https://spring-cloud-alibaba-group.github.io/github-pages/hoxton/en-us/index.html#_spring_cloud_alibaba_sentinel)

**记住一句话：Sentinel 是微服务的"流量阀门"——限流防止冲垮自己，熔断防止拖死下游，灰度让变更可控。三个能力解决同一个核心问题：让你的系统在不可控的流量面前依然可控。**
