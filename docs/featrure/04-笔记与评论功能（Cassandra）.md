# 笔记与评论功能（Cassandra）

## 1. 功能概述

### 1.1 业务功能

笔记服务提供完整的笔记生命周期管理和评论功能：

```
┌─────────────────────────────────────────────────┐
│                   NoteService                    │
├─────────────────────────────────────────────────┤
│ createDraft  →  创建草稿（保存到 Cassandra）      │
│ publishNote  →  发布笔记（生成 MinIO 上传 URL）   │
│ getNoteDetail → 查看笔记 + 评论列表（并发查询）    │
│ addComment   →  添加评论（leaf 生成 comment ID）  │
└─────────────────────────────────────────────────┘
```

**状态机**：

```
DRAFT  ──publishNote()──→  PUBLISHED
  │                            │
  │  创建笔记草稿              │  可查看、可评论
  │  未关联文件                │  关联了 MinIO objectKey
  └────────────────────────────┘
```

### 1.2 为什么用 Cassandra？

| 数据库 | 写性能 | 读性能 | 水平扩展 | 适合场景 |
|--------|--------|--------|---------|---------|
| MySQL | 中等 | 高（B+Tree） | 困难（分库分表） | 事务性业务、关联查询 |
| **Cassandra** | **极高**（LSM Tree） | 高（主键查询） | **天然支持** | 时序数据、高写入、海量数据 |
| MongoDB | 中等 | 高（单文档） | 容易（分片） | 灵活 schema、JSON 文档 |
| Redis | 极高 | 极高 | 支持（Cluster） | 缓存、计数器 |

**本项目选 Cassandra 的原因**：
1. 笔记和评论是典型的高写入场景（大量用户同时发笔记、评论）
2. 查询模式简单：按 noteId 查笔记，按 noteId 查评论列表（没有复杂的 JOIN）
3. Cassandra 的 LSM Tree 写入非常快（顺序写 + Memtable + SSTable）
4. 水平扩展天然支持（加节点 = 自动 rebalance）
5. 与 Spring Data Cassandra 集成良好

---

## 2. 详细实现分析

### 2.1 数据模型设计

**Cassandra 数据建模的核心原则**：先设计查询，再设计表。

本项目的查询需求：
1. 按 `noteId` 查笔记详情——主键查询
2. 按 `noteId` 查该笔记的所有评论——分区查询 + 时间排序
3. 按 `userId` 查笔记（有索引）——辅助查询
4. 按 `status` 查笔记——辅助查询

#### 笔记表

```sql
CREATE TABLE note (
    id BIGINT PRIMARY KEY,        -- 主键 = 分区键
    user_id BIGINT,
    title TEXT,
    content TEXT,
    summary TEXT,
    object_key TEXT,              -- MinIO 中的存储路径
    status TEXT,                  -- DRAFT / PUBLISHED
    created_at BIGINT,
    updated_at BIGINT
);
CREATE INDEX ON note (user_id);   -- 辅助索引：按用户查笔记
CREATE INDEX ON note (status);    -- 辅助索引：按状态过滤
```

**为什么用 BIGINT 存时间戳而不是 TIMESTAMP？**
1. 避免时区问题（TIMESTAMP 在不同客户端可能转换）
2. 与 Leaf 生成的时间戳一致
3. 在应用层处理时间格式化更灵活

#### 评论表

```sql
CREATE TABLE comment (
    note_id BIGINT,               -- 分区键（Partition Key）
    comment_id BIGINT,            -- 聚簇键（Clustering Key）
    user_id BIGINT,
    content TEXT,
    created_at BIGINT,
    PRIMARY KEY (note_id, comment_id)  -- 复合主键
);
```

**Cassandra 复合主键的含义**：

```
PRIMARY KEY (note_id, comment_id)
         ↑           ↑
    Partition Key  Clustering Key
    (决定数据在哪个节点)  (决定分区内的排序)
```

- 同一个 `note_id` 的所有评论存储在同一个分区内
- 分区内按 `comment_id` 排序（默认升序）
- 查询 `WHERE note_id = ?` 只命中一个节点，效率极高

**为什么 comment_id 用 Leaf 号段而不用 Cassandra 的 UUID？**
- Leaf 的号段模式生成趋势递增的 ID，作为 Clustering Key 可以获得更好的排序效果
- Cassandra 的 `uuid()` 函数生成的是 UUID v4（随机），作为排序键会导致频繁的 SSTable 合并

