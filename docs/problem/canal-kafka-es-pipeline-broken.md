# 前端发布笔记后无法实时搜索

## 现象

通过前端发布笔记后，在搜索框搜索不到新发布的笔记。MySQL 完整数据 33 篇，ES 仅 12 篇，丢失 20+ 篇。

```
MySQL:  SELECT COUNT(*) FROM note;  → 33
ES:     GET /notes/_count            → 12
Kafka:  consumer group lag           → 0（但 ES 少 20 篇，说明部分消息索引失败后仍被 ACK）
```

## 根因

链路 `MySQL binlog → Canal → Kafka → search-sync-service → ES` 中有 3 个断裂点：

### 第一层：Canal 未切换到 Kafka 模式（主因）

`canal.properties` 中 `canal.serverMode = tcp`（默认值），Canal 仅在 TCP 通道内传递 binlog 事件，从未投递到 Kafka。环境变量 `CANAL_SERVER_MODE=kafka` 已设置，但 Canal v1.1.8 镜像的启动脚本未正确替换到配置文件。

同时 instance 配置中 `canal.instance.master.address=127.0.0.1:3306` 无法连接 MySQL 容器（应为 `mysql:3306`），`canal.mq.topic=example` 而非 `search_sync`。

只有 note-service 的 `NoteSyncProducer`（直接发送到 Kafka 的异步路径）投递了部分事件，解释了为什么 ES 仍有 12 篇。

### 第二层：Kafka 消费者自动 ACK，写入失败仍然提交 offset

```java
// KafkaConsumerConfig — 修复前
// 未设置 ENABLE_AUTO_COMMIT_CONFIG，默认为 true
// 未设置 AckMode，默认为自动
```

```java
// SearchSyncConsumer — 修复前
public void onMessage(String message) {
    try {
        // ...
        esIndexService.indexNote(msg.getData()); // 写入失败只记日志
    } catch (Exception e) {
        log.error("Failed", e); // offset 已被自动提交，消息永久丢失
    }
}
```

```java
// EsIndexService — 修复前
private void executeBulk(List<BulkOperation> ops, String index) {
    BulkResponse response = esClient.bulk(...);
    if (response.errors()) {
        items.stream()
            .filter(item -> item.error() != null)
            .forEach(item -> log.error("Bulk error: {}", item.error().reason()));
        // 只记日志，未向上层传递失败信号
    }
}
```

ES 索引失败 → 日志记录 → 消费者方法正常返回 → auto-commit 提交 offset → 消息永久丢失。

### 第三层：多实例抢占分区

同时运行 3 个 `SearchSyncApplication` 实例（PID 40972, 72212, 42844），同属 `search-sync-group` 消费者组。实例频繁 rebalance，部分消息在 rebalance 期间被跳过。

## 修复

### 1. Canal 配置修正

**`scripts/canal.properties`（新建，挂载到容器）**：

```properties
canal.serverMode = kafka
kafka.bootstrap.servers = kafka:9092
kafka.acks = all
kafka.retries = 3
```

**`scripts/canal-instance.properties`（新建，挂载到容器）**：

```properties
canal.instance.master.address = mysql:3306
canal.instance.dbUsername = root
canal.instance.dbPassword = root
canal.instance.filter.regex = live_community\\..*
canal.mq.topic = search_sync
```

**`docker-compose.yml`** 挂载配置：

```yaml
canal-server:
  volumes:
    - ./scripts/canal.properties:/home/admin/canal-server/conf/canal.properties:ro
    - ./scripts/canal-instance.properties:/home/admin/canal-server/conf/example/instance.properties:ro
```

### 2. Kafka 消费者改为手动 ACK

**`KafkaConsumerConfig.java`**：

```java
// 新增两行
props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
// ...
factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
```

**`SearchSyncConsumer.java`**：增加 `Acknowledgment` 参数，ES 写入成功才 `ack()`：

```java
@KafkaListener(topics = "search_sync", groupId = "search-sync-group")
public void onMessage(String message, Acknowledgment ack) {
    try {
        CanalMessage msg = CanalMessage.fromJson(message);
        boolean ok = switch (msg.getTable()) {
            case "note" -> esIndexService.indexNote(msg.getData());
            // ...
        };
        if (ok) {
            ack.acknowledge();  // 写入成功才提交
        }
        // 失败不 ack，消息会被重新投递
    } catch (Exception e) {
        log.error("Failed, will retry", e);
        // 不 ack，触发重试
    }
}
```

### 3. ES 写入返回成功/失败标志

**`EsIndexService.java`**：所有 index/delete 方法改为返回 `boolean`：

```java
public boolean indexNote(List<JsonNode> rows) {
    // ...
    if (response.errors()) {
        log.error("Bulk errors: {}", errors);
        return false;  // 通知消费者不要 ACK
    }
    return true;
}
```

### 4. 幂等性保证

ES `index` 操作使用固定 doc id（PUT 语义），重复消费 = 覆盖写入，天然幂等。无需额外的去重逻辑。

### 5. 防止多实例抢占

**方案 A（当前采用）：单实例部署**

`search_sync` topic 只有 1 个分区，Kafka 规定同一分区只能被同一消费者组内的 1 个消费者消费。多个实例中只有 1 个能拿到分区，其余空闲，且新实例加入时触发 rebalance 导致短暂的消费暂停。

最简单方案：确保只启动 1 个 search-sync-service 实例。通过端口占用天然限制（Dubbo 端口 20886 固定）：

```bash
# 启动前先检查是否已有实例在运行
jps -l | grep SearchSync && echo "实例已在运行，请先停止" || java -jar search-sync-service/target/search-sync-service-1.0.0-SNAPSHOT.jar
```

**方案 B（生产环境）：静态组成员 + 多分区**

如果将来需要多实例高可用，扩展 `search_sync` topic 为多个分区后，启用 Kafka 静态组成员：

```java
// KafkaConsumerConfig 中增加
props.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, "search-sync-" + instanceId);
```

静态组成员在滚动重启时不会触发 rebalance，消费者短暂失联（`session.timeout.ms` 内）不会引发分区重分配。

## 修复后链路

```
MySQL binlog
  ↓
Canal (serverMode=kafka, acks=all, retries=3)   ← 确保投递到 Kafka
  ↓
Kafka (topic: search_sync)
  ↓
search-sync-service (MANUAL ack)                ← 写入 ES 成功才提交 offset
  ↓
Elasticsearch (PUT by id, 天然幂等)              ← 重复消费不会产生脏数据
```

## 关键文件变更

| 文件 | 变更 |
|------|------|
| `search-sync-service/.../KafkaConsumerConfig.java` | `ENABLE_AUTO_COMMIT=false`，`AckMode=MANUAL` |
| `search-sync-service/.../SearchSyncConsumer.java` | 接收 `Acknowledgment`，按写入结果决定 `ack()` |
| `search-sync-service/.../EsIndexService.java` | 返回 `boolean`，出错返回 `false` |
| `scripts/canal.properties` | **新建**：`serverMode=kafka`，`acks=all`，`retries=3` |
| `scripts/canal-instance.properties` | **新建**：MySQL 连接配置 + topic 配置 |
| `docker-compose.yml` | mount 两个 Canal 配置文件到容器 |
| 运维 | 手动 kill 多余实例（`taskkill //F //PID xxx`），确保仅 1 个实例运行 |
| 修复后链路图 | 见下方「修复后链路」 |
