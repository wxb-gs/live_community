# Kafka 入门指南

## 1. 什么是 Kafka

Apache Kafka 是一个**分布式流处理平台**，由 LinkedIn 开发后捐赠给 Apache 基金会。它提供高吞吐、低延迟、可持久化的消息传递能力。

### 核心特征

| 特征 | 说明 |
|------|------|
| **高吞吐** | 单机轻松处理 10 万 TPS，集群可达百万级 |
| **持久化** | 消息写入磁盘（顺序 I/O），不因消费完毕而删除 |
| **水平扩展** | 增加 Broker 即可线性扩展吞吐量和存储 |
| **多消费者** | 同一消息被多个消费者组独立消费（发布/订阅） |
| **顺序保证** | 同一分区内消息严格有序 |
| **回溯消费** | 可按 offset 重置到任意位置重新消费 |

---

## 2. Kafka vs 传统消息队列

| | Kafka | RabbitMQ | RocketMQ |
|---|---|---|---|
| **吞吐量** | 百万级 TPS | 万级 TPS | 十万级 TPS |
| **消息持久化** | ✅ 默认持久化，磁盘顺序写 | 可选持久化 | ✅ 默认持久化 |
| **消息回溯** | ✅ 按 offset 任意回溯 | ❌ 消费完即删 | ✅ 按时间/offset 回溯 |
| **顺序性** | 分区内有序 | 队列内有序 | 队列内有序 |
| **协议** | 自定义二进制协议 | AMQP | 自定义（类 JMS） |
| **事务消息** | ✅（幂等 + 事务） | ❌ | ✅（半消息） |
| **延时消息** | ❌ 需自行实现 | ✅ 插件 | ✅ 内置 |
| **运维复杂度** | 中（依赖 ZK/KRaft） | 低 | 中 |

> **选择 Kafka 的场景**：大数据管道、日志收集、事件溯源、流处理、Canal binlog 同步。
> **不选 Kafka 的场景**：简单的任务队列（RabbitMQ 更轻量）、需要延时消息（RocketMQ 更好）。

---

## 3. 核心概念

### 3.1 架构全景

```
┌──────────────────────────────────────────────────────────────┐
│                       Kafka Cluster                          │
│                                                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                   │
│  │ Broker 1 │  │ Broker 2 │  │ Broker 3 │                   │
│  │          │  │          │  │          │                   │
│  │ 分区0(R) │  │ 分区0(L) │  │ 分区1(R) │  ←─ 副本分布      │
│  │ 分区1(L) │  │ 分区2(R) │  │ 分区2(L) │                   │
│  └──────────┘  └──────────┘  └──────────┘                   │
│                                                              │
│   Topic: "search.note.sync"  (3 分区, 2 副本)                │
└──────────────────────────────────────────────────────────────┘
     ▲                                        │
     │  写入 (Producer)                        │  消费 (Consumer)
     │                                        ▼
┌─────────┐  ┌─────────┐            ┌──────────────┐
│  Canal  │  │  App A  │            │ ES Consumer  │  ← Consumer Group A
└─────────┘  └─────────┘            └──────────────┘
                                     ┌──────────────┐
                                     │  Data Analyst│  ← Consumer Group B (独立消费)
                                     └──────────────┘
```

### 3.2 Broker（代理节点）

一个 Kafka 服务器实例。多个 Broker 组成 Cluster。

| 角色 | 说明 |
|------|------|
| **Controller** | 集群中唯一的 Controller Broker，管理分区上下线、副本选举 |
| **Coordinator** | 按 Consumer Group 分配，管理消费者组的 offset 提交和 rebalance |

### 3.3 Topic（主题）

消息的逻辑分类，相当于 RabbitMQ 的 **Exchange + Queue**。Topic 可以有多个分区。

```
Topic "search.note.sync"
├── Partition 0
├── Partition 1
└── Partition 2
```

**命名约定**：项目中使用 `<顶层业务>.<实体>.<操作>` 格式，如 `search.note.sync`。

### 3.4 Partition（分区）

Topic 的物理分片，每个分区是一个**有序的、不可变的消息序列**。消息以 append-only 的方式写入分区尾部。