### 2.2 Spring Data Cassandra 配置

```java
@Configuration
@EnableCassandraRepositories(basePackages = "com.example.note.repository")
public class CassandraConfig {

    @Bean
    public CqlSession cqlSession(CassandraKeyspaceInitializer init) {
        return new CqlSessionBuilder()
                .addContactPoint(new InetSocketAddress(contactPoints, port))
                .withLocalDatacenter(localDatacenter)
                .withKeyspace(keyspace)
                .build();
    }
    // 同时配置 CqlTemplate, CassandraTemplate, CassandraAdminTemplate
}
```

**为什么需要手动配置 Bean 而不是完全依赖 Spring Boot 自动配置？**

Spring Boot 的 Cassandra 自动配置在某些版本中存在局限性：
1. Keyspace 的创建时机不容易控制（需要在 session 建立前存在）
2. 需要 `CassandraKeyspaceInitializer` 确保 keyspace 先创建
3. 显式配置能确保 keyspace → session → schema 的初始化顺序

### 2.3 Keyspace 和 Schema 的自动初始化

```java
// CassandraKeyspaceInitializer - 实现 InitializingBean，在 Bean 初始化完成后执行
@Override
public void afterPropertiesSet() {
    try (CqlSession initSession = new CqlSessionBuilder()...build()) {
        initSession.execute("CREATE KEYSPACE IF NOT EXISTS notes "
            + "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
    }
}

// CassandraSchemaInitializer - 在 ApplicationReadyEvent 时执行
@EventListener(ApplicationReadyEvent.class)
public void initSchema() {
    cqlSession.execute("CREATE TABLE IF NOT EXISTS note (...");
    cqlSession.execute("CREATE TABLE IF NOT EXISTS comment (...");
    cqlSession.execute("CREATE INDEX IF NOT EXISTS ON note (user_id)");
    cqlSession.execute("CREATE INDEX IF NOT EXISTS ON note (status)");
}
```

**为什么分成两个阶段？**
- Keyspace 必须在 CqlSession 连接到 keyspace 之前存在
- Schema（表）必须在 CqlSession 建立之后才能创建（DDL 需要在 keyspace 上下文中执行）
- `ApplicationReadyEvent` 确保所有 Bean 都已就绪，此时 CqlSession 已建立

**`SimpleStrategy` + `replication_factor=1`**：
- `SimpleStrategy`：简单复制策略，假设所有节点在同一个数据中心
- `replication_factor=1`：只有一份数据副本，适合开发/测试环境
- 生产环境应改为 `NetworkTopologyStrategy` + `replication_factor=3`

### 2.4 笔记详情查询的并发优化

```java
public NoteDetailResponse getNoteDetail(Long noteId) {
    // 并发查询笔记和评论
    CompletableFuture<NoteEntity> noteFuture = CompletableFuture.supplyAsync(() ->
            noteRepository.findById(noteId)
                    .orElseThrow(() -> new RuntimeException("Note not found: " + noteId)));

    CompletableFuture<List<CommentEntity>> commentsFuture = CompletableFuture.supplyAsync(() -> {
        try {
            return commentRepository.findByNoteId(noteId);
        } catch (Exception e) {
            log.warn("Failed to load comments for noteId={}", noteId, e);
            return Collections.emptyList();
        }
    });

    NoteEntity note = noteFuture.join();           // 等待笔记查询完成
    List<CommentEntity> commentEntities = commentsFuture.join(); // 等待评论查询完成
    // ...
}
```

**为什么用 CompletableFuture 并发查询？**

两个查询是独立的：
- 查 note 表：`SELECT * FROM note WHERE id = ?`
- 查 comment 表：`SELECT * FROM comment WHERE note_id = ?`

串行执行耗时 = T_note + T_comment（约 20ms + 10ms = 30ms）
并发执行耗时 = max(T_note, T_comment)（约 20ms）

在高并发下，这 10ms 的差异对 API 响应时间有明显的累积效果。

**为什么评论查询异常时返回空列表而不是抛异常？**
- 降级策略：笔记内容是核心数据，评论是辅助数据
- 如果 Cassandra 有压力，宁可评论先不展示，也不能让整个接口报错
- 这是典型的 "部分失败，整体可用" 的弹性模式

