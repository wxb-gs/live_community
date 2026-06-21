# Kafka 广播地址为 Docker 主机名导致互动接口超时 500

## 现象

点赞、收藏等互动接口返回 500，响应耗时约 60 秒：

```
POST /api/interaction/toggle  →  500 Internal Server Error  (60s)
POST /api/interaction/toggle  →  500 Internal Server Error  (60s)
```

但 `GET /api/interaction/status` 正常返回 200。

## 根因

### 第一层：Kafka 广播地址为 Docker 内部主机名

`docker-compose.yml` 中 Kafka 的 `KAFKA_ADVERTISED_LISTENERS` 配置为：

```yaml
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
```

Kafka 协议的工作方式是：客户端首先通过 `bootstrap.servers`（`localhost:9092`）发起初始连接，然后 Broker 通过 `KAFKA_ADVERTISED_LISTENERS` 告知客户端后续通信应用 `kafka:9092`。因为 Java 服务运行在宿主机（非 Docker 容器内），`kafka` 主机名无法解析，导致所有后续 Kafka 操作失败。

### 第二层：Kafka Producer 阻塞等待元数据

`InteractionService.toggleNote()` 在 Redis 原子操作完成后，同步调用 `kafkaTemplate.send()` 发送互动事件到 topic `interaction-events`：

```java
// InteractionService.java:152
kafkaProducer.publish(new InteractionEvent(
        type.name(), targetType, targetId, userId, active));
```

虽然 `kafkaTemplate.send()` 返回 `CompletableFuture`（异步），但在首次向某个 topic 发送消息时，Producer 需要先从 Broker 获取 topic 元数据。该元数据获取操作会阻塞当前线程，最长等待 `max.block.ms`（默认 60000ms）。

因为 Broker 返回的广播地址 `kafka:9092` 无法解析，Producer 的元数据请求超时，阻塞 HTTP 请求线程整 60 秒后抛出 `TimeoutException`，最终导致 500 响应。

### 第三层：Dubbo 超时短于 Kafka 超时

note-service 日志中能看到 Dubbo 3 秒超时警告：

```
[Dubbo-Provider] execute service com.example.common.InteractionRpcService#toggle
cost 60008ms. Timeout: 3000ms
```

Dubbo 消费者（gateway）在 3 秒后即判定调用超时，但 note-service 侧的 Dubbo 线程仍被 Kafka Producer 阻塞 60 秒，造成线程泄漏。

### 为什么 GET /api/interaction/status 正常？

`getStatus()` 方法只读 Redis，不发送 Kafka 事件，因此不受影响。

## 修复

**docker-compose.yml**：将 `KAFKA_ADVERTISED_LISTENERS` 改为宿主机可解析的地址：

```yaml
# 修复前
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092

# 修复后
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
```

重启 Kafka 容器和相关 Java 服务后，`kafkaTemplate.send()` 的元数据获取在毫秒级完成，互动接口恢复正常。

## 关键文件变更

| 文件 | 变更 |
|------|------|
| `docker-compose.yml` | `KAFKA_ADVERTISED_LISTENERS` 从 `kafka:9092` 改为 `localhost:9092` |
