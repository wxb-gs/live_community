# Cassandra 入门指南

## 1. 什么是 Cassandra

Apache Cassandra 是一个**分布式 NoSQL 宽列数据库**，最初由 Facebook 开发，后捐赠给 Apache 基金会。它被设计用来在廉价的商用服务器上处理海量数据，同时提供高可用性和无单点故障的架构。

### 核心特征

| 特征 | 说明 |
|------|------|
| **分布式无中心** | 所有节点平等（P2P 架构），没有 Master/Slave 之分 |
| **高可用** | 自动数据复制、多数据中心容灾，无单点故障 |
| **高写入吞吐** | 写入先写内存（Memtable）+ 日志（CommitLog），顺序 I/O |
| **横向扩展** | 加节点即可线性扩展，无需停服 |
| **最终一致性** | 可调节一致性级别（ONE / QUORUM / ALL） |

---

## 2. Cassandra vs 关系型数据库

| | Cassandra | MySQL / PostgreSQL |
|---|---|---|
| **数据模型** | 宽列（Wide Column）| 行式关系（Relational）|
| **Schema** | 灵活，但需要定义主键 | 严格的表结构 |
| **JOIN** | **不支持** | 核心能力 |
| **事务** | 轻量级事务（LWT），无 ACID | 完整 ACID |
| **扩展方式** | 水平扩展（加节点）| 垂直扩展（升级硬件）|
| **查询方式** | **按主键查询**，不支持任意 WHERE | 任意 SQL WHERE |
| **适用场景** | 写多读少、时序数据、日志、IOT | OLTP、复杂查询、强一致性业务 |

> **简单理解**：Cassandra 牺牲了 SQL 数据库的灵活查询和 JOIN 能力，换来了近乎无限的横向扩展和高写入性能。

---

## 3. 核心概念

### 3.1 Keyspace（键空间）

相当于关系型数据库的 **Database**。定义复制策略和复制因子。

```sql
CREATE KEYSPACE notes
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};
```

- `SimpleStrategy`：单数据中心
- `NetworkTopologyStrategy`：多数据中心
- `replication_factor`：每份数据存几份副本

### 3.2 Table（表）

相当于关系型数据库的 **Table**，但设计思维完全不同——Cassandra 的表是**围绕查询设计的**，而非围绕实体关系。

### 3.3 Primary Key（主键）= Partition Key + Clustering Key

这是 Cassandra 最核心的概念：

```
PRIMARY KEY = (partition_key) + clustering_key_1, clustering_key_2, ...
```

#### Partition Key（分区键）

- **决定数据存储在哪个节点**（通过哈希取模）
- 同一个分区键的数据存在一起（一个分区内）
- 查询时**必须提供完整的分区键**，否则会全表扫描（极慢）

#### Clustering Key（聚簇键）

- **决定分区内数据的排序顺序**
- 聚簇键是可选的
- 支持范围查询（`>`、`<`、`BETWEEN`）

#### 图示

```
PRIMARY KEY ((note_id), comment_id)
             ^^^^^^^^^  ^^^^^^^^^^
             Partition   Clustering

分区1 (note_id=100):                   分区2 (note_id=200):
  comment_id=1, content="好文章"          comment_id=4, content="学到了"
  comment_id=2, content="收藏了"          comment_id=7, content="赞"
  comment_id=5, content="不错"
```

### 3.4 数据分布示意

```
客户端写入 note_id=42 的数据
         │
         ▼
   ┌─ 哈希(42) % 节点数 ─┐
   │                      │
   ▼                      ▼
 Node A                Node B
 ┌──────────┐          ┌──────────┐
 │ 分区 42   │          │ 分区 99   │
 │ 分区 7    │          │ 分区 15   │
 └──────────┘          └──────────┘
```

---

## 4. 本项目中的 Cassandra 使用

### 4.1 环境配置

```yaml
# note-service/src/main/resources/application.yml
spring:
  cassandra:
    keyspace-name: notes
    contact-points: localhost    # 集群任一节点 IP
    port: 9042
    local-datacenter: datacenter1
    schema-action: CREATE_IF_NOT_EXISTS
```