```
Partition 0
┌───┬───┬───┬───┬───┬───┬───┐
│ 0 │ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │  ← 每条消息有唯一的 offset
└───┴───┴───┴───┴───┴───┴───┘
  ↑                    ↑
  最早                 最新
```

**核心规则**：

| 规则 | 说明 |
|------|------|
| 同一个 key 的消息进入同一个分区 | `hash(key) % partition_count` |
| 同一分区内严格有序 | offset 单调递增 |
| 分区数决定并行度 | 消费者数 ≤ 分区数，超出则空闲 |
| **分区数只能增加，不能减少** | 增加可动态调整，减少需删除 Topic 重建 |

### 3.5 Producer（生产者）

写入消息到 Kafka 的客户端。

```java
// 发送策略
// 发后即忘（fire-and-forget）
producer.send(record);

// 同步发送
producer.send(record).get();

// 异步回调（推荐）
producer.send(record, (metadata, exception) -> {
    if (exception == null) {
        log.info("Sent to partition {}, offset {}", metadata.partition(), metadata.offset());
    }
});
```

**重要配置**：

| 配置 | 说明 | 推荐值 |
|------|------|--------|
| `acks` | 写入确认级别 | `all`（最强保证） |
| `compression.type` | 压缩算法 | `lz4`（平衡速度与压缩比） |
| `batch.size` | 批量发送大小（字节） | 16384 |
| `linger.ms` | 批量等待时间 | 5-10ms |
| `max.in.flight.requests.per.connection` | 在途请求数 | `1`（严格顺序时） |

**acks 三级别**：

| 值 | 含义 | 可靠性 | 延迟 |
|----|------|--------|------|
| `0` | 不等待确认 | ❌ 可能丢消息 | 最低 |
| `1` | Leader 写入即确认 | ⚠️ Leader 宕机可能丢 | 低 |
| `all` / `-1` | 所有 ISR 副本全部确认 | ✅ 最强 | 最高 |

### 3.6 Consumer（消费者）与 Consumer Group

```
Consumer Group A (一个逻辑订阅者):
  Consumer 1 → Partition 0, 1
  Consumer 2 → Partition 2

Consumer Group B (另一个独立订阅者):
  Consumer 3 → Partition 0, 1, 2   (独立 offset，不受 Group A 影响)
```

**关键规则**：
- 同一 Group 内，一个分区最多被一个 Consumer 消费
- 不同 Group 之间完全隔离，各自独立消费
- Consumer 数量 > 分区数 → 多余 Consumer 空闲
- Consumer 宕机 → Rebalance：剩余 Consumer 接管分区

### 3.7 Offset（偏移量）

每条消息在分区内的唯一序号。Consumer 提交 offset 表示已消费到哪里。

```
Offset 管理演进:
  v0.8 前: Zookeeper 存储 offset
  v0.9+:  Kafka 内部 __consumer_offsets Topic 存储 offset
```

**提交策略**：

| 策略 | 行为 | 风险 |
|------|------|------|
| **自动提交** | `enable.auto.commit=true`，定时提交 | 可能重复消费（已处理但未提交时崩溃） |
| **手动同步提交** | `consumer.commitSync()` | 阻塞直到成功，吞吐低 |
| **手动异步提交**（推荐） | `consumer.commitAsync()` + 异常重试 | 高吞吐，可能有短暂重复 |

### 3.8 Replica（副本）与 ISR

```
Partition 0
├── Leader (Broker 1)     ← 所有读写走 Leader
├── Follower (Broker 2)   ← ISR（In-Sync Replica），与 Leader 同步
└── Follower (Broker 3)   ← OSR（Out-of-Sync Replica），落后太多被踢出
```

| 概念 | 说明 |
|------|------|
| **Leader** | 处理所有读写请求 |
| **Follower** | 被动从 Leader 拉取数据 |
| **ISR** | 与 Leader 保持同步的副本集合 |
| **HW** (High Watermark) | ISR 中最小的 offset，消费者只能消费到 HW 之前 |

---

## 4. Docker 安装（KRaft 模式，无需 Zookeeper）