### 2.5 草稿与发布的业务逻辑

```java
// 创建草稿：只需要基本信息
public CreateDraftResponse createDraft(CreateDraftRequest request) {
    IdResponse idResp = leafRpcService.generateSegmentId("note");
    long noteId = idResp.getId();
    // ... 设置基本信息，status = "DRAFT"
    noteRepository.save(entity);
    return new CreateDraftResponse(noteId, "DRAFT");
}

// 发布笔记：关联文件 + 生成上传 URL
public NoteDetailResponse publishNote(PublishNoteRequest request) {
    NoteEntity entity = noteRepository.findById(request.getNoteId())
            .orElseThrow(() -> new RuntimeException("Note not found"));
    if (!"DRAFT".equals(entity.getStatus())) {
        throw new RuntimeException("Note is not in DRAFT status");
    }
    // 生成 MinIO 上传 URL（10 分钟有效）
    String uploadUrl = minioClient.getPresignedObjectUrl(...
            .expiry(10, TimeUnit.MINUTES)...);
    entity.setObjectKey(objectKey);
    entity.setStatus("PUBLISHED");
    noteRepository.save(entity);
    // 返回笔记详情 + 上传 URL
}
```

**草稿-发布模式的设计考量**：
- 草稿不关联文件，仅保存文字内容（类似于 CMS 的"保存草稿"功能）
- 发布时生成预签名 URL 而非直接上传，文件由客户端直接上传到 MinIO
- 发布 URL 有效期 10 分钟（比上传 URL 的 5 分钟长），因为用户可能需要时间准备文件
- 发布后的笔记仍然可以修改（调用 publishNote 再次发布覆盖）

---

## 3. 实现难点

### 3.1 Cassandra 查询模型与关系型思维的差异

**典型的错误思维**（来自 SQL）：
```sql
SELECT n.*, c.*, u.*
FROM note n
JOIN comment c ON n.id = c.note_id
JOIN user u ON n.user_id = u.id
WHERE n.id = ?
```

**Cassandra 的正确做法**：
```
// 分两个独立查询（不支持的 JOIN）
note = SELECT * FROM note WHERE id = ?
comments = SELECT * FROM comment WHERE note_id = ?
// 在应用层组装
```

**关键认知**：Cassandra 不支持 JOIN、不支持子查询、不支持 `OR` 条件。所有数据关联必须在应用层完成。这是分布式数据库的性能代价——你用 JOIN 的便利性换取了水平扩展的能力。

### 3.2 二级索引的陷阱

```sql
CREATE INDEX ON note (user_id);
```

**Cassandra 二级索引的问题**：
1. 索引不存储原始数据，查询时需要先扫索引再回表
2. 索引在后台异步更新，可能读到不一致的数据
3. 高基数（cardinality）字段的索引效率很低
4. 索引数据分布在所有节点上，不能精确路由

**最佳实践**：
- 如果经常按 `user_id` 查询，应该建一个独立的查询表 `note_by_user`，以 `user_id` 为分区键
- 本项目使用索引是为了简化实现，适合笔记数量不大的场景
- 生产环境建议建物化视图（Materialized View）或反范式表

### 3.3 跨服务 RPC 调用链

```
note-service (NoteService)
  → @DubboReference LeafRpcService.generateSegmentId("note")
    → leaf-service:20881
      → SegmentIdGenerator.getId("note")
        → MySQL:3306 (UPDATE leaf_alloc + SELECT)
  ← IdResponse
  → Cassandra:9042 (INSERT INTO note ...)
```

**这个调用链的风险点**：
1. `leafRpcService` 不可用 → 无法生成 noteId → 创建笔记失败
2. RPC 调用超时 → 需要合理设置 Dubbo 超时时间
3. 分布式事务：如果 ID 生成成功但 Cassandra 写入失败，ID 就浪费了（但这是可接受的）

---

## 4. 面试准备

### 4.1 高频问题

**Q: Cassandra 和 MySQL 有什么本质区别？**
> 1. **存储引擎**：MySQL 用 B+Tree（适合读），Cassandra 用 LSM Tree（适合写）
> 2. **扩展方式**：MySQL 垂直扩展为主（分库分表复杂），Cassandra 水平扩展天然支持
> 3. **一致性**：MySQL 默认强一致性（ACID），Cassandra 默认最终一致性（可调）
> 4. **查询能力**：MySQL 支持复杂 SQL（JOIN、子查询、聚合），Cassandra 只支持主键查询和有限的二级索引
> 5. **使用场景**：MySQL 适合事务性 OLTP，Cassandra 适合高写入的时序数据和日志类数据

