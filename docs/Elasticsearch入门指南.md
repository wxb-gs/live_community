# Elasticsearch 入门指南

## 1. 什么是 Elasticsearch

Elasticsearch（简称 ES）是一个**分布式实时搜索与分析引擎**，基于 Apache Lucene 构建。它可以对海量数据进行近实时的全文搜索、结构化搜索和分析。

### 核心特征

| 特征 | 说明 |
|------|------|
| **全文搜索** | 对文本进行分词、倒排索引，毫秒级返回结果 |
| **分布式架构** | 数据自动分片和复制，水平扩展无上限 |
| **近实时** | 写入后 ~1s 即可搜索（refresh 间隔） |
| **RESTful API** | 所有操作通过 JSON over HTTP，客户端无关 |
| **聚合分析** | 类似 SQL GROUP BY 的统计能力，支持嵌套、管道聚合 |
| **多语言支持** | 内置分词器 + 插件（IK 中文分词）|

---

## 2. Elasticsearch vs 关系型数据库

| | Elasticsearch | MySQL |
|---|---|---|
| **搜索方式** | 倒排索引，基于词（term）搜索 | B+Tree 索引，前缀匹配 LIKE |
| **模糊搜索** | ✅ 天然支持，关联度排序 | ❌ `LIKE '%keyword%'` 全表扫描 |
| **排序** | 关联度评分（TF-IDF/BM25）+ 自定义 | ORDER BY 指定列 |
| **聚合** | 实时海量聚合，内存 + 磁盘混合 | GROUP BY，大数据量下性能陡降 |
| **事务** | ❌ 不支持 ACID 事务 | ✅ 完整 ACID |
| **GROUP BY** | ✅ 嵌套聚合、日期直方图、百分位 | ✅ 标准 SQL GROUP BY |
| **数据模型** | JSON 文档（无 Schema 约束） | 严格的表结构 + 列类型 |
| **扩展** | 水平扩展，加节点自动 rebalance | 分库分表（应用层介入） |

> ES 不是为了替换 MySQL，而是**互补**——MySQL 负责事务操作（OLTP），ES 负责搜索和分析（OLAP）。

---

## 3. 核心概念

### 3.1 Cluster（集群）

由一个或多个节点组成，通过 `cluster.name` 标识。节点间通过 Zen Discovery / 单节点模式通信。

### 3.2 Node（节点）

单个 ES 实例。类型：
- **Master-eligible**：管理集群状态（索引创建/删除、分片分配）
- **Data**：存储数据、执行搜索
- **Ingest**：预处理管道（字段提取、转换）
- **Coordinating**：请求分发，不存数据

### 3.3 Index（索引）

相当于 MySQL 的 **Database**。一组有相似结构的文档的集合。名字必须全小写。

### 3.4 Shard（分片）与 Replica（副本）

```
Index "posts"
├── Primary Shard 0  ───→  Node A
│   └── Replica 0     ───→  Node B
├── Primary Shard 1  ───→  Node B
│   └── Replica 1     ───→  Node A
└── Primary Shard 2  ───→  Node C
    └── Replica 2     ───→  Node A
```

| | Primary Shard | Replica Shard |
|---|---|---|
| **数量** | 创建索引时定义，后续不可改 | 可动态调整 |
| **写入** | 必须经过 primary | 从 primary 同步，不能直接写入 |
| **搜索** | 参与搜索 | 参与搜索（提高读吞吐） |
| **容灾** | 丢失 = 数据不完整 | primary 宕机时 replica 提升为 primary |

### 3.5 Document（文档）

相当于 MySQL 的 **Row**，JSON 格式。每个文档有唯一的 `_id`。

```json
{
  "_index": "posts",
  "_id": "1",
  "_source": {
    "title": "Spring Cloud 入门",
    "content": "Spring Cloud Alibaba 是...",
    "tags": ["spring", "微服务"],
    "created_at": "2025-01-01T00:00:00Z"
  }
}
```

### 3.6 Mapping（映射）

相当于 MySQL 的 **Schema**，定义字段类型和索引行为。