### 4.2 Java 配置类

```java
// CassandraConfig.java
@Configuration
@EnableCassandraRepositories(basePackages = "com.example.note.repository")
public class CassandraConfig {

    @Bean
    public CqlSession cqlSession(...) {
        // 与 Cassandra 集群建立连接
    }

    @Bean
    public CassandraTemplate cassandraTemplate(CqlSession session) {
        // Spring Data 操作模板，类似 JdbcTemplate
    }
}
```

项目还通过 `CassandraKeyspaceInitializer` 在启动时自动创建 keyspace（`CREATE KEYSPACE IF NOT EXISTS`）。

### 4.3 表设计实例

#### note 表（简单主键）

```java
@Table("note")
public class NoteEntity {
    @PrimaryKey
    private Long id;            // 简单主键 = Partition Key + 无 Clustering Key

    @Column("user_id")
    private Long userId;

    @Column("title")
    private String title;
    // ...
}
```

```
CQL:
CREATE TABLE note (
    id         bigint PRIMARY KEY,
    user_id    bigint,
    title      text,
    content    text,
    ...
);
```

查询方式：只能通过 `id` 查。

```
SELECT * FROM note WHERE id = ?;     ✅ 走主键
SELECT * FROM note WHERE status = ?; ❌ 全表扫描（不允许）
```

#### comment 表（复合主键）

```java
@Table("comment")
public class CommentEntity {
    @PrimaryKeyColumn(name = "note_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private Long noteId;          // Partition Key：按笔记 ID 分区

    @PrimaryKeyColumn(name = "comment_id", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    private Long commentId;       // Clustering Key：分区内排序
    // ...
}
```

```
CQL:
CREATE TABLE comment (
    note_id     bigint,
    comment_id  bigint,
    user_id     bigint,
    content     text,
    PRIMARY KEY (note_id, comment_id)
);
```

查询方式：可以按 `note_id` 查某条笔记的所有评论，评论按 `comment_id` 排序。

```sql
SELECT * FROM comment WHERE note_id = ?;  ✅ 走分区键，高效
```

#### interaction_record 表（三列复合主键）

```java
@Table("interaction_record")
public class InteractionRecordEntity {
    @PrimaryKeyColumn(name = "target_type", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String targetType;

    @PrimaryKeyColumn(name = "target_id", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    private Long targetId;

    @PrimaryKeyColumn(name = "interaction_type", ordinal = 2, type = PrimaryKeyType.CLUSTERED)
    private String interactionType;

    @PrimaryKeyColumn(name = "user_id", ordinal = 3, type = PrimaryKeyType.CLUSTERED)
    private Long userId;
}
```

```
CQL:
CREATE TABLE interaction_record (
    target_type      text,
    target_id        bigint,
    interaction_type text,
    user_id          bigint,
    status           text,
    PRIMARY KEY ((target_type, target_id), interaction_type, user_id)
);
```

> `(target_type, target_id)` 组成复合 Partition Key，`interaction_type` 和 `user_id` 组成 Clustering Key。

查询方式：

```sql
-- 查某篇笔记的所有点赞用户
SELECT * FROM interaction_record
  WHERE target_type = 'note'
    AND target_id = 42
    AND interaction_type = 'like';
```

### 4.4 Spring Data Cassandra Repository

```java
@Repository
public interface CommentRepository extends CassandraRepository<CommentEntity, Long> {

    // 自定义 CQL 查询
    @Query("SELECT * FROM comment WHERE note_id = ?0")
    List<CommentEntity> findByNoteId(Long noteId);
}

// 使用
commentRepository.findByNoteId(42L);
```

Spring Data Cassandra 提供与 JPA 类似的 Repository 抽象，但 JOIN 和派生查询（`findByXxx`）受限于主键设计。

---

## 5. CQL 快速参考

CQL（Cassandra Query Language）语法类似 SQL，但有明显差异。

### DDL