```bash
# 单节点（KRaft 模式，Kafka 3.3+）
docker run -d \
  --name kafka \
  -p 9092:9092 \
  -e KAFKA_CFG_NODE_ID=1 \
  -e KAFKA_CFG_PROCESS_ROLES=controller,broker \
  -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  bitnami/kafka:latest

# 测试
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

---

## 5. Topic 操作

```bash
# 创建 Topic
kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic search.note.sync \
  --partitions 3 --replication-factor 1

# 查看 Topic
kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic search.note.sync
# Topic: search.note.sync	PartitionCount: 3	ReplicationFactor: 1
#   Partition: 0	Leader: 1	Replicas: 1	Isr: 1
#   Partition: 1	Leader: 1	Replicas: 1	Isr: 1
#   Partition: 2	Leader: 1	Replicas: 1	Isr: 1

# 查看所有 Topic
kafka-topics.sh --bootstrap-server localhost:9092 --list

# 增加分区（只能增，不能减）
kafka-topics.sh --bootstrap-server localhost:9092 \
  --alter --topic search.note.sync --partitions 6

# 删除 Topic
kafka-topics.sh --bootstrap-server localhost:9092 \
  --delete --topic search.note.sync
```

**分区数怎么选**：

| 场景 | 建议分区数 |
|------|-----------|
| 开发环境 | 1-3 |
| 低吞吐（< 1000 TPS） | 3-8 |
| 中吞吐（1000-10000 TPS） | 8-32 |
| 高吞吐（> 10000 TPS） | 32-128 |

> 分区数决定了写入并行度和消费者并行度，但也增加选举和 FD 开销，不是越多越好。

---

## 6. 消息操作（命令行）

```bash
# 生产消息
echo '{"noteId":1,"title":"hello"}' | kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic search.note.sync

# 消费消息（从最新开始）
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic search.note.sync

# 消费消息（从头开始，--from-beginning）
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic search.note.sync --from-beginning

# 消费消息（带 key 和 partition 信息）
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic search.note.sync --property print.key=true --property print.partition=true

# 查看消费者组进度
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group es-consumer-group
# GROUP            TOPIC              PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
# es-consumer-group search.note.sync  0          150             150             0
# es-consumer-group search.note.sync  1          142             142             0
# es-consumer-group search.note.sync  2          148             148             0
```

> **LAG**（延迟）是最重要的监控指标——代表消费者落后多少条消息。LAG > 0 意味着消费有堆积。

---

## 7. Spring Kafka

### 7.1 依赖

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### 7.2 Producer

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      compression-type: lz4
```

```java
@Component
public class NoteSyncProducer {

    @Autowired
    private KafkaTemplate<String, NoteSyncMessage> kafkaTemplate;

    public void sendNoteSync(NoteSyncMessage message) {
        // key = noteId 字符串，保证同一笔记的操作进入同一分区（有序）
        kafkaTemplate.send("search.note.sync", String.valueOf(message.getNoteId()), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send: {}", message, ex);
                    } else {
                        log.debug("Sent to partition {}, offset {}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
```

### 7.3 Consumer

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: es-consumer-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
      max-poll-records: 500
      properties:
        spring.json.trusted.packages: "com.example.common.dto"
```

```java
@Component
@Slf4j
public class NoteSyncConsumer {

    @Autowired
    private ElasticsearchOperations esOperations;

    @KafkaListener(topics = "search.note.sync", concurrency = "3")
    public void consume(@Payload List<NoteSyncMessage> messages,
                        Acknowledgment ack) {
        try {
            List<IndexQuery> queries = messages.stream()
                    .filter(msg -> msg.getType() != SyncType.DELETE)
                    .map(this::toIndexQuery)
                    .toList();

            if (!queries.isEmpty()) {
                esOperations.bulkIndex(queries, PostDocument.class);
            }

            // 处理删除
            messages.stream()
                    .filter(msg -> msg.getType() == SyncType.DELETE)
                    .forEach(msg -> esOperations.delete(String.valueOf(msg.getNoteId()), PostDocument.class));

            ack.acknowledge();  // 手动提交 offset
        } catch (Exception e) {
            log.error("Failed to process batch, will retry", e);
            // 不 ack，下一轮 poll 重新消费（at-least-once）
        }
    }