```json
{
  "mappings": {
    "properties": {
      "title":    { "type": "text", "analyzer": "ik_max_word" },
      "content":  { "type": "text", "analyzer": "ik_smart" },
      "tags":     { "type": "keyword" },
      "view_count": { "type": "integer" },
      "created_at": { "type": "date" }
    }
  }
}
```

**核心字段类型**：

| 类型 | 说明 | 使用场景 |
|------|------|---------|
| `text` | 全文索引，会分词 | 标题、正文 |
| `keyword` | 精确值，不分词 | 标签、状态、ID |
| `integer` / `long` | 整数 | 计数、ID |
| `float` / `double` | 浮点数 | 评分 |
| `date` | 日期 | 时间范围过滤 |
| `boolean` | 布尔值 | 开关 |
| `object` / `nested` | 嵌套对象 | 复杂结构 |

> `text` 和 `keyword` 是初学者最容易搞混的——搜"Spring"用 `text`，精确匹配"Spring"用 `keyword`。

### 3.7 倒排索引（Inverted Index）

这是 ES 搜索快的根本原因。

```
正排索引（MySQL B+Tree）：文档 → 词
  文档1 → ["Spring", "Cloud", "入门"]
  文档2 → ["Spring", "Boot", "实战"]

倒排索引（ES Lucene）：词 → 文档
  "Spring" → [文档1, 文档2]
  "Cloud"  → [文档1]
  "Boot"   → [文档2]
  "入门"   → [文档1]
  "实战"   → [文档2]
```

搜索 "Spring" 时直接查倒排索引，O(1) 定位，不需要遍历所有文档。

---

## 4. Docker 安装

```bash
docker run -d \
  --name es \
  -p 9200:9200 \
  -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  elasticsearch:8.15.0

# 测试
curl http://localhost:9200
```

> ES 8.x 默认启用安全认证。测试环境可通过 `xpack.security.enabled=false` 关闭。

安装 IK 中文分词：

```bash
docker exec -it es bash
./bin/elasticsearch-plugin install https://get.infini.cloud/elasticsearch/analysis-ik/8.15.0
```

---

## 5. 索引操作

### 5.1 创建索引

```bash
# 创建索引（无 mapping）
curl -X PUT "localhost:9200/posts"

# 创建索引（含 mapping 和 settings）
curl -X PUT "localhost:9200/posts" -H 'Content-Type: application/json' -d'
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "refresh_interval": "1s"
  },
  "mappings": {
    "properties": {
      "title":    { "type": "text",   "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "content":  { "type": "text",   "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "tags":     { "type": "keyword" },
      "status":   { "type": "keyword" },
      "author_id":{ "type": "long" },
      "view_count":{ "type": "integer" },
      "created_at": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" }
    }
  }
}'
```

**Settings 关键参数**：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `number_of_shards` | 1 | 主分片数，创建后不可改 |
| `number_of_replicas` | 1 | 每个主分片的副本数 |
| `refresh_interval` | 1s | 写入后多久可搜索（调大减少 I/O） |

### 5.2 查看索引

```bash
curl -X GET "localhost:9200/posts"          # 单个索引
curl -X GET "localhost:9200/_cat/indices?v" # 所有索引（表格形式）
```

### 5.3 修改索引

```bash
# 改副本数（settings 中的动态参数可以改）
curl -X PUT "localhost:9200/posts/_settings" -H 'Content-Type: application/json' -d'
{ "number_of_replicas": 2 }'

# 新增字段
curl -X PUT "localhost:9200/posts/_mapping" -H 'Content-Type: application/json' -d'
{
  "properties": {
    "summary": { "type": "text", "analyzer": "ik_smart" }
  }
}'

# 已有字段不能改类型，只能用 reindex 迁移数据
```

### 5.4 删除索引

```bash
curl -X DELETE "localhost:9200/posts"
```

---

## 6. 文档操作

### 6.1 创建/写入（Index）

```bash
# PUT 指定 ID（幂等）
curl -X PUT "localhost:9200/posts/_doc/1" -H 'Content-Type: application/json' -d'
{
  "title": "Spring Cloud 入门指南",
  "content": "Spring Cloud Alibaba 是一套微服务解决方案...",
  "tags": ["spring", "微服务"],
  "status": "published",
  "author_id": 1001,
  "view_count": 0,
  "created_at": "2025-06-21 10:00:00"
}'

# POST 自动生成 ID
curl -X POST "localhost:9200/posts/_doc" -H 'Content-Type: application/json' -d'{...}'
```

