# Sentinel 限流功能

## 1. 功能概述

### 1.1 什么是 Sentinel？

Sentinel 是阿里巴巴开源的流量治理组件，面向分布式服务架构的**流量控制、熔断降级、系统保护**。它以流量为切入点，从流量控制、熔断降级、系统负载保护等多个维度保护服务的稳定性。

### 1.2 本项目的使用场景

本项目在 **gateway 的上传接口**上使用 Sentinel 进行限流保护：

```java
@GetMapping("/presigned")
@SentinelResource(value = "upload-presigned", blockHandler = "rateLimitFallback")
public Result<PresignedUrlResponse> presignedUrl(
        @RequestParam String fileName,
        @RequestParam String contentType) {
    // ... 业务逻辑
}

// 限流降级方法
public Result<?> rateLimitFallback(String fileName, String contentType, BlockException ex) {
    return Result.error(429, "Too many requests, please try again later");
}
```

### 1.3 为什么只在上传接口加限流？

上传接口是**资源消耗型**接口——每次调用都要生成 MinIO 预签名 URL，涉及 Dubbo RPC 调用。如果有恶意用户或客户端 bug 疯狂调这个接口：

1. MinIO 会收到大量预签名 URL 生成请求（虽然不是上传流量）
2. upload-service 的 Dubbo 线程池可能被打满
3. 网关内存和 CPU 被耗尽，影响其他接口

笔记的草稿、发布、评论接口虽然也重要，但它们本身处理较快（纯 DB/Cassandra 操作），且频次自然受限于用户行为。上传是更"重"的操作，应优先保护。

---

## 2. 详细实现分析

### 2.1 `@SentinelResource` 注解解析

```java
@SentinelResource(
    value = "upload-presigned",      // 资源名称（在 Sentinel Dashboard 中显示）
    blockHandler = "rateLimitFallback" // 限流触发的降级方法
)
```

**`blockHandler` 的规则**：
1. 必须是 `public` 方法
2. 返回值类型必须与原方法一致
3. 参数列表必须与原方法一致，**并在最后增加一个 `BlockException` 参数**
4. 必须与原方法在同一个类中

```java
// 原方法
public Result<PresignedUrlResponse> presignedUrl(String fileName, String contentType)

// blockHandler —— 参数匹配 + 额外 BlockException
public Result<?> rateLimitFallback(String fileName, String contentType, BlockException ex)
```

### 2.2 限流规则配置

当前项目没有配置持久化限流规则（无 Nacos datasource、无配置文件规则）。这意味着 Sentinel 使用**默认规则**，或者依赖 Sentinel Dashboard 动态下发规则。

**为什么用代码 fallback 而不是配置文件规则？**

- **代码内 blockHandler**：只需注解 + 方法，零配置，适合简单场景
- **配置文件规则**：需要 Sentinel 控制台或 Nacos 持久化，规则可动态修改，适合复杂场景
- 本项目选择了最简单的起步方式——先用 blockHandler 兜底，后续可按需接入 Dashboard

**如果要在 `application.yml` 中配置规则**（当前未配置）：

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8080   # Sentinel 控制台地址
        port: 8719                   # 与 Dashboard 通信的端口
      datasource:
        ds1:
          nacos:
            server-addr: localhost:8848
            data-id: sentinel-rules
            group-id: DEFAULT_GROUP
            data-type: json
            rule-type: flow