```sql
-- 创建 keyspace
CREATE KEYSPACE my_keyspace
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

-- 切换 keyspace
USE my_keyspace;

-- 创建表
CREATE TABLE users (
    user_id    bigint,
    name       text,
    email      text,
    created_at timestamp,
    PRIMARY KEY (user_id)
);

-- 删除表
DROP TABLE users;
```

### DML

```sql
-- 插入（UPSERT：不存在则插入，存在则更新）
INSERT INTO users (user_id, name, email, created_at)
  VALUES (1, '张三', 'zhangsan@example.com', toTimestamp(now()));

-- 更新（本质也是 UPSERT）
UPDATE users SET email = 'new@example.com' WHERE user_id = 1;

-- 查询（只能按主键查询！）
SELECT * FROM users WHERE user_id = 1;

-- 范围查询（需要 Clustering Key）
SELECT * FROM comment WHERE note_id = 100 AND comment_id > 5;
```

### ⚠️ WHERE 限制

Cassandra **不允许**无索引的列出现在 WHERE 中：

```sql
SELECT * FROM note WHERE status = 'PUBLISHED';  -- ❌ 全表扫描，被拒绝
```

解决方式：

1. **重新设计主键**让查询列成为 Partition Key 或 Clustering Key
2. **创建二级索引**（慎用，影响写入性能）
3. **ALLOW FILTERING**（开发环境可用，生产禁用）
4. **新建一张反查表**，用不同主键存同一份数据

---

## 6. 数据建模核心原则

### 原则一：查询优先，而非实体优先

关系型数据库先画 ER 图，Cassandra 先列出查询。

```
需求 → 列出所有查询 → 为每个查询设计一张表 → 冗余存储
```

### 原则二：一张表服务于一个查询模式

```
# 关系型思维（一张表 + JOIN）
SELECT * FROM note n JOIN user u ON n.user_id = u.id;

# Cassandra 思维（冗余存储）
-- 表1: 按笔记 ID 查笔记详情
PRIMARY KEY (note_id)

-- 表2: 按用户 ID 查用户所有笔记（反查表）
PRIMARY KEY ((user_id), created_at, note_id)
```

### 原则三：Parition Key 的选择

- **高基数**（值足够分散，如 user_id、note_id）
- **避免热点**（不要所有数据集中在少数分区）
- **查询必须提供完整值**

### 原则四：注意分区大小

- 单个分区建议不超过 100MB
- 分区过大说明 Partition Key 基数太低
- 可通过"分桶"策略拆分（如 `user_id_month` 作为分区键）

---

## 7. Docker 本地部署

项目使用 Docker Compose 部署 Cassandra：

```yaml
# docker-compose.yml
cassandra:
  image: cassandra:4.1
  ports:
    - "9042:9042"
  environment:
    - CASSANDRA_CLUSTER_NAME=LiveCommunity
```

### 常用运维命令

```bash
# 进入 Cassandra 容器
docker exec -it cassandra cqlsh

# 查看 keyspace
DESCRIBE KEYSPACES;

# 查看表结构
DESCRIBE notes;

# 查看数据
SELECT * FROM notes.note LIMIT 10;
SELECT * FROM notes.comment WHERE note_id = 123;

# 查看表数据量
SELECT COUNT(*) FROM notes.note;
```

---

## 8. 常见问题

### Q: 为什么不能做 JOIN？

Cassandra 是分布式数据库，数据分散在不同节点。JOIN 需要跨节点聚合数据，与它的设计哲学（单节点快速读写）冲突。解决方案是**在写入时做冗余**，把关联数据写入多张表。

### Q: 什么时候用 Cassandra？

- 数据量大（TB 级），需要水平扩展
- 写入量极大（每秒万级+）
- 查询模式固定且简单（按主键查）
- 可接受数据冗余
- 高可用要求（不能停机）

### Q: 什么时候不用 Cassandra？

- 需要复杂查询、聚合、JOIN
- 需要 ACID 事务
- 数据量小（GB 级），MySQL/PostgreSQL 完全够用
- 查询模式还在频繁变化中

### Q: ALLOW FILTERING 能用吗？