### 6.2 查询文档

```bash
# 按 ID 查询
curl -X GET "localhost:9200/posts/_doc/1"

# 判断是否存在
curl -I "localhost:9200/posts/_doc/1"
```

### 6.3 更新文档

```bash
# 更新部分字段
curl -X POST "localhost:9200/posts/_update/1" -H 'Content-Type: application/json' -d'
{
  "doc": { "view_count": 100, "title": "Spring Cloud 入门指南（修订版）" }
}'

# 使用脚本更新（原子递增）
curl -X POST "localhost:9200/posts/_update/1" -H 'Content-Type: application/json' -d'
{
  "script": { "source": "ctx._source.view_count += params.count", "params": { "count": 1 } }
}'
```

### 6.4 删除文档

```bash
curl -X DELETE "localhost:9200/posts/_doc/1"
```

### 6.5 批量操作（Bulk）

```bash
curl -X POST "localhost:9200/_bulk" -H 'Content-Type: application/json' --data-binary @bulk.json
```

`bulk.json` 格式（每一对：操作行 + 数据行）：

```json
{ "index":  { "_index": "posts", "_id": "1" } }
{ "title": "文档1", "content": "内容1", "tags": ["java"] }
{ "index":  { "_index": "posts", "_id": "2" } }
{ "title": "文档2", "content": "内容2", "tags": ["spring"] }
{ "update": { "_index": "posts", "_id": "1" } }
{ "doc": { "view_count": 10 } }
{ "delete": { "_index": "posts", "_id": "3" } }
```

> Bulk 比逐条写快 100 倍 +，**同步数据（Canal → ES）必须用 bulk**。

---

## 7. 搜索

### 7.1 基础搜索

```bash
# 全量查询（默认返回 10 条）
curl -X GET "localhost:9200/posts/_search"

# 搜索指定字段
curl -X GET "localhost:9200/posts/_search" -H 'Content-Type: application/json' -d'
{
  "query": {
    "match": { "title": "Spring Cloud 入门" }
  },
  "from": 0,
  "size": 20
}'
```

### 7.2 核心查询类型

| 查询 | 用途 | 示例 |
|------|------|------|
| `match` | 全文搜索，先分词再匹配 | `{ "match": { "title": "微服务" } }` |
| `multi_match` | 多字段全文搜索 | `{ "multi_match": { "query": "微服务", "fields": ["title^2", "content"] } }` |
| `term` | 精确值查询，不分词 | `{ "term": { "status": "published" } }` |
| `terms` | 多值精确匹配（IN） | `{ "terms": { "tags": ["java", "spring"] } }` |
| `range` | 范围查询 | `{ "range": { "view_count": { "gte": 100, "lte": 1000 } } }` |
| `exists` | 字段是否存在 | `{ "exists": { "field": "cover_image" } }` |
| `match_phrase` | 短语匹配（词序一致、相邻） | `{ "match_phrase": { "content": "微服务架构" } }` |
| `prefix` | 前缀匹配 | `{ "prefix": { "title": "Spring" } }` |
| `wildcard` | 通配符 | `{ "wildcard": { "title": "Spring*" } }` |
| `ids` | 按 ID 批量查询 | `{ "ids": { "values": ["1", "2", "3"] } }` |

### 7.3 Bool 组合查询

```json
{
  "query": {
    "bool": {
      "must": [
        { "match": { "title": "微服务" } }
      ],
      "filter": [
        { "term": { "status": "published" } },
        { "range": { "view_count": { "gte": 100 } } }
      ],
      "must_not": [
        { "term": { "tags": "draft" } }
      ],
      "should": [
        { "match": { "content": "Dubbo" } },
        { "term": { "tags": "热门" } }
      ],
      "minimum_should_match": 1
    }
  }
}
```