```

### 2.3 限流返回 HTTP 429

```java
public Result<?> rateLimitFallback(String fileName, String contentType, BlockException ex) {
    return Result.error(429, "Too many requests, please try again later");
}
```

**HTTP 429 Too Many Requests** 是标准的限流响应码：
- 客户端看到 429 后应该做 backoff 重试（指数退避）
- 与 503 Service Unavailable 不同：429 是"你的请求太快了"，503 是"服务暂时不可用"
- 返回自定义的 JSON 格式（`Result` 包装），便于客户端解析

### 2.4 Sentinel 的流量控制策略

Sentinel 支持多种限流策略（本项目可扩展）：

| 策略 | 原理 | 适用场景 |
|------|------|---------|
| **QPS 限流** | 超过每秒请求数阈值时限流 | 保护后端服务不被冲垮 |
| **并发线程数限流** | 超过同时处理请求数时限流 | 保护慢接口（如大文件上传） |
| **基于调用关系的限流** | 对调用链路中的特定环节限流 | 保护下游弱依赖 |
| **热点参数限流** | 对特定参数值（如特定 userId）限流 | 防止单个用户刷接口 |
| **系统保护规则** | 基于系统负载/CPU/RT 自动限流 | 全局兜底保护 |

---

## 3. Sentinel vs 其他限流方案

### 3.1 对比表格

| 维度 | Sentinel | Guava RateLimiter | Nginx limit_req | Redis 令牌桶 |
|------|----------|-------------------|-----------------|-------------|
| 架构层 | 应用层（Java） | 应用层（Java） | 网关层（Nginx） | 中间件层 |
| 分布式支持 | **支持**（集群模式） | 单机 | 单机（除非共享状态） | **支持** |
| 动态规则 | **支持**（Dashboard/Nacos） | 静态代码 | 配置文件 reload | 需自建管理端 |
| 熔断降级 | **支持**（配套 Sentinel） | 不支持 | 不支持 | 需额外实现 |
| 监控大盘 | **支持**（Dashboard） | 无 | 无 | 需 Grafana |
| 精准度 | 滑动窗口 | 令牌桶 | 漏桶 | 令牌桶 |
| Spring Cloud 集成 | **原生集成** | 需手动集成 | 外部部署 | 需手动集成 |

### 3.2 为什么选 Sentinel 而不是 Nginx 限流？

1. **Sentinel 是 Java 原生方案**：与 Spring Cloud 深度集成，一个注解即可
2. **更细粒度**：Nginx 只能按 URL/IP 限流，Sentinel 可以按参数值、调用链路限流
3. **配套降级**：Sentinel 自带熔断降级能力（@SentinelResource 的 fallback 属性）
4. **架构一致性**：全栈 Spring Cloud Alibaba，技术栈统一

**但 Nginx 限流仍然是有效的第一道防线**。理想的架构是：Nginx 限流（粗粒度，防 DDoS） → Sentinel 限流（细粒度，保护具体接口）。

### 3.3 与 Hystrix 的对比（历史视角）

| 维度 | Sentinel | Hystrix |
|------|----------|---------|
| 状态 | **活跃维护**（Alibaba） | **停止维护**（Netflix，2018） |
| 隔离策略 | 信号量隔离（默认） | 线程池隔离 + 信号量 |
| 熔断策略 | 慢调用比例、异常比例、异常数 | 异常比例 |
| 规则推送 | 控制台/Datasource | Archaius 配置 |
| 资源开销 | 较低（无额外线程池） | 较高（线程池隔离时） |

**为什么 Hystrix 用线程池隔离？**
- 线程池隔离可以完全隔离下游依赖，防止一个依赖的线程阻塞影响其他依赖
- 但线程池切换有性能开销，配置不当可能成为瓶颈

**为什么 Sentinel 不用线程池隔离？**
- Sentinel 基于信号量 + 滑动窗口统计，资源开销更低
- 对于大多数场景，信号量已经足够（线程不会长时间阻塞）

### 3.4 限流算法对比

限流工具的背后是不同的算法，理解算法才能理解限流的精准度、突发容忍度、内存开销等差异。

#### 3.4.1 固定窗口计数器（Fixed Window Counter）

**原理**：将时间线切分为固定大小的窗口（如 1 秒），每个窗口内维护一个计数器。请求到达时窗口计数器 +1，超过阈值则拒绝。窗口结束时计数器重置。

```
时间轴:  |  窗口1 (0~1s)  |  窗口2 (1~2s)  |
请求:    ····(4个)····   ·····(3个)····
阈值=5:  ✓✓✓✓            ✓✓✓
```

**致命缺陷——临界突发**：

```
阈值=5/秒
窗口1末尾(0.9~1.0s): 进来5个请求  → 全部放行
窗口2开头(1.0~1.1s): 进来5个请求  → 全部放行
实际 0.9s~1.1s 这一小段时间: 放行了10个请求 → 两倍阈值！
```

恶意流量只要打在窗口边界，就能稳定突破限流。**生产环境几乎不单独使用**。

| 维度 | 评价 |
|------|------|
| 精准度 | 低（临界问题严重） |
| 内存占用 | O(1)（只存一个计数） |
| 突发容忍 | 窗口级允许，跨窗口不可控 |
| 适用场景 | 仅适合对精准度要求不高的场景 |

---

#### 3.4.2 滑动窗口（Sliding Window）

**原理**：将窗口细分为 N 个等长子窗口（桶）。每个桶独立计数，窗口整体覆盖最近 N 个桶。时间推进时，新桶追加、最老桶丢弃，始终只统计最近一个完整窗口内的请求数。

```
窗口长度=1s，分为4个桶（每桶250ms）
时间轴:  [桶0][桶1][桶2][桶3] → [桶1][桶2][桶3][桶4] → ...
          ←   窗口范围   →      ←   新窗口范围  →
