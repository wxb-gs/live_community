# Leaf 雪花算法分布式 ID

## 1. 功能概述

### 1.1 什么是雪花算法？

Snowflake（雪花算法）是 Twitter 开源的分布式 ID 生成算法。它完全在内存中计算，**无需任何数据库或外部依赖**，生成 64 位的 Long 型 ID。

### 1.2 64 位结构

```
 1 bit    41 bits                       5 bits      5 bits      12 bits
┌─────┬─────────────────────────────┬───────────┬───────────┬────────────┐
│ 未用 │ 时间戳 (毫秒，从 epoch 算起) │ 数据中心ID │ 机器ID    │ 序列号      │
│  0  │ 41 bits = ~69 年            │ 5b = 32   │ 5b = 32   │ 12b = 4096 │
└─────┴─────────────────────────────┴───────────┴───────────┴────────────┘
       ← 高位                                                    → 低位
```

| 段 | 位数 | 最大值 | 含义 |
|----|------|--------|------|
| 符号位 | 1 bit | — | 始终为 0（保证 ID 为正数） |
| 时间戳 | 41 bits | ~2.2×10¹² | 从自定义 EPOCH 开始，可支撑约 69 年 |
| 数据中心 ID | 5 bits | 31 | 支持 32 个数据中心（0~31） |
| 机器 ID | 5 bits | 31 | 每个数据中心支持 32 台机器（0~31） |
| 序列号 | 12 bits | 4095 | 同一毫秒内最多 4096 个 ID |

### 1.3 与号段模式的对比

| 维度 | 号段模式 | 雪花算法 |
|------|---------|---------|
| 外部依赖 | **依赖 MySQL** | **零依赖**（纯内存计算） |
| QPS 上限 | 极高（异步预加载） | 极高（每毫秒 4096，即 409.6 万/秒） |
| ID 有序性 | 趋势递增 | 趋势递增（同一实例内严格递增） |
| 时钟依赖 | 不敏感 | **强依赖时钟，回拨会导致问题** |
| ID 长度 | 取决于 step 起始值 | 固定 64 位 |
| 部署复杂度 | 需要 MySQL + leaf_alloc 表 | 只需配置 workerId/datacenterId |
| 适用场景 | 需要人为可控步长的场景 | 对 DB 零依赖的高可用场景 |

---

## 2. 详细实现分析

### 2.1 自定义 EPOCH

```java
private static final long EPOCH = 1700000000000L;  // 2023-11-14 22:13:20 UTC
```

**为什么不用 Twitter 原版的 EPOCH（1288834974657L = 2010-11-04）？**
1. 原版 EPOCH 距今已很久，可用年限减少
2. 自定义 EPOCH 可以对齐系统上线时间，最大化可用年限
3. 用近期的 EPOCH 可以让生成的 ID 更短（虽然都是 64 位，但高位时间戳更小）

1700000000000L 对应 2023-11-14，加上 41 位时间戳容量（~69 年），可以支撑到约 2092 年。

### 2.2 位运算实现

```java
// 位移常量——每个段左移到正确的位置
private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;                        // 12
private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;   // 17
private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS; // 22

// ID 组装：OR 运算将各部分合并
return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
        | (datacenterId << DATACENTER_ID_SHIFT)
        | (workerId << WORKER_ID_SHIFT)
        | sequence;
```

**为什么用位移 + OR 而不是字符串拼接？**
位移操作是 CPU 原语，单条指令即可完成，而字符串操作用到了堆内存分配和 GC。对于每秒百万级的 ID 生成，性能差异巨大。

### 2.3 同一毫秒内的序列号处理

```java
if (timestamp == lastTimestamp) {
    sequence = (sequence + 1) & SEQUENCE_MASK;  // 序列号 +1，用掩码取模
    if (sequence == 0) {                          // 本毫秒序列号耗尽
        timestamp = waitNextMillis(lastTimestamp); // 阻塞等待下一毫秒
    }
} else {
    sequence = 0L;  // 新的毫秒，序列号重置
}
```