开发调试可以，**生产环境绝对不要用**。它会触发全表扫描，慢且消耗大量节点资源。如果发现需要 ALLOW FILTERING，说明表设计有问题。

---

## 9. 设计决策与取舍

本章结合本项目的**点赞/收藏系统**，分析几个关键的架构取舍：热点行问题、Redis + Lua + 异步落库的组合选择、以及 Cassandra 与 MySQL 的分工边界。

**本项目采用双持久化路径**：
- **笔记的点赞/收藏**：Redis 挡热点 → 定时异步合并写入 **MySQL**（与笔记强关联，方便关联查询）
- **评论的点赞记录**：Redis 挡热点 → 定时同步写入 **Cassandra**（与评论共存，按 `note_id` 分区自然聚合）

### 9.1 热点行问题

#### 什么是热点行

**热点行（Hot Row）** 是指被大量并发请求集中读写的同一行数据。在点赞场景下最典型：

```
1000 个用户同时点赞同一篇热门笔记
  → 所有请求争抢同一条 "like_count" 记录
  → 产生行锁竞争、连接耗尽、响应超时
```

#### 不同数据库面对热点行的表现

| 数据库 | 热点行问题 | 根因 |
|--------|-----------|------|
| **MySQL** | 🔴 严重 | 行锁（InnoDB）——更新同一行时所有请求排队等待锁，锁等待超时导致事务回滚 |
| **Cassandra** | 🟡 中等 | 无行锁，但同一分区的并发写入会产生**写冲突**（Last Write Wins），且重复写入同一行本身无意义 |
| **Redis** | 🟢 无影响 | 单线程模型 + 内存操作，INCR/DECR 天然原子，不存在锁竞争。SET 的 SADD/SREM 也是 O(1) |

#### 为什么 Redis 能解决

```
MySQL:                          Redis:
请求1 ─┐                        请求1 ─┐
请求2 ─┤ → [🔒行锁排队] → 写磁盘   请求2 ─┤ → [单线程内存执行] → 返回
请求3 ─┘   顺序执行，各等 5ms      请求3 ─┘   并发排队，但每个 0.01ms
           总耗时 ≈ 15ms                     总耗时 ≈ 0.03ms
```

Redis 的单线程事件循环天然将并发"串行化"，但执行速度是微秒级，不构成瓶颈。

#### 本项目方案：Redis 挡热点 + 异步落库

```
用户点赞请求
  → Redis Lua 脚本原子 toggle（不直接写磁盘数据库）
    ├── SISMEMBER 检查是否已点
    ├── SADD/SREM 修改集合
    └── INCR/DECR 修改计数器
  → 标记 "pending sync" 到 Redis Set
  → @Scheduled 每 30s 批量写入持久库：
      ├── 笔记点赞/收藏 → MySQL interaction_record 表
      └── 评论点赞     → Cassandra interaction_record 表
```

**核心思想**：热点数据留在 Redis（内存 + 单线程 + 原子操作），磁盘数据库只做冷数据的持久化存储。笔记互动数据归 MySQL（与笔记元数据就近），评论互动数据归 Cassandra（与评论表同 keyspace）。

---

### 9.2 点赞系统的方案演进与取舍

#### 方案一：纯 MySQL（❌ 否决）

```
Counter 表: note_id | like_count | update_time
Toggle 表:  note_id | user_id | status

每次点赞:
  UPDATE counter SET like_count = like_count + 1 WHERE note_id = ?;  -- 行锁
  INSERT INTO toggle ...                                              -- 判断重复需先 SELECT
```

**问题**：

| 问题 | 说明 |
|------|------|
| **热点行锁竞争** | 热门笔记的 `counter` 行被所有请求争抢，锁等待队列迅速堆积 |
| **非原子** | 检查是否点过赞 + 修改状态 + 更新计数器，三个操作不在同一事务则数据不一致 |
| **高并发崩溃** | 1000 QPS 打同一行，InnoDB 锁超时导致大量事务回滚，用户体验极差 |
| **扩展困难** | 分库分表能缓解，但跨分片事务和全局 ID 带来额外复杂度 |

#### 方案二：纯 Cassandra（❌ 部分否决）