桶0过期被丢弃                                        桶4新创建
```

**Sentinel 的实现——LeapArray**：

```java
// Sentinel 核心数据结构（简化版）
class LeapArray {
    int sampleCount;      // 桶数量（如 1s 窗口分 2 个桶）
    long intervalInMs;    // 窗口总长度（默认 1000ms）
    Bucket[] buckets;     // 循环数组，长度 = sampleCount

    Bucket currentBucket() {
        // 1. 计算当前时间落在哪个桶
        // 2. 如果桶过期，重置复用（覆盖最老的数据）
        // 3. 返回当前桶
    }

    long pass() {
        // 统计窗口内所有桶的 pass 计数总和
    }
}
```

关键设计点：
- **循环数组**：物理上固定长度，逻辑上通过覆盖实现滑动
- **O(1) 统计**：计算 QPS 时只需遍历固定数量（sampleCount）的桶
- **无锁 CAS**：桶内计数用 `AtomicLong` 的 CAS 更新，避免锁竞争

| 维度 | 评价 |
|------|------|
| 精准度 | 高（细分桶越多越精准） |
| 内存占用 | O(N)（N = 桶数量，通常 2~60） |
| 突发容忍 | 窗口内均匀分布，无临界问题 |
| 适用场景 | **Sentinel 默认算法**，适合绝大多数限流场景 |

---

#### 3.4.3 漏桶（Leaky Bucket）

**原理**：请求先进入一个"桶"（队列），桶以**固定速率**漏出水滴（处理请求）。桶满则溢出（拒绝）。无论流入速率如何波动，流出速率始终恒定。

```
请求流入（任意速率）
  ↓  ↓  ↓  ↓  ↓  ↓
┌─────────────────┐
│   漏桶（队列）    │  ← 桶满 → 溢出拒绝
└────────┬────────┘
         ↓  固定速率流出（如 10 req/s）
```

**与滑动窗口的本质区别**：漏桶强制平滑输出，而滑动窗口只限制总量不限制节奏。

| 维度 | 评价 |
|------|------|
| 精准度 | 高（严格限速） |
| 内存占用 | 取决于队列大小 |
| 突发容忍 | **零容忍**——流量必须严格匀速流出 |
| 适用场景 | Nginx `limit_req`（流量整形）、API 网关保护慢后端 |

**为什么 Sentinel 不用漏桶？** 微服务场景通常不需要强制匀速——偶尔的突发如果是安全的总量内，应该被允许。漏桶的"削峰填谷"会让请求排队等待，增加延迟。

---

#### 3.4.4 令牌桶（Token Bucket）

**原理**：以固定速率向桶中放入令牌，桶满则丢弃多余令牌。请求到达时先取令牌，拿到令牌才能通过，拿不到则拒绝（或排队等待）。

```
令牌生成器 ──→ 每隔 100ms 放入 1 个令牌
              ┌─────────────────┐
              │   令牌桶（容量10） │  ← 桶满则丢弃多余令牌
              └────────┬────────┘
                       ↓  请求到达时取令牌
                  拿不到 → 拒绝/排队
