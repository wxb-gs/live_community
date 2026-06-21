# note-service 重构分析

## 1. 当前架构 vs 目标架构

### 当前架构（有问题）

```
笔记 (Note):
  主存储: Cassandra (NoteEntity + NoteRepository)
  辅助:   MySQL     (NoteMysqlRepository.upsert, 双写)
  文件:   MinIO     (预签名URL)
  读取:   Cassandra (findById)

评论 (Comment):
  主存储: Cassandra (CommentEntity + CommentRepository)

笔记点赞/收藏:
  热路径: Redis (Lua 原子 toggle)
  冷路径: Kafka → MySQL (interaction_record + note.like_count)

评论点赞:
  Cassandra (comment_like + comment_like_count counter)
```

**核心问题**：笔记写了两个数据库（Cassandra + MySQL），但只从 Cassandra 读。MySQL 里的笔记数据只是"备份"，从未被查询使用——这是浪费，且两个库之间存在潜在不一致。

### 目标架构（你的需求）

```
笔记 (Note):
  主存储: MySQL  (元数据: 标题/内容/状态/标签)
  文件:   MinIO (OSS, 附件/图片)
           ↑ 不再写 Cassandra

评论 (Comment):
  主存储: Cassandra  (不变)

笔记点赞/收藏:
  Kafka 异步 → MySQL  (不变)

评论点赞:
  Cassandra  (不变)
```

### 对比

| 实体 | 当前 | 目标 | 变化 |
|------|------|------|------|
| 笔记元数据 | Cassandra + MySQL 双写 | **仅 MySQL** | 🔴 去掉 Cassandra |
| 笔记文件 | MinIO | MinIO | 🟢 不变 |
| 评论 | Cassandra | Cassandra | 🟢 不变 |
| 笔记点赞/收藏 | Redis → Kafka → MySQL | Redis → Kafka → MySQL | 🟢 不变 |
| 评论点赞 | Cassandra | Cassandra | 🟢 不变 |

---

## 2. 需要改什么

### 2.1 NoteMysqlRepository — 加读方法

当前只有写（upsert/addViewCount/addLikeCount/delete），需要新增：

```java
Optional<NoteRow> findById(long noteId);          // 按ID查
List<NoteRow> findPublished(int limit);            // 已发布列表
List<NoteRow> listByPage(int offset, int limit);   // 分页查询
```

`NoteRow` 是 MySQL 查询结果的对象映射（不需要全功能 JPA，用 `JdbcTemplate` + `RowMapper` 或 `BeanPropertyRowMapper`）。

### 2.2 NoteService — 读写对象从 Cassandra 切到 MySQL

| 方法 | 当前 | 改成 |
|------|------|------|
| `createDraft` | `noteRepository.save` + `noteMysqlRepository.upsert` | **仅** `noteMysqlRepository.upsert` |
| `publishNote` | `noteRepository.findById` (Cassandra) | **`noteMysqlRepository.findById`** (MySQL) |
| | `noteRepository.save` (Cassandra) | **`noteMysqlRepository.upsert`** (MySQL) |
| `getNoteDetail` | `noteRepository.findById` (Cassandra) | **`noteMysqlRepository.findById`** (MySQL) |
| `listPublishedNotes` | `noteRepository.findPublished` (Cassandra) | **`noteMysqlRepository.findPublished`** (MySQL) |

MinIO 预签名 URL 生成逻辑不变。

### 2.3 可以删除的代码

| 文件 | 原因 |
|------|------|
| `NoteEntity.java` | Cassandra 实体，不再需要 |
| `NoteRepository.java` | Cassandra Repository，不再需要 |
| `NoteRepository.findPublished()` 方法 | Cassandra CQL 查询 |
| `NoteService` 中的 `NoteEntity` import | 替换为 MySQL Row |

### 2.4 不用改的

- `CommentEntity` / `CommentRepository` / `CommentLikeEntity` / `CommentLikeCountEntity` — 全保留
- `InteractionService` / `InteractionEventProducer` / `InteractionEventConsumer` — 全保留
- `ViewCountSyncScheduler` — 保留
- `MinioConfig` — 保留
- `NoteMysqlRepository` — 加读方法，写方法保留

---

## 3. 改动范围汇总

```
修改文件:
  note-service/
    ├── NoteService.java               ← 核心改动：读从 MySQL 替代 Cassandra
    ├── NoteMysqlRepository.java        ← 新增 findById / findPublished / listByPage
    └── NoteDetailResponse.java (可能)  ← 保持兼容

删除文件:
  note-service/
    ├── NoteEntity.java                 ← Cassandra 实体（可保留做历史迁移参考）
    └── NoteRepository.java             ← Cassandra Repository

不需要改:
  note-service/
    ├── CommentEntity.java              ✅ 保留
    ├── CommentRepository.java          ✅ 保留
    ├── CommentLikeEntity.java          ✅ 保留
    ├── CommentLikeCountEntity.java     ✅ 保留
    ├── CommentLikeRepository.java      ✅ 保留
    ├── CommentLikeService.java         ✅ 保留
    ├── InteractionService.java         ✅ 保留
    ├── InteractionEventProducer.java   ✅ 保留
    ├── InteractionEventConsumer.java   ✅ 保留
    ├── ViewCountSyncScheduler.java     ✅ 保留
    └── MinioConfig.java                ✅ 保留
```

---

## 4. 影响评估

| 维度 | 影响 |
|------|------|
| **API 兼容** | 无变化，Controller/RPC 接口不变 |
| **数据一致性** | ✅ 改善：单一写入源 MySQL，消除 Cassandra/MySQL 不一致风险 |
| **搜索链路** | ✅ 不变：MySQL binlog → Canal → Kafka → ES，不受影响 |
| **Cassandra** | `note` 表不再写入，可保留历史数据，不影响 comment 相关表 |
| **MySQL** | `note` 表从现在起是读写主角，需要关注索引优化 |

---

## 5. 实施步骤

1. `NoteMysqlRepository` 增加 `findById(long)`、`findPublished(int)` 方法
2. 创建简单的 `NoteRow` 数据对象（替代 NoteEntity）
3. 修改 `NoteService` 四个方法，把 Cassandra 调用替换为 MySQL 调用
4. 编译验证
5. Docker Compose 重启验证

---

## 6. 未明确的问题

1. **Cassandra 的 `note` 表要不要删？** — 建议保留，线上数据可做历史备份
2. **列表查询需要分页（OFFSET/LIMIT）吗？** — 当前 `findPublished` 只有 limit，是否需要 page 参数？
3. **是否需要 JPA 替代 JdbcTemplate？** — 当前用手写 SQL 足够，暂时不引入 JPA 减少依赖