| 子句 | 作用 | 影响评分 |
|------|------|---------|
| `must` | 必须满足（AND） | ✅ 影响 |
| `filter` | 必须满足（AND），不评分 | ❌ 不影响 |
| `must_not` | 必须不满足（NOT） | ❌ 不影响 |
| `should` | 应该满足（OR） | ✅ 影响 |

> 能放 `filter` 就不要放 `must`——不计算评分，性能更好，结果会被缓存。

### 7.4 搜索结果结构

```json
{
  "hits": {
    "total": { "value": 123, "relation": "eq" },
    "max_score": 3.14,
    "hits": [
      {
        "_index": "posts",
        "_id": "1",
        "_score": 3.14,
        "_source": { "title": "...", "content": "..." },
        "highlight": { "title": ["<em>Spring Cloud</em> 入门"] }
      }
    ]
  },
  "aggregations": { ... }
}
```

### 7.5 高亮

```json
{
  "query": { "match": { "content": "微服务" } },
  "highlight": {
    "fields": {
      "title": { "number_of_fragments": 0 },
      "content": { "number_of_fragments": 3, "fragment_size": 100 }
    },
    "pre_tags": ["<em class='highlight'>"],
    "post_tags": ["</em>"]
  }
}
```

### 7.6 聚合（Aggregation）

```json
{
  "size": 0,
  "aggs": {
    "by_tag": {
      "terms": { "field": "tags", "size": 20 }
    },
    "avg_views": {
      "avg": { "field": "view_count" }
    },
    "by_month": {
      "date_histogram": {
        "field": "created_at",
        "calendar_interval": "month"
      },
      "aggs": {
        "avg_score": { "avg": { "field": "view_count" } }
      }
    }
  }
}
```

| 聚合类型 | 说明 | 类比 SQL |
|----------|------|---------|
| `terms` | 分组统计 | `GROUP BY` |
| `avg` / `sum` / `min` / `max` | 统计值 | `AVG()` / `SUM()` |
| `date_histogram` | 时间分组 | `GROUP BY DATE()` |
| `range` | 区间分组 | `CASE WHEN` |
| `nested` | 嵌套对象聚合 | 子查询聚合 |

> 聚合请求设置 `"size": 0` 不返回文档只返回聚合结果，节省带宽。

### 7.7 分词测试

```bash
# 看看一段文本被拆成哪些词
curl -X POST "localhost:9200/_analyze" -H 'Content-Type: application/json' -d'
{
  "analyzer": "ik_max_word",
  "text": "Spring Cloud Alibaba 微服务解决方案"
}'
# 结果: ["spring", "cloud", "alibaba", "微服务", "服务", "解决方案", "解决", "方案"]
```

---

## 8. 中文分词（IK）

### 两种模式

| 模式 | 粒度 | 示例："中华人民共和国" |
|------|------|----------------------|
| `ik_max_word` | 最细粒度 | `中华人民共和国` `中华人民` `中华` `华人` `人民共和国` `人民` `共和国` `共和` `国` |
| `ik_smart` | 粗粒度 | `中华人民共和国` |

**推荐**：索引时用 `ik_max_word`（尽可能多切分，提高召回），搜索时用 `ik_smart`（避免太多无关结果，提高精度）。

### 自定义词典

当默认词典缺少特定词汇（如技术术语"号段模式"），创建 `IKAnalyzer.cfg.xml` 配置自定义词典文件 `custom.dic`：

```
号段模式
雪花算法
号段缓冲
```

---

## 9. Spring Data Elasticsearch

### 9.1 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

### 9.2 配置

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
    connection-timeout: 5s
    socket-timeout: 30s
```

### 9.3 Entity

```java
@Document(indexName = "posts")
public class PostDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Integer)
    private Integer viewCount;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
```

### 9.4 Repository

```java
public interface PostRepository extends ElasticsearchRepository<PostDocument, String> {

    // 方法命名自动生成查询
    List<PostDocument> findByTitleContaining(String title);

    List<PostDocument> findByStatusAndTagsIn(String status, List<String> tags);