```sql
-- Cassandra 没有 INCR 语义的原子 toggle
-- 需要 Read-then-Write，在并发下不安全：
SELECT * FROM like_users WHERE note_id = ? AND user_id = ?;  -- 读
INSERT INTO like_users ...                                     -- 写
UPDATE counter SET count = count ± 1 WHERE note_id = ?;       -- 写
```

**问题**：

| 问题 | 说明 |
|------|------|
| **无原子 Read-Modify-Write** | Cassandra 不支持 `UPDATE ... SET count = count + 1 WHERE ...`，需要先查后改，并发不安全 |
| **LWT 性能差** | Cassandra 的轻量级事务（`IF EXISTS` / `IF NOT EXISTS`）走 Paxos 协议，延迟 10-100ms，比 Redis 慢 1000 倍 |
| **热点分区压力** | 同一篇笔记的所有点赞写进同一个分区，Coordinator 节点压力集中 |

**适用部分**：Cassandra 适合做**持久化存储**——数据量大的历史记录，按 `note_id` 查询"谁点了赞"。

#### 方案三：Redis + Lua + 异步落库（✅ 最终方案）

```
┌────────────────────────────────────────────────────────────┐
│  笔记互动（Redis + Kafka → MySQL）                          │
│                                                             │
│  请求 → Redis Lua 脚本（原子 toggle）                        │
│         ├─ like:count:note:42  ← INCR/DECR                  │
│         ├─ like:users:note:42  ← SADD/SREM                  │
│         └─ user:like:123:note  ← SADD/SREM                  │
│       → Kafka 发送 InteractionEvent                         │
│         → 消费者窗口聚合（30s 窗口，按用户去重）               │
│         → 批量 UPSERT MySQL interaction_record              │
│                                                             │
│  评论互动（@Async → Cassandra）                              │
│                                                             │
│  请求 → CommentLikeService.toggleAsync()                    │
│         ├─ comment_like 表 SAVE 用户状态                     │
│         └─ comment_like_count 表 counter ± 1                │
└────────────────────────────────────────────────────────────┘
```

**为什么笔记和评论走不同的持久化路径？**

| | 笔记点赞/收藏 | 评论点赞 |
|---|---|---|
| **热路径** | Redis + Lua 原子 toggle | 无（直接异步写） |
| **异步方式** | Kafka 事件 → 消费者窗口聚合 | `@Async` → 直接写 Cassandra |
| **持久化目标** | MySQL | Cassandra（counter + comment_like） |
| **原因** | 笔记是核心实体，JOIN 用户表需求；Kafka 削峰填谷防止 MySQL 行锁瓶颈 | 评论点赞无热点（分散到数百条评论），counter 类型天然原子；与评论同 keyspace |
| **计数读取** | Redis GET（实时） | Cassandra counter 列读取 |
| **并发安全** | Lua 脚本原子化 | counter 列原子性 + `@Async` 顺序执行 |

#### 为什么用 Lua 脚本而不是 Redis 事务

```lua
-- toggle_interaction.lua
-- 一个 Lua 脚本完成：检查 → 修改 → 返回，全部原子
local usersKey = KEYS[1]
local countKey = KEYS[2]
local historyKey = KEYS[3]
local userId = ARGV[1]
local activeAction = ARGV[2]
local inactiveAction = ARGV[3]

local isMember = redis.call('SISMEMBER', usersKey, userId)
if isMember == 1 then
    redis.call('SREM', usersKey, userId)
    redis.call('DECR', countKey)
    redis.call('SREM', historyKey, countKey)
    return {redis.call('GET', countKey), inactiveAction}
else
    redis.call('SADD', usersKey, userId)
    redis.call('INCR', countKey)
    redis.call('SADD', historyKey, countKey)
    return {redis.call('GET', countKey), activeAction}
end
```