```

**与漏桶的关键区别**：令牌桶允许**突发流量**。如果桶里积攒了 10 个令牌，突发 10 个请求可以一瞬间全部通过（前提是不超过桶容量）。漏桶不允许这种突发。

| 维度 | 评价 |
|------|------|
| 精准度 | 高（长期平均速率 = 令牌生成速率） |
| 内存占用 | O(1)（只存令牌数 + 上次填充时间） |
| 突发容忍 | **可控突发**——积攒的令牌可一次性消费 |
| 适用场景 | Guava `RateLimiter`（预热模式）、网关限流、AWS API Gateway |

---

#### 3.4.5 四种算法对比总表

| 维度 | 固定窗口 | 滑动窗口 | 漏桶 | 令牌桶 |
|------|---------|---------|------|--------|
| **核心思想** | 按周期清零计数 | 细粒度窗口累加 | 固定速率出队 | 固定速率放令牌 |
| **突发处理** | 临界可翻倍 | 平滑无临界 | 强制削峰，零突发 | 可控突发（桶容量） |
| **输出节奏** | 不限 | 不限 | 强制匀速 | 不限（但总量约束） |
| **内存复杂度** | O(1) | O(N) | O(队列长度) | O(1) |
| **时间精度** | 低 | 高（桶越多越高） | 高 | 高 |
| **典型实现** | — | Sentinel LeapArray | Nginx `limit_req` | Guava `RateLimiter` |

---

#### 3.4.6 面试要点

**Q: 滑动窗口 vs 令牌桶，怎么选？**

> - **不需要突发**（如保护 DB 连接池，超过 50 并发就崩）→ 滑动窗口，直接用并发线程数限流
> - **需要允许突发**（如抢购秒杀，10 秒内允许 1000 个请求但不允许 1 秒冲 500）→ 令牌桶，设置桶容量 = 可接受的最大突发
> - Sentinel 的滑动窗口已经覆盖 90% 场景，令牌桶在 Guava RateLimiter + 网关层更常见

**Q: 细说 Sentinel LeapArray 的桶覆盖机制（为什么 O(1)）？**

> LeapArray 用循环数组 + 时间戳来判断桶是否过期：
> ```java
> int idx = (currentTimeMs / bucketLengthMs) % sampleCount;
> Bucket bucket = buckets[idx];
> if (bucket.timestamp != expectedTimestamp) {
>     bucket.reset(expectedTimestamp);  // 覆盖旧数据
> }
> ```
> 关键点：
> 1. 数组长度固定（sampleCount），不会动态扩容
> 2. 通过取模定位桶，O(1) 寻址
> 3. 覆盖而不是新建，复用对象减少 GC
> 4. 统计 QPS 时遍历 sampleCount 个桶取 `pass()` 总和，O(sampleCount) = O(1)

**Q: 如果让你实现一个分布式令牌桶，怎么做？**

> 核心难点：令牌的原子分配。两种方案：
> 1. **Redis Lua 脚本**：
> ```lua
> -- key: rate_limit_key, capacity: 桶容量, rate: 生成速率/秒
> local tokens = redis.call('GET', key) or capacity
> if tonumber(tokens) > 0 then
>     redis.call('DECR', key)
>     return 1  -- 放行
> else
>     return 0  -- 限流
> end
> ```
> 配合定时任务或 `EXPIRE` 做令牌补充。
>
> 2. **Sentinel 集群模式**：Token Server 维护全局计数，Token Client 每次请求前向 Server 申请令牌，减少 Redis 依赖。

---

## 4. 实现难点

### 4.1 blockHandler 与原方法参数签名匹配

最容易出错的是 blockHandler 方法签名不匹配：

```java
// ❌ 错误——缺少参数或参数类型不匹配
public Result<?> rateLimitFallback(BlockException ex) { ... }

// ❌ 错误——返回值类型不匹配
public String rateLimitFallback(String fileName, String contentType, BlockException ex) { ... }