    // 自定义 @Query
    @Query("{\"bool\":{\"must\":[{\"match\":{\"title\":\"?0\"}}],\"filter\":[{\"term\":{\"status\":\"published\"}}]}}")
    Page<PostDocument> searchByTitle(String keyword, Pageable pageable);
}
```

### 9.5 ElasticsearchOperations（复杂查询）

```java
@Autowired
private ElasticsearchOperations operations;

public List<PostDocument> advancedSearch(String keyword) {
    Criteria criteria = new Criteria("title").matches(keyword)
            .and("status").is("published");

    Query query = new CriteriaQuery(criteria)
            .addSort(Sort.by("view_count").descending())
            .setPageable(PageRequest.of(0, 20));

    SearchHits<PostDocument> hits = operations.search(query, PostDocument.class);
    return hits.stream().map(SearchHit::getContent).toList();
}
```

---

## 10. 设计决策与取舍

### 10.1 为什么不用 MySQL 全文索引

| | MySQL FULLTEXT | Elasticsearch |
|---|---|---|
| **中文分词** | 需要 NGram 插件 | IK 分词器开箱即用 |
| **搜索语法** | MATCH AGAINST，功能单一 | 丰富的 DSL（bool/filter/aggregation/highlight） |
| **评分排序** | 简单相关度 | BM25 算法 + Function Score（加权、衰减、随机） |
| **大数据量** | 单表，无法分布式 | 分片 + 副本，水平扩展 |
| **高并发** | CPU 密集，拖慢主库 | 独立集群，与业务库隔离 |
| **与主库耦合** | 同一个实例，互相影响 | 物理隔离，搜索不走业务库 |

> 结论：MySQL 做 OLTP 事务，ES 做搜索和分析，各司其职。

### 10.2 为什么用 Canal 而不是 MQ 同步

```
方案 A: 应用层双写（❌）
  → noteService → MySQL INSERT + ES INDEX
  → 问题：一个失败导致不一致；分布式事务复杂

方案 B: MQ 异步同步（❌）
  → noteService → MySQL INSERT → 发 MQ → Consumer → ES INDEX
  → 问题：业务代码侵入（每个写入点要发 MQ），漏发难排查

方案 C: Canal 订阅 binlog（✅）
  → noteService → MySQL INSERT  (单写，不用关心 ES)
  → Canal 伪装成 MySQL slave，订阅 binlog
  → 解析 binlog → 发 Kafka → Consumer → ES INDEX
  → 优点：业务零侵入、天然顺序、可回放重建索引
```

### 10.3 Canal + Kafka + ES 的容错

| 环节 | 失败处理 |
|------|---------|
| Canal → Kafka | Kafka 持久化，Canal 断线重连后从断点续传 |
| Kafka → ES | Consumer ACK，失败重试，死信队列兜底 |
| ES 不可用 | Kafka 堆积，ES 恢复后消费追回 |
| 全量重建 | 重新从 Canal 初始位点开始消费 |

### 10.4 Mapping 设计原则

| 原则 | 说明 |
|------|------|
| **索引时用 `ik_max_word`** | 尽可能多分词，提高召回率 |
| **搜索时用 `ik_smart`** | 粗粒度匹配，提高精度 |
| **不需要搜索的字段不索引** | `"index": false` 节省存储 |
| **精确值用 keyword** | 状态、ID、标签——不分词，用于 filter |
| **title 权重 > content** | `multi_match` 用 `^2` 提权 |
| **日期字段用 `date` 类型** | 支持 range 过滤和 date_histogram 聚合 |

---

## 11. 本项目搜索数据流

```
┌─────────────────────────────────────────────────────────────┐
│  数据写入（业务代码只写 MySQL，Canal 负责同步 ES）              │
│                                                             │
│  note-service                                               │
│    → MySQL INSERT note (status, title, content, tags...)     │
│    → MySQL binlog 产生变更事件                                │
│  Canal (伪装 MySQL slave)                                     │
│    → 订阅 binlog，解析增删改事件                               │
│    → 过滤：只关注 note 表的 INSERT / UPDATE / DELETE           │
│    → 发送到 Kafka topic: search.note.sync                    │
│  ES Consumer                                                 │
│    → 消费 Kafka 消息                                          │
│    → 构建 PostDocument                                       │
│    → Bulk 批量写入 ES posts 索引                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  数据查询                                                     │
│                                                             │
│  用户输入 "微服务"                                            │
│    → gateway:8080 GET /api/search?keyword=微服务              │
│      → @DubboReference SearchRpcService                      │
│        → search-service                                      │
│          → ES DSL:                                           │
│            {                                                 │
│              "query": {                                      │
│                "function_score": {                            │
│                  "query": {                                  │
│                    "bool": {                                 │
│                      "must": [{                              │
│                        "multi_match": {                      │
│                          "query": "微服务",                   │
│                          "fields": ["title^3", "content"]    │
│                        }                                     │
│                      }],                                     │
│                      "filter": [                              │
│                        { "term": { "status": "published" } } │
│                      ]                                       │
│                    }                                         │
│                  },                                          │
│                  "functions": [                               │
│                    { "field_value_factor": {                  │
│                        "field": "like_count",                │
│                        "factor": 1.2,                        │
│                        "modifier": "log1p"                   │
│                    }},                                       │
│                    { "gauss": {                               │
│                        "created_at": {                        │
│                          "scale": "7d",                      │
│                          "decay": 0.5                        │
│                    }}}                                       │
│                  ],                                          │
│                  "score_mode": "multiply"                     │
│                }                                             │
│              },                                              │
│              "highlight": {                                   │
│                "fields": {                                    │
│                  "title": {},                                │
│                  "content": { "fragment_size": 100 }         │
│                }                                             │
│              }                                               │
│            }                                                 │
│    ← Result<Page<PostDocument>>                               │
│      (含高亮片段、total、分页信息)                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 12. 常见问题

