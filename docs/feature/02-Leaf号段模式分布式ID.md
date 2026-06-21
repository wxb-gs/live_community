# Leaf 号段模式分布式 ID

## 1. 功能概述

### 1.1 什么是号段模式？

号段模式（Segment Mode）是美团 Leaf 分布式 ID 生成器的核心算法之一。其思想是：**从数据库批量取一段 ID 号段（如 1000 个），缓存在本地内存中，业务每次从这段缓存中取一个 ID，取完后重新申请下一个号段**。

```
数据库 leaf_alloc 表                     应用内存
┌────────┬────────┬──────┐           SegmentBuffer (biz_key="note")
│biz_tag │ max_id │ step │           ┌─────────────────────────┐
├────────┼────────┼──────┤           │ segments[0] (current)   │
│ note   │ 1001   │ 1000 │           │  value=1, max=1001      │
│ comment│ 1001   │ 1000 │           │  ← 当前从这里取 ID      │
└────────┴────────┴──────┘           │ segments[1] (备用)      │
      ↑                              │  value=1001, max=2001   │
UPDATE leaf_alloc                    │  ← 提前异步加载好        │
SET max_id = max_id + step           └─────────────────────────┘
WHERE biz_tag = 'note'
```

### 1.2 为什么需要号段模式？

| 方案 | 每次获取ID | QPS上限 | 数据库压力 | 优缺点 |
|------|-----------|---------|-----------|--------|
| 数据库自增 | 1次DB写入 | ~500-1000 | 极高 | 简单但性能瓶颈严重 |
| UUID | 0次DB | 无限 | 无 | 无序，索引不友好 |
| Snowflake | 0次DB | 极高 | 无 | 依赖机器时钟 |
| **号段模式** | 1次DB/N个ID | 极高 | 极低 | 趋势递增，QPS与DB解耦 |
| Redis 原子递增 | 1次Redis | ~10万 | 中等 | 依赖 Redis 持久化 |

号段模式的核心优势：**用极低的数据库交互次数支撑极高的 ID 获取 QPS**。step=1000 时，DB 的压力是直接自增的 1/1000。

### 1.3 本项目中的使用场景

```java
// NoteService.java:45 - 创建笔记时
IdResponse idResp = leafRpcService.generateSegmentId("note");
long noteId = idResp.getId();

// NoteService.java:126 - 添加评论时
IdResponse idResp = leafRpcService.generateSegmentId("comment");
long commentId = idResp.getId();
```

**biz_tag 的设计**：不同业务线使用不同的 tag，互不干扰，各自维护号段。`note` 和 `comment` 的 ID 序列完全独立。

---

## 2. 详细实现分析

### 2.1 数据库表设计

```sql
CREATE TABLE `leaf_alloc` (
    `biz_tag` VARCHAR(128) NOT NULL DEFAULT '',
    `max_id` BIGINT NOT NULL DEFAULT 1,
    `step` INT NOT NULL,
    `description` VARCHAR(256) DEFAULT NULL,
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`biz_tag`)
) ENGINE=InnoDB;
```

**关键字段**：
- `biz_tag`：业务标识，主键。不同的业务线对应不同行
- `max_id`：当前已分配的最大 ID。初始值为 1，每次分配号段时增加 step
- `step`：每次分配的号段长度。例：step=1000 时，一次从 DB 拿 1000 个 ID
- `update_time`：记录最后更新时间，可用于监控哪些 biz_tag 长期没有请求

**核心 SQL**（LeafAllocDao.java）：

```sql
-- 分配号段：原子性地将 max_id 增加 step
UPDATE leaf_alloc SET max_id = max_id + step WHERE biz_tag = ?
-- 查询最新的 max_id 和 step
SELECT biz_tag, max_id, step FROM leaf_alloc WHERE biz_tag = ?
```

**为什么先 UPDATE 再 SELECT，而不是反过来？**
先 UPDATE 保证原子性：在高并发时，如果先 SELECT 再 UPDATE，两个请求可能读到相同的 max_id。先 UPDATE 利用 MySQL 行锁，保证每次分到的号段不重复、不交叉。

### 2.2 双 Buffer 机制

这是 Leaf 号段模式最精妙的设计——**两个内存 Segment 交替使用，异步预加载，实现无阻塞的 ID 获取**。

**数据结构**（SegmentBuffer.java）：

```java
public class SegmentBuffer {
    private final Segment[] segments = {new Segment(), new Segment()};  // 两个号段
    private volatile int currentPos;   // 当前使用的 segment 下标（0 或 1）
    private volatile boolean nextReady; // 备用 segment 是否已加载好
    private final ReadWriteLock lock;   // 读写锁，切换时用写锁，读取时用读锁
    private final AtomicBoolean threadRunning; // 是否已有加载线程在运行
}
```

**单个 Segment 的数据结构**（Segment.java）：