**`(sequence + 1) & SEQUENCE_MASK` 的作用**：

```
SEQUENCE_MASK = ~(-1L << 12) = 0b111111111111 = 4095

sequence=4095: (4095 + 1) & 4095 = 4096 & 4095 = 0  ← 溢出回零，触发 waitNextMillis
sequence=1000: (1000 + 1) & 4095 = 1001              ← 正常递增
```

**QPS 计算**：12 位序列号 = 每毫秒最多 4096 个 ID → 理论上限 409.6 万 QPS。实际受 `synchronized` 关键字限制，单实例约几十万 QPS。

### 2.4 时钟回拨处理

这是雪花算法最棘手的难题，本项目的处理方案：

```java
if (timestamp < lastTimestamp) {           // 时钟回拨了！
    long offset = lastTimestamp - timestamp; // 回拨了多少毫秒
    if (offset <= 5) {                       // 回拨 ≤ 5ms：等待追上
        wait(offset << 1);                   // 等待回拨量的 2 倍时间
        timestamp = currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(...);  // 还没追上，拒绝服务
        }
    } else {                                 // 回拨 > 5ms：直接拒绝
        throw new RuntimeException(...);
    }
}
```

**三种时钟回拨处理策略对比**：

| 策略 | 原理 | 优点 | 缺点 |
|------|------|------|------|
| **等待（本项目 ≤5ms）** | 短暂回拨时等待时钟追上 | 零配置，不影响 ID 正确性 | 回拨量大时等待时间不可接受 |
| 直接拒绝（本项目 >5ms） | 抛异常，服务降级 | 不产生错误 ID，最安全 | 服务暂时不可用 |
| 备用 workerId | 检测到回拨后切换备用 workerId | 服务不中断 | 需要运维配合，实现复杂 |
| 记录历史序列号 | 回拨后使用该毫秒的上次最大序列号继续递增 | 服务不中断 | 可能产生不连续的 ID |

**为什么要分 5ms 的界限？**
- NTP 时钟同步通常在几毫秒内完成，5ms 是经验值
- 超过 5ms 说明可能是时钟被人为修改或出现严重问题，等待不可靠
- 等待时间是 `offset << 1`（2 倍），给一些余量

### 2.5 `synchronized` 关键字

```java
public synchronized long nextId() {
    // ...
}
```

**为什么整个方法用 `synchronized`？**
同一个实例内，时间戳检查 + 序列号递增必须是原子操作，否则两个线程可能在同一毫秒内得到相同的序列号。这是最简单的正确性保证。更精细的做法是用 CAS 循环，但 synchronized 对几十万 QPS 的场景足够了。

---

## 3. 实现难点

### 3.1 Worker ID 和 Datacenter ID 的分配

```java
public SnowflakeIdGenerator(
        @Value("${leaf.snowflake.worker-id}") long workerId,
        @Value("${leaf.snowflake.datacenter-id}") long datacenterId)
```

当前实现是**静态配置**，需要运维人员手动为每个实例分配不同的 workerId。这在实际部署中有两个问题：
1. 扩容时需要人工分配新 workerId，容易出错
2. workerId 范围 0~31，最多 32 个实例（5 位限制）

**改进方案**：
- **ZooKeeper 自动注册**：实例启动时向 ZK 申请一个临时顺序节点，用节点序号作为 workerId
- **数据库自增**：用 DB 记录已分配的最大 workerId，与新实例的心跳绑定
- **K8s StatefulSet**：用 Pod 序号作为 workerId

### 3.2 时间戳精度

```java
private long currentTimeMillis() {
    return System.currentTimeMillis();
}
```

`System.currentTimeMillis()` 的精度和性能：
- Windows 下精度约 10~15ms（较粗）
- Linux 下精度约 1ms
- 精度如果不够，同一毫秒的序列号会快速耗尽，触发 `waitNextMillis`