| | MULTI/EXEC 事务 | Lua 脚本 |
|---|---|---|
| **条件逻辑** | ❌ 不支持（无法在事务中根据前一步结果决定下一步） | ✅ 完整编程能力 |
| **原子性** | ✅ 命令间不插入其他命令 | ✅ 整个脚本阻塞执行 |
| **网络往返** | 多次（WATCH → MULTI → 命令... → EXEC） | 1 次 |
| **回滚** | WATCH 检测到冲突则 EXEC 不执行 | 脚本失败自动回滚 |

> 点赞 toggle 的核心逻辑是"检查是否已点 → 决定 add 还是 remove"，天然需要 `if-else`，Lua 是唯一选择。

#### 异步同步的取舍

```
为什么用 Kafka 而不是 @Scheduled 扫 Redis？
  ✅ Kafka 天然削峰填谷，即使瞬间 10000 次点赞也不压垮 MySQL
  ✅ 消费者可独立扩缩容，不受 note-service 实例数影响
  ✅ 消息持久化在 Kafka，不会因 Redis 重启丢失待同步数据
  ✅ 未来可加多个消费者组（数据分析、实时推送）

为什么 30s 窗口聚合？
  ✅ 同一笔记同一用户的多次操作在窗口内去重（赞→取消→赞 = 最终一次写入）
  ✅ 30s 内同一笔记 100 次点赞 → 1 次批量 UPSERT，极大减轻 MySQL 压力
  ✅ 计数查询走 Redis（实时），MySQL 只做"谁点过赞"的备份查询

MySQL 批量 UPSERT 要点：
  → INSERT ... ON DUPLICATE KEY UPDATE 保证幂等
  → 每批 500 条，避免长事务锁表
  → 消费者失败时重新放回 buffer，保证 at-least-once

评论为什么不用 Kafka？
  ✅ 评论点赞无热点，单条评论的 QPS 极低
  ✅ @Async + 直接写 Cassandra 足够，引入 Kafka 增加无谓延迟
  ✅ Cassandra counter 列的原子性代替了 Lua 脚本的作用
```

---

### 9.3 Cassandra 与 MySQL 的取舍

#### 本项目各模块的数据库选择

| 模块 | 使用的数据库 | 原因 |
|------|------------|------|
| `auth-service` | MySQL | 用户认证数据，强一致性，关联查询（user → role） |
| `leaf-service` | MySQL | `leaf_alloc` 表需要 `SELECT ... FOR UPDATE` 行锁保证号段唯一性 |
| `note-service` | Cassandra + Redis | 笔记内容、评论、点赞——写多读多，无 JOIN 需求，需水平扩展 |
| `upload-service` | MinIO（对象存储） | 文件存储，不走数据库 |

#### 决策框架

```
需要强一致性事务？
  ├─ 是 → MySQL
  └─ 否 → 查询模式是否固定且简单？
            ├─ 是 → 数据量大吗？
            │       ├─ 是 → Cassandra
            │       └─ 否 → MySQL 足够
            └─ 否 → MySQL（需要灵活查询）
```

#### 具体场景对照

| 场景 | 选择 | 原因 |
|------|------|------|
| 用户注册/登录 | **MySQL** | 强一致性，不能"最终一致"导致用户重复注册 |
| 分布式 ID 号段分配 | **MySQL** | 需要 `FOR UPDATE` 行锁保证号段不重复 |
| 笔记内容存储 | **Cassandra** | 按 `note_id` 单主键查询，模式固定，量大可扩展 |
| 评论列表 | **Cassandra** | 按 `note_id` 分区，天然有序，一次查询拿全部评论 |
| 点赞计数 | **Redis** | 热点数据，纯内存操作，避免任何磁盘数据库的瓶颈 |
| 笔记点赞/收藏记录 | **Redis → MySQL** | 异步合并写入，方便未来与用户表、笔记表 JOIN 做推荐 |
| 评论点赞记录 | **Redis → Cassandra** | 与评论同 keyspace，按 `note_id` 分区天然聚合 |
| 文件/图片 | **MinIO** | 对象存储，不占数据库空间，直接 HTTP 访问 |

#### 如果全用 MySQL 会怎样