```java
public class Segment {
    private final AtomicLong value;  // 当前已分配到的值，用 AtomicLong 保证线程安全
    private volatile long max;       // 当前号段的最大值（不包含）
    private volatile int step;       // 号段大小
}
```

### 2.3 双 Buffer 工作流程

```
初始态（null → 初始化）:
  segments[0] = {value:1, max:1001}     ← currentPos=0, 从这里取ID
  segments[1] = {value:0, max:0}        ← 未加载

耗尽到 90%（value 达到 max*0.9）时触发异步加载:
  segments[0] = {value:900, max:1001}   ← current, 还在用
  segments[1]                                ← 异步线程去 DB 加载
  加载完后: segments[1] = {value:1001, max:2001}
  nextReady = true                          ← 标记已就绪

segments[0] 耗尽（value >= max）:
  切换: currentPos=1                         ← 原子切换
  nextReady = false
  现在 segments[1] 是当前，segments[0] 等待下次预加载
```

**核心代码解读**（SegmentIdGenerator.java）：

```java
// 50-77行：从 buffer 获取 ID 的核心循环
private long getFromBuffer(String bizKey, SegmentBuffer buffer) {
    while (true) {
        buffer.getLock().readLock().lock();  // 读锁，允许并发读取
        try {
            Segment segment = buffer.getCurrent();
            // 条件：备用未就绪 && 剩余不足90% && 没有其他线程在加载
            if (!buffer.isNextReady()
                    && segment.getIdle() < 0.9 * segment.getStep()
                    && buffer.getThreadRunning().compareAndSet(false, true)) {
                // 提交异步任务去加载下一个号段
                scheduler.execute(() -> {
                    try {
                        loadNextSegment(bizKey, buffer);
                    } finally {
                        buffer.getThreadRunning().set(false);
                    }
                });
            }
            // 原子递增并获取 ID
            long value = segment.getAndIncrement();
            if (value < segment.getMax()) {
                return value;  // 正常情况：直接返回
            }
            // 号段耗尽，进入切换逻辑
        } finally {
            buffer.getLock().readLock().unlock();
        }
        waitAndSwitch(buffer, bizKey);  // 阻塞等待号段切换
    }
}
```

### 2.4 并发安全设计

| 机制 | 用途 |
|------|------|
| `AtomicLong value` (in Segment) | 多线程同时取 ID 时的 CAS 无锁递增 |
| `ReentrantReadWriteLock` (in SegmentBuffer) | 读取号段用读锁（多线程并发），切换号段用写锁（独占） |
| `AtomicBoolean threadRunning` | 保证只有一个线程去 DB 加载号段（防止重复加载） |
| `volatile currentPos / nextReady` | 跨线程可见性，切换立即可见 |
| `synchronized` (初始化时) | DCL（双重检查锁）保证只初始化一次 |

### 2.5 异步预加载时机

**为什么是 90%（0.9 * step）？**

在 90% 消耗时触发预加载，留出 10% 的缓冲。这个值的选择需要平衡：
- 太低（如 50%）：频繁触发，浪费预加载线程
- 太高（如 99%）：ID 消耗速度可能快于 DB 加载速度，导致阻塞等待
- 90% 是经验值，给 DB 加载留了 (step*0.1) 个 ID 的时间窗口

```java
if (!buffer.isNextReady()
        && segment.getIdle() < 0.9 * segment.getStep()
        && buffer.getThreadRunning().compareAndSet(false, true))
```

---

## 3. 实现难点

### 3.1 号段切换时的阻塞等待

```java
private void waitAndSwitch(SegmentBuffer buffer, String bizKey) {
    buffer.getLock().writeLock().lock();  // 写锁，独占
    try {
        Segment segment = buffer.getCurrent();
        long value = segment.getValue();
        if (value < segment.getMax()) {
            return;  // 被其他线程先切换好了
        }
        if (buffer.isNextReady()) {
            buffer.setCurrentPos(buffer.switchPos());  // 切换到备用号段
            buffer.setNextReady(false);
        } else {
            // 最坏情况：两个号段都用完了，同步等待 DB 加载
            updateSegmentFromDb(bizKey, segment);
        }
    } finally {
        buffer.getLock().writeLock().unlock();
    }
}
```

**为什么需要双重检查？**
- 第一次检查在 `getFromBuffer` 的读锁中（`value < segment.getMax()`）
- 第二次检查在 `waitAndSwitch` 的写锁中
- 两个检查之间，可能有其他线程已经完成了切换

**什么时候会走到 "两个号段都用完" 的情况？**
1. 系统刚启动，第一个号段还没加载第二个
2. DB 出现慢查询，预加载未能及时完成
3. ID 消耗速度极快（如突发的秒杀流量）

### 3.2 MySQL 连接池与事务