// ✅ 正确——参数列表完全匹配 + 额外 BlockException
public Result<?> rateLimitFallback(String fileName, String contentType, BlockException ex) { ... }
```

参数名可以不同，但类型和顺序必须一致。否则 Sentinel 找不到 blockHandler，会直接抛出 `BlockException` 给全局异常处理器（默认返回 500）。

### 4.2 Sentinel 与 Dubbo 的兼容性

Spring Cloud Alibaba 与 Dubbo 的 Sentinel 支持有两种路径：
1. **Sentinel 适配 HTTP 层**（`@SentinelResource`，本项目使用）——对 Dubbo 调用也有效，因为入口是 HTTP
2. **Sentinel 适配 Dubbo 层**（`dubbo.provider.filter=sentinel`）——对 Dubbo 调用直接限流

本项目用的是方案 1，流控发生在网关的 HTTP 层，在进 Dubbo 调用之前就拦截了。

### 4.3 没有 Dashboard 的局限性

当前项目没有配置 Sentinel Dashboard，限流规则只能靠代码中的 `blockHandler` 兜底。这带来一些问题：

- **规则不能动态修改**：改限流阈值需要改代码、重新部署
- **没有可视化监控**：看不到实时 QPS、通过/拒绝数量
- **没有集群流控**：每个 gateway 实例独立计数，不是全局限流

**改进方案**：
1. 接入 Sentinel Dashboard（加一个 `transport.dashboard` 配置即可）
2. 用 Nacos 持久化规则（动态配置，Dashboard 修改后推送到所有实例）

---

## 5. 面试准备

### 5.1 高频问题

**Q: `blockHandler` 和 `fallback` 有什么区别？**
> - `blockHandler`：处理 Sentinel **限流/熔断阻断**的异常（`BlockException`），即流量被规则拒绝时
> - `fallback`：处理**业务异常**（如 Dubbo 调用失败、DB 超时），即请求通过了限流但业务执行出错了
> - 两者可以同时配置，先判断是否 BlockException → 进入 blockHandler，其他异常 → 进入 fallback

**Q: Sentinel 的滑动窗口是怎么实现的？**
> Sentinel 使用 LeapArray（跳跃数组）实现滑动窗口统计：
> 1. 将时间窗口等分为 N 个桶（如 1 秒分成 2 个 500ms 的桶）
> 2. 每个桶统计该时间段内的 pass/block/error 计数
> 3. 当前时间落在哪个桶就写入哪个桶
> 4. 计算 QPS 时，取时间窗口内所有桶的合计
> 5. 桶的数组是循环使用的——新的桶覆盖最老的桶
> 6. 时间复杂度 O(1)，空间复杂度 O(N)

**Q: 如何实现全局限流（多实例共享限流配额）？**
> 1. Sentinel 提供了集群流控模式：一个 Token Server + 多个 Token Client
> 2. Token Server 负责维护全局的 QPS 计数
> 3. Token Client 每次请求向 Server 申请 Token
> 4. 也可以自己做：用 Redis 的 INCR + EXPIRE 实现分布式计数器
> 5. 全局限流是有性能代价的（额外的网络调用），只在必要时启用

### 5.2 进阶讨论

**Q: 限流和熔断有什么区别？**
> - **限流（Rate Limiting）**：主动拒绝超出容量的请求，保护系统不被打垮。像餐厅门口排队——你知道里面坐满了就不让你进。
> - **熔断（Circuit Breaking）**：被动发现下游依赖不可用后，暂时放弃调用该依赖，给它恢复时间。像电路的保险丝——短路时自动断开。
> - Sentinel 两者都支持：`@SentinelResource` 的 `blockHandler` 做限流，`DegradeRule` 做熔断。

**Q: 为什么返回 429 而不是 503？**
> 429 是 "Too Many Requests"，语义明确告诉客户端"你请求太快了"。503 是 "Service Unavailable"，表示"服务有问题"。语义不同，客户端看到 503 可能会触发告警，而看到 429 只会做退避重试。这是一个运维友好型设计。

**Q: 如果要按用户 ID 限流（每个用户每分钟最多 10 次上传），怎么做？**
> 使用 Sentinel 的热点参数限流：
> ```java
> @GetMapping("/presigned")
> @SentinelResource(value = "upload-presigned",
>     blockHandler = "rateLimitFallback")
> public Result<PresignedUrlResponse> presignedUrl(
>         @RequestParam String fileName,
>         @RequestParam String contentType,
>         @RequestParam Long userId) { // 添加 userId 参数
> ```
> 然后在 Sentinel Dashboard 或 Nacos 中配置：资源 `upload-presigned`，参数索引 2（第3个参数），阈值 10/min。


**滑动窗口和令牌桶的区别**

特性	滑动窗口	令牌桶
突发处理	较生硬。如果瞬间来了 100 个，窗口立刻占满，剩下的请求全部被拒。	较优雅。利用积攒的令牌快速处理，处理完后再恢复平滑速率。
流量分布	窗口内可能前 10ms 跑完 100 个，后 990ms 全拒绝。	令牌匀速产生，能引导流量分布得更均匀。
内存开销	较高（需要记录多个小格子的计数，格子越多开销越大）。	极低（只需存储两个变量）。
实现难度	中等（涉及窗口滑动逻辑）。	低（简单的数学计算）。
---

## 6. 关键代码位置

| 文件 | 作用 |
|------|------|
| `gateway/controller/UploadController.java:23-34` | @SentinelResource 注解 + blockHandler 定义 |