| 问题 | 影响 |
|------|------|
| 笔记表 1000 万行 | MySQL 仍可应对（索引良好），但分页查询随 offset 增大而变慢 |
| 评论表 1 亿行 | 需要分库分表，跨分片按 `note_id` 查评论变得复杂 |
| 每秒 1 万次点赞 | `counter` 表行锁成为全局瓶颈，需要引入 Redis 挡读挡写 |
| 评论按笔记 ID 排序 | MySQL 天然支持，这点比 Cassandra 方便 |

#### 结论：三库各司其职

| 数据库 | 定位 | 本项目中承担 |
|--------|------|-------------|
| **MySQL** | 强一致、事务型、关联查询 | 用户认证 + 分布式 ID + **笔记互动记录** |
| **Cassandra** | 大数据量、简单查询、水平扩展 | 笔记内容 + 评论 + **评论互动记录** |
| **Redis** | 热点数据、高并发、原子操作 | 互动计数 + 互动状态 + 待同步队列 |

**为什么笔记互动记录放在 MySQL 而不是 Cassandra？**
- 笔记互动数据未来需要与用户表 JOIN（"我点赞过的笔记"、"与我互动过的用户"）
- 互动记录量级可控（百万笔记 × 平均百次互动 ≈ 亿级，MySQL 分表可应对）
- 评论互动记录放在 Cassandra 是因为它和评论共享 `note_id` 分区键，属于同一查询模式

---

## 10. 本项目数据模型一览

### Cassandra 表

| 表名 | Partition Key | Clustering Key | 用途 |
|------|--------------|----------------|------|
| `note` | `id` | — | 笔记存储，按 ID 查询 |
| `comment` | `note_id` | `comment_id` | 笔记评论，按笔记 ID 查询 |
| `comment_like` | `comment_id` | `user_id` | 评论点赞用户记录 |
| `comment_like_count` | `comment_id` | — (counter) | 评论点赞计数器（Cassandra counter 类型） |

### MySQL 表

| 表名 | 主键 | 用途 |
|------|------|------|
| `interaction_record` | `(target_type, target_id, interaction_type, user_id)` | 笔记点赞/收藏记录，Kafka 消费者窗口聚合批量 UPSERT |

### 数据流

```
笔记点赞（Redis + Kafka → MySQL）
  → gateway:8080 POST /api/interaction/toggle
    → Dubbo → InteractionService.toggle()
        ├─ Redis Lua 原子 toggle（like:count:note:42, like:users:note:42）
        └─ Kafka 发送 InteractionEvent
              → InteractionEventConsumer 窗口聚合
              → @Scheduled 30s 批量 UPSERT MySQL interaction_record
  ← ToggleResponse {active, count, action}

评论点赞（@Async → Cassandra counter + comment_like）
  → gateway:8080 POST /api/interaction/toggle
    → Dubbo → InteractionService.toggleComment()
        └─ CommentLikeService.toggleAsync()
            ├─ comment_like 表 INSERT/UPDATE 用户状态
            └─ comment_like_count 表 UPDATE like_count = like_count ± 1（counter）
  ← ToggleResponse {active, count, action} (同步返回预估计数)

用户查看笔记详情
  → gateway:8080 GET /api/note/detail?noteId=42
    → CompletableFuture 并行加载:
        ├─ NoteRepository.findById(42)              // Cassandra 查笔记
        └─ CommentRepository.findByNoteId(42)       // Cassandra 查评论
    → InteractionService.batchStatus("LIKE", ...)    // Redis 查笔记点赞数
    → commentLikeService.batchGetCounts(...)          // Cassandra 查评论点赞数
  ← Result<NoteDetailResponse> (含 likeCount, favoriteCount, comments)
```

---

## 11. 推荐学习路径

1. **理解核心概念**：Partition Key、Clustering Key、数据分布（本文 1-3 节）
2. **动手实践**：用 Docker 起一个 Cassandra，执行 CQL 增删改查
3. **数据建模**：学习查询优先的建模思维（本文第 6 节）
4. **深入阅读**：[Cassandra 官方文档](https://cassandra.apache.org/doc/latest/)、[DataStax Academy](https://www.datastax.com/learn/cassandra-fundamentals)
