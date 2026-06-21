# Search System Design — Canal + Kafka + ES

## Overview

Real-time full-text search for notes and users. Notes use Function Score (BM25 + business weights like views/likes), users use pure BM25. Prefix autocomplete via Completion Suggester. Data sync via Canal → Kafka → ES pipeline.

## Architecture

```
note-svc/auth-svc ──write──→ MySQL (note, user_info tables)
                                  │
                                  │ binlog
                                  ▼
                               Canal ──publish──→ Kafka (search_sync)
                                                       │
                                                       │ consume
                                                       ▼
                                               search-sync-service ──index──→ ES
                                                                           │
                                                                           │ query
                                                                           ▼
                                               gateway ←── search-service
                                                  │
                                                  ▼
                                               front (React)
```

Browse counter path (high-frequency write, avoid hitting MySQL directly):

```
gateway (GET /api/note/detail) ──INCR──→ Redis (note:view:<id>, counter)
                                              │
                                              │ every 5min, scheduled task
                                              ▼
note-service ──UPDATE view_count──→ MySQL ──binlog──→ Canal ──→ Kafka ──→ ES
```

## Module Breakdown

### 1. MySQL — New `note` Table

```sql
CREATE TABLE note (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    title       VARCHAR(256) NOT NULL,
    content     TEXT,
    summary     VARCHAR(512),
    tags        VARCHAR(256) DEFAULT '',
    category    VARCHAR(64) DEFAULT 'general',
    view_count  INT DEFAULT 0,
    like_count  INT DEFAULT 0,
    status      VARCHAR(16) DEFAULT 'PUBLISHED',
    created_at  BIGINT NOT NULL,
    updated_at  BIGINT NOT NULL,
    INDEX idx_note_status (status),
    INDEX idx_note_created_at (created_at),
    FULLTEXT INDEX ft_note_title_content (title, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2. note-service — Changes

- **Dual-write**: On publish/update, write to both Cassandra (comments) and MySQL (note metadata).
- **Browse counter**: Scheduled task reads Redis `note:view:*` keys every 5 minutes, batch `UPDATE note SET view_count = view_count + ? WHERE id = ?`, then delete the Redis keys.
- **Like count**: Already consumed from Kafka by `InteractionEventConsumer`. After persisting to MySQL `interaction_record`, also `UPDATE note SET like_count = ...`.

### 3. Canal — Configuration

- Monitor MySQL binlog for `live_community.note` and `live_community.user_info`
- Publish to Kafka topic `search_sync`
- Use Canal adapter or custom Canal client embedded in search-sync-service

### 4. search-sync-service (New Module)

Spring Boot + Dubbo consumer, port 8085, Dubbo port 20885.

- **Kafka consumer**: Consume `search_sync` topic, group `search-sync-group`
- **Data transformer**: Convert MySQL row change events (insert/update/delete) into ES bulk index actions
- **ES bulk indexer**: `BulkProcessor` with 5MB / 1000 docs / 5s flush thresholds
- **Handles**: `note` table → `notes` index, `user_info` table → `users` index

### 5. search-service (New Module)

Spring Boot + Dubbo consumer, port 8086, Dubbo port 20886.

- **Search API**: `GET /api/search/note?q=&page=&size=&category=&sort=`
- **Suggest API**: `GET /api/search/suggest?q=`
- **Notes query**: `function_score` wrapping `multi_match` on title/content/summary with IK analyzer, script_score: `log(1 + doc['view_count'].value * 0.01 + doc['like_count'].value * 0.5)`
- **Users query**: `multi_match` on username/nickname with IK analyzer, pure BM25
- **All query**: Multi-index search against both `notes` and `users`, results grouped by `_index`

### 6. gateway — Changes

- Add `SearchController` with `@DubboReference SearchRpcService`
- Routes: `/api/search/note`, `/api/search/user`, `/api/search/all`, `/api/search/suggest`
- Browse INCR: In `NoteController.detail()`, after successful response, `redisTemplate.opsForValue().increment("note:view:" + noteId)` (non-blocking)

### 7. common — Changes

- Add `SearchRpcService` interface
- Add DTOs: `NoteSearchRequest`, `NoteSearchResponse`, `UserSearchRequest`, `UserSearchResponse`, `SuggestResponse`

### 8. ES Index Mapping

**notes index**:
```json
{
  "mappings": {
    "properties": {
      "id": { "type": "long" },
      "user_id": { "type": "long" },
      "title": {
        "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart",
        "fields": { "suggest": { "type": "completion" } }
      },
      "content": { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "summary": { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "tags": { "type": "keyword" },
      "category": { "type": "keyword" },
      "view_count": { "type": "integer" },
      "like_count": { "type": "integer" },
      "status": { "type": "keyword" },
      "created_at": { "type": "date" }
    }
  }
}
```

**users index**:
```json
{
  "mappings": {
    "properties": {
      "id": { "type": "long" },
      "username": {
        "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart",
        "fields": { "keyword": { "type": "keyword" }, "suggest": { "type": "completion" } }
      },
      "nickname": {
        "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart",
        "fields": { "suggest": { "type": "completion" } }
      },
      "avatar": { "type": "keyword" },
      "bio": { "type": "text", "analyzer": "ik_max_word" },
      "status": { "type": "keyword" }
    }
  }
}
```

## Scoring Formula (Notes)

```
final_score = _score * log2(1 + view_count * 0.01 + like_count * 0.5)
```

- `_score`: BM25 relevance from `multi_match` across title^3, content^1, summary^2
- `view_count * 0.01`: every ~100 views contributes 1 to the multiplier
- `like_count * 0.5`: every 2 likes contributes 1 to the multiplier
- `log2`: smooths the impact so a post with 10k views doesn't completely dominate

## API Design

| Endpoint | Method | Params | Description |
|----------|--------|--------|-------------|
| `/api/search/note` | GET | q, page, size, category, sort(relevance/views/likes/time) | Note search with function score |
| `/api/search/user` | GET | q, page, size | User search, pure BM25 |
| `/api/search/all` | GET | q, page, size | Multi-index search, grouped results |
| `/api/search/suggest` | GET | q | Completion suggester for both indices |

## Docker Compose Additions

```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
  environment:
    discovery.type: single-node
    xpack.security.enabled: false
    ES_JAVA_OPTS: -Xms512m -Xmx512m
  ports:
    - "9200:9200"
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:9200/_cluster/health"]
  networks: [live-community]

canal-server:
  image: canal/canal-server:v1.1.7
  depends_on:
    mysql: { condition: service_healthy }
    kafka: { condition: service_healthy }
  networks: [live-community]

search-sync-service:
  build: ./search-sync-service
  depends_on:
    canal-server: { condition: service_healthy }
    elasticsearch: { condition: service_healthy }
  networks: [live-community]

search-service:
  build: ./search-service
  depends_on:
    elasticsearch: { condition: service_healthy }
  networks: [live-community]
```

## Error Handling

- **ES unavailable**: search-sync-service retries with exponential backoff, dead-letter to Kafka DLQ
- **Canal disconnected**: auto-reconnect, Kafka offsets ensure no data loss
- **Search timeout**: 2s query timeout, fallback to degraded search (remove function_score, use pure BM25)
- **Empty result**: return empty list, not error

## Test Strategy

- **search-sync-service**: `KafkaConsumerTest` — mock Kafka message, verify ES `BulkRequest` content
- **search-service**: `SearchControllerTest` — mock `ElasticsearchClient`, verify query structure matches expected function_score shape
- **note-service**: `ViewCountSyncTest` — verify Redis INCR + scheduled UPDATE
- **Integration**: Docker Compose full stack, publish note → wait 3s → search → verify result

## Implementation Order

1. MySQL `note` table + note-service dual-write + Redis browse counter
2. Canal + Kafka topic setup
3. search-sync-service (Kafka consumer → ES indexer)
4. ES index templates + IK plugin setup
5. search-service (search / suggest APIs)
6. Gateway SearchController + common DTOs
7. Frontend search integration
8. Docker Compose full integration