**为什么不用 `System.nanoTime()`？**
`nanoTime()` 是单调时钟（不受系统时间调整影响），但它的起点不固定（JVM 启动时的某个值），不能用于计算绝对时间戳。雪花算法需要绝对时间戳来做趋势递增。

### 3.3 与号段模式的互补

本项目**同时实现了两种模式**，通过 `LeafRpcService` 统一暴露：

```java
public interface LeafRpcService {
    IdResponse generateSegmentId(String bizKey);   // 号段：按业务 key 隔离
    IdResponse generateSnowflakeId();              // 雪花：全局唯一，无业务隔离
}
```

**什么时候用号段，什么时候用雪花？**
- **号段**：业务主键（noteId, commentId），需要按 biz_tag 隔离，且 step 可控
- **雪花**：全局唯一性要求更高、不依赖 DB 的场景（如分布式事务 ID、日志链路 traceId）

---

## 4. 面试准备

### 4.1 高频问题

**Q: 雪花算法的 ID 在数据库索引中有什么优势？**
> 趋势递增的 ID 对 InnoDB B+Tree 非常友好：
> 1. 新数据总是追加到索引最右侧，不会产生页分裂
> 2. 相比 UUID，减少了随机 IO，插入性能提升数倍
> 3. 相比自增 ID，可以在分布式环境下生成而无需等待 DB
> 注意是**趋势递增**而非全局严格递增——不同实例在同一毫秒可能交叉生成 ID，但整体趋势是递增的。

**Q: 为什么第一位总是 0？**
> Long 在 Java 中是有符号类型，最高位 1 表示负数。将第一位固定为 0 确保所有生成的 ID 都是正数，在数据库中作为 BIGINT UNSIGNED 使用更自然，也避免了一些框架对负 ID 的特殊处理。

**Q: 如果时间戳回拨了怎么办？**
> 1. **≤5ms**：`wait(offset*2)` 等待追上后继续
> 2. **>5ms**：抛异常拒绝服务，触发降级
> 3. 生产环境应配合 NTP 平滑校时（`ntpd -x` 或 `chrony`），避免回拨
> 4. 也可以设计备用 workerId 方案，回拨时自动切换

**Q: 雪花算法和号段模式怎么选择？**
> - 对 DB 零容忍 → 雪花（完全无 DB 依赖）
> - 需要按业务线分配独立 ID 序列 → 号段（靠 biz_tag 区分）
> - 需要高性能 + 趋势递增即可 → 两者都可以
> - 系统有时钟回拨风险 → 号段（不依赖时钟）
> 实际上美团 Leaf 推荐号段模式为首选，雪花作为补充。

### 4.2 进阶讨论

**Q: 为什么不能把序列号增加到 16 位、机器 ID 减少到 1 位？**
> 这是对系统需求的权衡：
> - 12 位序列号 = 每毫秒 4096 个 ID，对于单实例已经很大
> - 5 位机器 ID = 32 台机器，对于一般业务足够
> - 如果机器多但每台并发低，可以调大机器位、缩小序列号位
> - 这是**可配置的分段设计**，不同场景可以有不同的分配

**Q: 如果未来需要更多机器怎么办？**
> 方案 1：借用 datacenterId 的 5 位，合并为 10 位机器 ID（支持 1024 台）
> 方案 2：减少序列号位，例如 12→10（每毫秒 1024），增加机器位 5→7（128 台）
> 方案 3：混合使用号段模式，每台机器用号段代替雪花

---

## 5. 关键代码位置

| 文件 | 作用 |
|------|------|
| `leaf-service/snowflake/SnowflakeIdGenerator.java` | 雪花算法核心：位运算组装、时钟回拨处理、序列号递增 |
| `leaf-service/rpc/LeafRpcServiceImpl.java` | Dubbo RPC：暴露 generateSnowflakeId() |
| `leaf-service/controller/LeafController.java` | REST：GET /api/leaf/snowflake |
| `application.yml` | worker-id / datacenter-id 配置 |