### Q: near real-time 到底多 near？

默认 `refresh_interval = 1s`，写入后最多 1 秒可搜索。对于搜索场景足够了，如果非要立即可见，可以用 `refresh=wait_for` 参数强制刷新（性能代价大）。

### Q: 分片数怎么选？

- 单节点开发：1 shard
- 小于 10GB：1 shard
- 10-50GB：2-3 shards
- 50-200GB：5+ shards
- **规则**：单 shard 控制在 10-50GB，不要超过 50GB

### Q: Mapping 字段类型建错了怎么办？

**不能改！** Elastisearch 不支持修改已有字段类型。只能 reindex：

```bash
# 1. 创建新索引（正确的 mapping）
curl -X PUT "localhost:9200/posts_v2" -H 'Content-Type: application/json' -d'{...}'

# 2. 迁移数据
curl -X POST "localhost:9200/_reindex" -H 'Content-Type: application/json' -d'
{
  "source": { "index": "posts" },
  "dest": { "index": "posts_v2" }
}'

# 3. 别名切换（业务无感知）
curl -X POST "localhost:9200/_aliases" -H 'Content-Type: application/json' -d'
{
  "actions": [
    { "remove": { "index": "posts", "alias": "posts_alias" } },
    { "add": { "index": "posts_v2", "alias": "posts_alias" } }
  ]
}'

# 4. 删除旧索引
curl -X DELETE "localhost:9200/posts"
```

### Q: 为什么搜索 "Spring" 搜不到 "spring"？

`keyword` 类型区分大小写，用 `text` 类型分词搜索。或者对 `keyword` 字段用 `{"match": {"title.keyword": "Spring"}}`（ES 自动生成 `.keyword` 子字段）。

### Q: ES 需要多少内存？

- 最小：Xms 512m（开发环境）
- 一般：机器内存的 50%，不超过 32GB（JVM 指针压缩上限）
- Lucene 需要 OS Cache，**剩下的内存留给系统做文件缓存**

---

## 13. 推荐学习路径

1. **理解倒排索引**（本文 1-3 节）——知道为什么快
2. **动手 CRUD**（本文 4-6 节）——Docker 安装，用 curl 操作
3. **写搜索查询**（本文第 7 节）——从 `match` 到 `bool` 到 `aggregation`
4. **理解分词**（本文第 8 节）——用 `_analyze` 看分词结果
5. **整合 Spring**（本文第 9 节）——代码实操
6. **深入阅读**：[ES 官方文档](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)、[Elastic 中文社区](https://elasticsearch.cn/)