**Q: 为什么评论表的 PRIMARY KEY 是 (note_id, comment_id)？**
> - `note_id` 是分区键：同一个笔记的所有评论存储在同一节点，查询时只访问一个节点
> - `comment_id` 是聚簇键：分区内按 comment_id 排序，保证评论按时间排序
> - 这个设计保证"查某个笔记的所有评论"这个最高频查询只命中一个节点，效率最高
> - 代价：如果要按 `user_id` 查所有评论很困难（不是分区键）。这是 Cassandra "围绕查询设计表" 的典型 tradeoff

**Q: CompletableFuture 并发查询相比串行查询有什么优势？**
> 串行：`note查询(20ms) + comment查询(10ms) = 30ms`
> 并发：`max(20ms, 10ms) = 20ms`
> 1. 减少约 33% 的响应时间
> 2. 两个查询不相互依赖，天然适合并发
> 3. 配合适当的线程池（ForkJoinPool），资源开销很小
> 4. 在实际的高并发场景中，减少 10ms 对 P99 延迟有显著改善

**Q: @DubboReference 放在 NoteService 而不是 NoteRpcServiceImpl 中，这种做法合理吗？**
> 合理。`NoteRpcServiceImpl` 是 Dubbo 服务提供者的入口（`@DubboService`），它接收外部 RPC 调用后委托给 `NoteService` 处理业务逻辑。`NoteService` 是一个普通的 Spring `@Service`，它可以同时依赖本地 Bean（NoteRepository）和远程 Dubbo 服务（LeafRpcService）。这种分层让 RPC 层（`NoteRpcServiceImpl`）只管协议适配，业务逻辑层（`NoteService`）只管业务流程，职责清晰。

### 4.2 进阶讨论

**Q: 如果要实现笔记的"点赞"功能，在 Cassandra 中怎么设计？**
> 点赞是一个计数器，在 Cassandra 中可以用 Counter 类型：
> ```sql
> CREATE TABLE note_likes (
>     note_id BIGINT PRIMARY KEY,
>     like_count COUNTER
> );
> UPDATE note_likes SET like_count = like_count + 1 WHERE note_id = ?;
> ```
> COUNTER 类型支持原子递增，天然适合点赞/计数场景。但 COUNTER 不能与其他列混合使用（要么全 COUNTER，要么全普通列）。所以一般建独立的 counter 表。

**Q: 为什么要单独写 CassandraKeyspaceInitializer 而不配置成 Bean 的初始化方法？**
> Keyspace 的创建必须发生在 Session 连接到该 keyspace 之前。如果 Keyspace 不存在，`CqlSessionBuilder.withKeyspace(keyspace)` 会失败。所以需要一个**先于 CqlSession 执行的初始化步骤**。实现 `InitializingBean` 的 `afterPropertiesSet` 在 Bean 初始化完成后、依赖注入后立即执行，时机恰当。

---

## 5. 关键代码位置

| 文件 | 作用 |
|------|------|
| `note-service/service/NoteService.java` | 核心业务逻辑：CRUD、并发查询、状态转换 |
| `note-service/rpc/NoteRpcServiceImpl.java` | Dubbo RPC 服务暴露 |
| `note-service/controller/NoteController.java` | REST 直连接口 |
| `note-service/entity/NoteEntity.java` | 笔记 Cassandra 实体 |
| `note-service/entity/CommentEntity.java` | 评论 Cassandra 实体（复合主键） |
| `note-service/repository/NoteRepository.java` | 笔记 Repository |
| `note-service/repository/CommentRepository.java` | 评论 Repository（含 CQL 自定义查询） |
| `note-service/config/CassandraConfig.java` | Cassandra Session + Template 配置 |
| `note-service/config/CassandraKeyspaceInitializer.java` | Keyspace 自动创建 |
| `note-service/config/CassandraSchemaInitializer.java` | Schema（表+索引）自动创建 |
| `gateway/controller/NoteController.java` | 对外 REST 入口（Dubbo 消费方） |