    private IndexQuery toIndexQuery(NoteSyncMessage msg) { /* ... */ }
}
```

**Consumer 关键参数**：

| 配置 | 说明 |
|------|------|
| `auto-offset-reset: earliest` | 无 offset 时从头消费（`latest` = 从最新开始） |
| `enable-auto-commit: false` | 关闭自动提交，改用手动 ACK |
| `max-poll-records: 500` | 每次 poll 最多拉取 500 条（批量消费） |
| `concurrency: 3` | 消费线程数（≤ 分区数） |
| `batch: true` | 批量消费（一次 poll 出多条） |

---

## 8. 消息语义与可靠性

### 8.1 三种语义

| 语义 | 含义 | 实现方式 |
|------|------|---------|
| **At-most-once** | 最多一次，可能丢 | ACK 先于处理 |
| **At-least-once** | 至少一次，可能重复 | 处理完再 ACK（本项目的选择） |
| **Exactly-once** | 精确一次 | Kafka 事务 + 幂等（复杂，性能低） |

### 8.2 本项目：At-least-once + 幂等处理

```
Why at-least-once:
  → 先处理（写 ES），处理成功后才 ACK
  → 处理崩溃 → 没 ACK → 下次 poll 重新拿到 → 重新处理

Why 不丢:
  → 处理失败时不 ACK → Kafka 重发 → 重试直到成功

Why 可能重复:
  → 处理成功 + ACK 没发出去 → Kafka 认为没收到 ACK → 重发
  → 应对方式：ES INDEX 操作本身就是幂等的（相同 docId 多次写入 = 最后一次生效）
```

### 8.3 死信队列（DLT）

当重试耗尽后，将失败消息发到死信 Topic，避免阻塞主流程：

```java
@Bean
public DeadLetterPublishingRecoverer recoverer(KafkaTemplate<String, NoteSyncMessage> template) {
    return new DeadLetterPublishingRecoverer(template,
            (r, e) -> new TopicPartition("search.note.sync.dlt", r.partition()));
}
```

> 死信队列的消息需要人工排查：是不合法数据、ES 宕机、还是 Bug。

---

## 9. 设计决策

### 9.1 为什么 Canal binlog 用 Kafka 而不是直连 ES

```
方案 A: Canal → ES（直连）
  Canal → ES Adapter 直推
  问题: ES 挂了 → Canal 重试耗尽 → 丢数据
       无法多消费者（只有一个 ES 消费者）

方案 B: Canal → Kafka → ES（✅）
  Canal → Kafka（写入）
  Kafka → ES Consumer（消费）
  优点: ES 挂了 → Kafka 堆积，恢复后自动追回
       Kafka 数据保留 7 天 → 可以重置 offset 重建 ES 索引
       其他消费者（数据分析、实时推送）可以独立消费同一数据
```

### 9.2 为什么不直接应用双写

```
方案 A: 应用双写（❌）
  noteService.publish() {
      noteRepo.save(note);      // 写 MySQL
      esRepo.save(doc);         // 写 ES
  }
  → 问题：
    1. 两次写入不在一个事务 → ES 成功但 MySQL 失败 = 数据不一致
    2. 业务代码入侵，每个写操作都要记得同步 ES
    3. ES 写失败抛异常 → 用户看到错误但 MySQL 是成功的

方案 B: Canal binlog 订阅（✅）
  noteService.publish() {
      noteRepo.save(note);      // 只写 MySQL
  }
  → Canal 自动感知变更 → 发 Kafka → Consumer 写 ES
  → 优点：
    1. 业务代码零入侵
    2. MySQL 是唯一写入源，不会不一致
    3. ES 写入失败不影响用户
```

### 9.3 顺序消息与分区 Key

```
场景：同一笔记先发布，后修改标题，再删除
  → 必须按顺序处理，否则先收到删除再收到修改 → 删除后又被创建回来

保证方式：Kafka key = noteId（hash 到固定分区）

Topic: search.note.sync (3 分区)
  noteId=42 → hash("42") % 3 = 0 → Partition 0
  noteId=99 → hash("99") % 3 = 1 → Partition 1

Partition 0 内消息严格有序:
  [offset=100: noteId=42 PUBLISH]  ← 先
  [offset=101: noteId=42 UPDATE]   ← 后
  [offset=102: noteId=42 DELETE]   ← 最后