```java
@Transactional
public SegmentAllocResult updateAndGet(String bizKey) {
    int rows = jdbcTemplate.update(UPDATE_SQL, bizKey);  // 先更新
    // ...
    Map<String, Object> row = jdbcTemplate.queryForMap(QUERY_SQL, bizKey); // 再查询
}
```

**为什么需要 `@Transactional`？**
- `UPDATE` 和 `SELECT` 必须在同一个事务中
- MySQL 默认隔离级别 REPEATABLE-READ 下，如果不用事务，两次操作之间可能被另一个事务插入
- 行锁在事务提交后才释放

### 3.3 step 大小的权衡

| step | 数据库压力 | 内存浪费（服务重启丢失） | ID 连续性 | 推荐场景 |
|------|-----------|-------------------------|---------|---------|
| 100 | 中等 | 少（最多浪费 100） | 较连续 | 低 QPS 场景 |
| 1000 | 低 | 中等（最多浪费 1000） | 有跳跃 | 一般场景（本项目） |
| 10000 | 极低 | 多（最多浪费 10000） | 明显跳跃 | 超高 QPS 场景 |
| 100000 | 极低 | 很多 | 跳跃大 | 日志/埋点等对连续性无要求的场景 |

**项目使用 step=1000**：INSERT 语句中的 `('note', 1, 1000, ...)`，适合大多数中等流量场景。

---

## 4. 面试准备

### 4.1 高频问题

**Q: 号段模式 vs 数据库自增主键，优势在哪？**
> 数据库自增（`AUTO_INCREMENT`）每次插入都要等 DB 返回 ID，且所有实例共享同一个递增序列。号段模式：
> 1. 每 N 次 ID 获取才访问一次 DB，DB 压力降为 1/N
> 2. 多实例各自持有自己的号段，无竞争
> 3. 应用启动时可以从本地缓存取 ID，不依赖 DB 实时可用
> 但注意：号段返回的 ID 是趋势递增而非全局单调递增（不同实例间可能交叉，因为实例 A 取 [1,1001)，实例 B 取 [1001,2001)，最终生成的 ID 可能 A:999, B:1001，实际顺序取决于谁先发号）

**Q: 服务重启会浪费 ID 吗？**
> 会。如果 step=1000，重启时最多浪费 1000 个 ID（当前缓存的段内剩余 ID 全部丢弃）。对于 64 位 BIGINT（最大 9223372036854775807），浪费 1000 个完全可以接受。如果真的在意浪费，可以减小 step。不过大部分分布式 ID 场景不要求 ID 绝对连续。

**Q: 双 buffer 为什么用 ReentrantReadWriteLock 而不是 synchronized？**
> 读多写少场景。获取 ID 是极高频的读操作（允许并发），号段切换是极低频的写操作（独占）。读写锁让 N 个获取 ID 的线程并发执行，只在号段切换时阻塞。如果全部用 synchronized，N 个线程会串行化，性能急剧下降。

### 4.2 进阶讨论

**Q: 多个 leaf-service 实例会重复发号吗？**
> 不会。因为每次 UPDATE `max_id = max_id + step` 是在 MySQL 行锁保护下原子执行的，无论多少实例，每个号段都是唯一的区间。实例 A 拿到 [1,1001)，实例 B 拿到 [1001,2001)，不会重叠。

**Q: 如果 DB 挂了怎么办？**
> 号段模式有一个容灾窗口：DB 挂掉后，已经在内存中的号段还能继续发号（最多发完当前号段 + 已预加载的备用号段共 2*step 个 ID）。对于 step=1000，这是一个约 2000 个 ID 的缓冲。但缓冲用完后就无法发号了。这也是号段比 Snowflake 脆弱的地方——Snowflake 完全不依赖 DB。

**Q: 号段模式的 ID 是全局有序的吗？**
> 不是严格全局有序（不同实例拿到的号段范围不同），但**趋势递增**。对于 InnoDB B+Tree 索引来说，趋势递增和严格有序的效果几乎一样——都能有效避免页分裂。这对大多数业务足够了。

---

## 5. 关键代码位置

| 文件 | 作用 |
|------|------|
| `leaf-service/segment/SegmentIdGenerator.java` | 核心：号段获取、双 Buffer 切换、异步预加载 |
| `leaf-service/segment/LeafAllocDao.java` | 数据库操作：事务性更新 max_id + 查询 |
| `leaf-service/segment/model/Segment.java` | 号段模型：AtomicLong + max + step |
| `leaf-service/segment/model/SegmentBuffer.java` | 双 Buffer：读写锁、切换逻辑、状态标志 |
| `leaf-service/rpc/LeafRpcServiceImpl.java` | Dubbo RPC 暴露：segmentId / snowflakeId |
| `leaf-service/controller/LeafController.java` | REST 直连接口 |
| `sql/init_leaf.sql` | 建表 + 初始化 leaf_alloc 数据 |
