# ES text 字段 term 查询返回 0 结果导致搜索功能无效

## 现象

所有搜索接口返回 `total:0`，ES 中已有 33 篇文档且状态均为 `PUBLISHED`：

```
GET /api/search/all?q=日本  →  {"notes": {"total": 0, "results": []}}
GET /api/search/note?q=git  →  {"total": 0, "results": []}
```

## 根因

### 第一层：ES 索引 mapping 未按预期创建

`init-es.sh` 使用 IK 分词器创建索引，但 IK 插件未安装到 ES 容器中，导致 `PUT /notes` 失败（HTTP 400）。索引实际由 search-sync-service 首次写入文档时自动创建，所有字符串字段被映射为 `text` 类型（附带 `.keyword` 子字段）：

```json
// 实际 mapping（自动创建）
"status": { "type": "text", "fields": { "keyword": { "type": "keyword" } } }
"category": { "type": "text", "fields": { "keyword": { "type": "keyword" } } }

// 期望 mapping（init-es.sh 定义）
"status": { "type": "keyword" }
"category": { "type": "keyword" }
```

### 第二层：term 查询在 text 字段上不工作

`NoteSearchService` 使用 `term` 查询过滤 `status` 和 `category`：

```java
b.filter(f -> f.term(t -> t.field("status").value("PUBLISHED")));
b.filter(f -> f.term(t -> t.field("category").value(request.getCategory())));
```

`term` 查询对 `text` 类型字段进行精确匹配时会失败，因为 text 字段在索引时被标准分析器 tokenize 和 lowercased。`PUBLISHED` 被分析为 `published`，而 `term` 查询不会对输入值做分析，直接匹配 `PUBLISHED`（大写），永远无法匹配。

### 第三层：异常被静默吞掉

`NoteSearchService.search()` 方法（第 104-106 行）捕获所有异常后返回空结果，导致 Elasticsearch 客户端的错误信息（如 `index_not_found_exception`、`MissingRequiredPropertyException`）被隐藏，增加了排查难度。

## 修复

### 1. term 查询改用 `.keyword` 子字段

```java
// 修复前
b.filter(f -> f.term(t -> t.field("status").value("PUBLISHED")));
b.filter(f -> f.term(t -> t.field("category").value(request.getCategory())));

// 修复后
b.filter(f -> f.term(t -> t.field("status.keyword").value("PUBLISHED")));
b.filter(f -> f.term(t -> t.field("category.keyword").value(request.getCategory())));
```

### 2. SortOptions 默认值处理

当 `sort` 参数为 null 时，`SortOptions` builder 缺少必需的 variant kind 导致 `MissingRequiredPropertyException`。添加 `_score` 降序作为默认排序：

```java
.sort(sort -> {
    String sortBy = request.getSort();
    if ("views".equals(sortBy)) {
        sort.field(f -> f.field("view_count").order(SortOrder.Desc));
    } else if ("likes".equals(sortBy)) {
        sort.field(f -> f.field("like_count").order(SortOrder.Desc));
    } else if ("time".equals(sortBy)) {
        sort.field(f -> f.field("created_at").order(SortOrder.Desc));
    } else {
        sort.field(f -> f.field("_score").order(SortOrder.Desc));
    }
    return sort;
})
```

### 3. 创建 users 索引

`init-es.sh` 因 IK 插件缺失而失败，users 索引未被创建。手动创建（使用标准分析器）：

```
PUT /users
{
  "mappings": {
    "properties": {
      "id": { "type": "long" },
      "username": { "type": "text", "fields": { "keyword": { "type": "keyword" }, "suggest": { "type": "completion" } } },
      "nickname": { "type": "text", "fields": { "suggest": { "type": "completion" } } },
      "avatar": { "type": "keyword" },
      "status": { "type": "keyword" }
    }
  }
}
```

## 关键文件变更

| 文件 | 变更 |
|------|------|
| `search-service/.../NoteSearchService.java` | `status`/`category` term 查询改用 `.keyword` 字段；SortOptions 增加 `_score` 默认值 |
| `search-service/.../UserSearchService.java` | users 搜索无 term filter，无需修改 |
| ES `users` 索引 | 手动创建（IK 插件不可用，用标准分析器） |