```

### 9.4 为什么配置 acks=all

```
acks=0    Broker 没收到 = 丢
acks=1    Leader 写成功但宕机，Follower 没同步 = 丢
acks=all  所有 ISR 写成功后确认 = 要么都成功，要么都失败

本项目中:
  Canal 写 Kafka（acks=all）
  → 消息持久化到 Kafka
  → ES Consumer 消费（at-least-once + 手动 ACK）
  → 全链路不丢消息
```

---

## 10. 本项目 Kafka 使用总览

| Topic | 生产者 | 消费者 | 用途 |
|-------|--------|--------|------|
| `search.note.sync` | Canal | ES Consumer | 笔记变更 → ES 搜索索引 |
| `search.note.sync.dlt` | Consumer（重试耗尽） | 人工排查 | 失败消息死信队列 |

### 从数据到搜索的完整链路

```
MySQL binlog 产生事件
  │
  ▼
Canal (伪装 MySQL slave)
  │  解析 binlog，提取 INSERT/UPDATE/DELETE
  │  构造 NoteSyncMessage {noteId, type, title, content, status, ...}
  │
  ▼
Kafka (Topic: search.note.sync, 3 partitions)
  │  key = noteId → 同一笔记到同一分区，保序
  │  数据保留 7 天
  │
  ├──── ES Consumer (Group: es-consumer-group)
  │       批量 poll (500 条/批)
  │       → 构建 ES IndexQuery
  │       → bulkIndex 写入 ES
  │       → 手动 ACK
  │
  └──── [未来] Data Analytics Consumer (Group: analytics-group)
          → 实时热点分析、推荐模型
```

---

## 11. 常见问题

### Q: Kafka 为什么这么快？

| 因素 | 说明 |
|------|------|
| **顺序 I/O** | 磁盘顺序写比随机写快 6000 倍 |
| **Page Cache** | 写文件先写 OS Page Cache，由 OS 异步刷盘 |
| **零拷贝** | `sendfile()` 系统调用，数据从磁盘 → Page Cache → 网卡，不经用户态 |
| **批量 + 压缩** | Producer 端批量发送 + lz4/snappy 压缩 |
| **分区并行** | 不同分区可并发读写 |

### Q: offset 自动提交 vs 手动提交？

自动提交省事但有**重复消费**风险（间隔内处理崩溃 → 重启从上次提交继续，但中间可能已部分处理）。生产环境建议**手动异步提交**。

### Q: Consumer 处理慢怎么办？

```
1. 增加 concurrency（消费线程数，≤ 分区数）
2. 增加 Topic 分区数（需停写后操作）
3. 增加 Consumer 实例数（同 Group 内负载均衡）
4. 批量消费（max-poll-records + batch）
5. ES 端用 bulk API（手动拼 bulk 或 Spring batch bulk）
```

### Q: 怎么监控 Kafka 健康度？

| 指标 | 含义 | 告警阈值 |
|------|------|---------|
| `consumer_lag` | 消费延迟 | > 1000 条或持续增长 |
| `under_replicated_partitions` | 副本未同步分区数 | > 0 |
| `active_controller_count` | Controller 数量 | ≠ 1（脑裂） |
| `bytes_in_per_sec` / `bytes_out_per_sec` | 进出流量 | 根据带宽上限设置 |

### Q: Kafka 最低需要几个节点？

- **开发**：1 个（KRaft 单节点）
- **测试**：1-3 个（3 个才能用 `min.insync.replicas=2` 保证可靠性）
- **生产**：≥ 3 个（3 副本 + `acks=all` + `min.insync.replicas=2`）

---

## 12. 推荐学习路径

1. **理解 Partition 和 Consumer Group**（本文第 3 节）——这是 Kafka 最核心的两个概念
2. **Docker 启动 + CLI 操作**（本文 4-6 节）——动手生产/消费消息
3. **理解 acks 和 offset 提交**（本文 8 节）——保证不丢消息
4. **Spring 整合**（本文第 7 节）——代码实操
5. **深入阅读**：[Kafka 官方文档](https://kafka.apache.org/documentation/)、[Kafka: The Definitive Guide](https://www.oreilly.com/library/view/kafka-the-definitive/9781492043072/)
