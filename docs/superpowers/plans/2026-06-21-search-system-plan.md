# Search System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build real-time search for notes (Function Score = BM25 × view/like weights) and users (pure BM25) with prefix autocomplete, synced via Canal → Kafka → ES pipeline.

**Architecture:** MySQL `note` table is the search source-of-truth. Canal monitors MySQL binlog, publishes to Kafka topic `search_sync`. New `search-sync-service` consumes Kafka, indexes into ES (`notes` + `users` indices). New `search-service` provides search/suggest APIs. Browse counter uses Redis INCR + 5-min scheduled MySQL UPDATE to avoid high-frequency writes.

**Tech Stack:** Spring Boot 3.2.6, Dubbo 3.3.0, Elasticsearch 8.12 (Java client), IK Analyzer, Kafka, Canal 1.1.7, Redis, MySQL 8.0

---

## File Structure

### New Modules

```
search-sync-service/
├── pom.xml
├── Dockerfile
└── src/main/
    ├── java/com/example/searchsync/
    │   ├── SearchSyncApplication.java         @SpringBootApplication + @EnableDubbo
    │   ├── config/
    │   │   ├── ElasticsearchConfig.java        ElasticsearchClient bean
    │   │   └── KafkaConsumerConfig.java        ConsumerFactory + ConcurrentListenerContainer
    │   ├── consumer/
    │   │   └── SearchSyncConsumer.java         @KafkaListener for search_sync topic
    │   ├── service/
    │   │   └── EsIndexService.java             BulkProcessor, index/delete doc builders
    │   └── model/
    │       ├── CanalMessage.java               Canal flat JSON message wrapper
    │       └── EsDocMapping.java               ES doc field constants → DTO builders
    └── resources/
        └── application.yml

search-service/
├── pom.xml
├── Dockerfile
└── src/main/
    ├── java/com/example/searchservice/
    │   ├── SearchServiceApplication.java       @SpringBootApplication + @EnableDubbo
    │   ├── config/
    │   │   └── ElasticsearchConfig.java        ElasticsearchClient bean
    │   ├── service/
    │   │   ├── NoteSearchService.java          Function score query builder
    │   │   ├── UserSearchService.java          Multi-match BM25 query builder
    │   │   └── SuggestService.java             Completion suggester
    │   ├── rpc/
    │   │   └── SearchRpcServiceImpl.java       @DubboService, implements SearchRpcService
    │   └── controller/
    │       └── SearchController.java           Direct REST for health/debug
    └── resources/
        └── application.yml
```

### Modified Files

| File | Change |
|------|--------|
| `common/src/main/java/com/example/common/SearchRpcService.java` | CREATE: Dubbo RPC interface |
| `common/src/main/java/com/example/common/NoteSearchRequest.java` | CREATE: DTO |
| `common/src/main/java/com/example/common/NoteSearchResponse.java` | CREATE: DTO |
| `common/src/main/java/com/example/common/UserSearchResponse.java` | CREATE: DTO |
| `common/src/main/java/com/example/common/SuggestResponse.java` | CREATE: DTO |
| `auth-service/src/main/resources/sql/init_auth.sql` | UPDATE: add `note` table DDL |
| `note-service/src/main/java/com/example/note/repository/NoteMysqlRepository.java` | CREATE: JDBC note repo |
| `note-service/src/main/java/com/example/note/service/NoteService.java` | UPDATE: dual-write MySQL on publish/update |
| `note-service/src/main/java/com/example/note/service/ViewCountSyncScheduler.java` | CREATE: scheduled Redis→MySQL task |
| `note-service/src/main/java/com/example/note/service/InteractionEventConsumer.java` | UPDATE: sync like_count to MySQL note table |
| `note-service/src/main/resources/application.yml` | UPDATE: add MySQL DataSource config |
| `gateway/src/main/java/com/example/gateway/controller/SearchController.java` | CREATE: search REST endpoints |
| `gateway/src/main/java/com/example/gateway/controller/NoteController.java` | UPDATE: INCR view on detail |
| `gateway/src/main/resources/application.yml` | UPDATE: add Dubbo reference for search |
| `docker-compose.yml` | UPDATE: add ES, Canal, search-sync-service, search-service |
| `front/src/pages/SearchPage.tsx` | CREATE: search UI |
| `front/src/api/index.ts` | UPDATE: add search API functions |
| `front/src/App.tsx` | UPDATE: add search route |

---

### Task 1: MySQL `note` table DDL

**Files:**
- Modify: `auth-service/src/main/resources/sql/init_auth.sql`

- [ ] **Step 1: Add note table to init SQL**

Append to `auth-service/src/main/resources/sql/init_auth.sql`:

```sql
CREATE TABLE IF NOT EXISTS note (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    title       VARCHAR(256) NOT NULL,
    content     TEXT,
    summary     VARCHAR(512),
    tags        VARCHAR(512) DEFAULT '',
    category    VARCHAR(64) DEFAULT 'general',
    view_count  INT DEFAULT 0,
    like_count  INT DEFAULT 0,
    status      VARCHAR(16) DEFAULT 'PUBLISHED',
    created_at  BIGINT NOT NULL,
    updated_at  BIGINT NOT NULL,
    INDEX idx_note_user_id (user_id),
    INDEX idx_note_status (status),
    INDEX idx_note_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: Commit**

```bash
git add auth-service/src/main/resources/sql/init_auth.sql
git commit -m "feat: add note MySQL table for search indexing"
```

---

### Task 2: Common DTOs + Dubbo RPC interface

**Files:**
- Create: `common/src/main/java/com/example/common/SearchRpcService.java`
- Create: `common/src/main/java/com/example/common/NoteSearchRequest.java`
- Create: `common/src/main/java/com/example/common/NoteSearchResponse.java`
- Create: `common/src/main/java/com/example/common/UserSearchResponse.java`
- Create: `common/src/main/java/com/example/common/SuggestResponse.java`

- [ ] **Step 1: Create NoteSearchRequest DTO**

```java
package com.example.common;

import java.io.Serializable;

public class NoteSearchRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String q;
    private int page = 1;
    private int size = 20;
    private String category;
    private String sort = "relevance"; // relevance, views, likes, time

    public NoteSearchRequest() {}

    public NoteSearchRequest(String q, int page, int size, String category, String sort) {
        this.q = q; this.page = page; this.size = size; this.category = category; this.sort = sort;
    }

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
}
```

- [ ] **Step 2: Create NoteSearchResponse DTO**

```java
package com.example.common;

import java.io.Serializable;
import java.util.List;

public class NoteSearchResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private long total;
    private int page;
    private int size;
    private List<NoteSearchResult> results;

    public NoteSearchResponse() {}

    public NoteSearchResponse(long total, int page, int size, List<NoteSearchResult> results) {
        this.total = total; this.page = page; this.size = size; this.results = results;
    }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public List<NoteSearchResult> getResults() { return results; }
    public void setResults(List<NoteSearchResult> results) { this.results = results; }

    public static class NoteSearchResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private Long userId;
        private String title;
        private String summary;
        private String tags;
        private String category;
        private int viewCount;
        private int likeCount;
        private Long createdAt;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public int getViewCount() { return viewCount; }
        public void setViewCount(int viewCount) { this.viewCount = viewCount; }
        public int getLikeCount() { return likeCount; }
        public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
        public Long getCreatedAt() { return createdAt; }
        public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    }
}
```

- [ ] **Step 3: Create UserSearchResponse DTO**

```java
package com.example.common;

import java.io.Serializable;
import java.util.List;

public class UserSearchResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private long total;
    private int page;
    private int size;
    private List<UserSearchResult> results;

    public UserSearchResponse() {}

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public List<UserSearchResult> getResults() { return results; }
    public void setResults(List<UserSearchResult> results) { this.results = results; }

    public static class UserSearchResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String username;
        private String nickname;
        private String avatar;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
    }
}
```

- [ ] **Step 4: Create SuggestResponse DTO**

```java
package com.example.common;

import java.io.Serializable;
import java.util.List;

public class SuggestResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<Suggestion> suggestions;

    public SuggestResponse() {}

    public SuggestResponse(List<Suggestion> suggestions) { this.suggestions = suggestions; }

    public List<Suggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(List<Suggestion> suggestions) { this.suggestions = suggestions; }

    public static class Suggestion implements Serializable {
        private static final long serialVersionUID = 1L;
        private String text;
        private String type; // "note" or "user"
        private Long id;
        public Suggestion() {}
        public Suggestion(String text, String type, Long id) { this.text = text; this.type = type; this.id = id; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }
}
```

- [ ] **Step 5: Create SearchRpcService interface**

```java
package com.example.common;

public interface SearchRpcService {
    NoteSearchResponse searchNotes(NoteSearchRequest request);
    UserSearchResponse searchUsers(String q, int page, int size);
    SuggestResponse suggest(String q);
}
```

- [ ] **Step 6: Compile common module**

```bash
mvn compile -pl common
```

- [ ] **Step 7: Commit**

```bash
git add common/src/main/java/com/example/common/SearchRpcService.java common/src/main/java/com/example/common/NoteSearchRequest.java common/src/main/java/com/example/common/NoteSearchResponse.java common/src/main/java/com/example/common/UserSearchResponse.java common/src/main/java/com/example/common/SuggestResponse.java
git commit -m "feat: add search RPC interface and DTOs to common module"
```

---

### Task 3: note-service — MySQL dual-write on publish/update

**Files:**
- Create: `note-service/src/main/java/com/example/note/repository/NoteMysqlRepository.java`
- Modify: `note-service/src/main/java/com/example/note/service/NoteService.java`
- Modify: `note-service/src/main/resources/application.yml`

- [ ] **Step 1: Add MySQL DataSource config to note-service application.yml**

Append to `note-service/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/live_community?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
```

First read the existing `application.yml` to know its current content.

- [ ] **Step 2: Create NoteMysqlRepository**

```java
package com.example.note.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NoteMysqlRepository {

    private final JdbcTemplate jdbc;

    public NoteMysqlRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String UPSERT_SQL =
        "INSERT INTO note (id, user_id, title, content, summary, tags, category, view_count, like_count, status, created_at, updated_at) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?) "
        + "ON DUPLICATE KEY UPDATE title = VALUES(title), content = VALUES(content), "
        + "summary = VALUES(summary), tags = VALUES(tags), category = VALUES(category), "
        + "status = VALUES(status), updated_at = VALUES(updated_at)";

    public void upsert(long id, long userId, String title, String content, String summary,
                        String tags, String category, String status, long createdAt, long updatedAt) {
        jdbc.update(UPSERT_SQL, id, userId, title, content, summary,
            tags != null ? tags : "", category != null ? category : "general",
            status, createdAt, updatedAt);
    }

    private static final String UPDATE_VIEW_COUNT_SQL =
        "UPDATE note SET view_count = view_count + ? WHERE id = ?";

    public void addViewCount(long noteId, int delta) {
        jdbc.update(UPDATE_VIEW_COUNT_SQL, delta, noteId);
    }

    private static final String UPDATE_LIKE_COUNT_SQL =
        "UPDATE note SET like_count = like_count + ? WHERE id = ?";

    public void addLikeCount(long noteId, int delta) {
        jdbc.update(UPDATE_LIKE_COUNT_SQL, delta, noteId);
    }

    private static final String DELETE_SQL = "DELETE FROM note WHERE id = ?";

    public void delete(long noteId) {
        jdbc.update(DELETE_SQL, noteId);
    }
}
```

- [ ] **Step 3: Modify NoteService — dual-write to MySQL on publish**

Read `note-service/src/main/java/com/example/note/service/NoteService.java` first. Then:

Add field injection for `NoteMysqlRepository`:

```java
private final NoteMysqlRepository noteMysqlRepository;

// add to constructor parameters
```

In `publishNote()` method, after Cassandra save, add:

```java
noteMysqlRepository.upsert(
    note.getId(), note.getUserId(), note.getTitle(), note.getContent(),
    note.getSummary(), null, null,
    note.getStatus(), note.getCreatedAt(), note.getUpdatedAt()
);
```

In `createDraft()` method, after Cassandra save, add:

```java
noteMysqlRepository.upsert(
    note.getId(), note.getUserId(), note.getTitle(), note.getContent(),
    note.getSummary(), null, null,
    note.getStatus(), note.getCreatedAt(), note.getUpdatedAt()
);
```

In `updateNote()` method (if exists), after Cassandra save, add same upsert.

- [ ] **Step 4: Commit**

```bash
git add note-service/
git commit -m "feat: add MySQL dual-write for note metadata in note-service"
```

---

### Task 4: note-service — View count sync scheduler

**Files:**
- Create: `note-service/src/main/java/com/example/note/service/ViewCountSyncScheduler.java`

- [ ] **Step 1: Create ViewCountSyncScheduler**

```java
package com.example.note.service;

import com.example.note.repository.NoteMysqlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@EnableScheduling
public class ViewCountSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ViewCountSyncScheduler.class);
    private static final String VIEW_KEY_PREFIX = "note:view:";

    private final StringRedisTemplate redisTemplate;
    private final NoteMysqlRepository noteMysqlRepository;

    public ViewCountSyncScheduler(StringRedisTemplate redisTemplate, NoteMysqlRepository noteMysqlRepository) {
        this.redisTemplate = redisTemplate;
        this.noteMysqlRepository = noteMysqlRepository;
    }

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void syncViewCounts() {
        Set<String> keys = redisTemplate.keys(VIEW_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        log.info("Syncing view counts for {} notes", keys.size());
        for (String key : keys) {
            try {
                long noteId = Long.parseLong(key.substring(VIEW_KEY_PREFIX.length()));
                String countStr = redisTemplate.opsForValue().getAndDelete(key);
                if (countStr != null) {
                    int delta = Integer.parseInt(countStr);
                    noteMysqlRepository.addViewCount(noteId, delta);
                }
            } catch (Exception e) {
                log.error("Failed to sync view count for key: {}", key, e);
            }
        }
    }
}
```

- [ ] **Step 2: Verify note-service compiles**

```bash
mvn compile -pl note-service
```

- [ ] **Step 3: Commit**

```bash
git add note-service/src/main/java/com/example/note/service/ViewCountSyncScheduler.java
git commit -m "feat: add view count sync scheduler (Redis → MySQL, 5min interval)"
```

---

### Task 5: note-service — like_count sync in InteractionEventConsumer

**Files:**
- Modify: `note-service/src/main/java/com/example/note/service/InteractionEventConsumer.java`

- [ ] **Step 1: Read current InteractionEventConsumer**

Read the file first to understand the existing structure.

- [ ] **Step 2: Add like_count sync to MySQL note table**

After persisting the like/unlike event, add a call to `noteMysqlRepository.addLikeCount(noteId, isLike ? 1 : -1)`.

Inject `NoteMysqlRepository` into the consumer, and in the like processing logic:

```java
// after saving interaction record
noteMysqlRepository.addLikeCount(noteId, 1);  // or -1 for unlike
```

- [ ] **Step 3: Commit**

```bash
git add note-service/src/main/java/com/example/note/service/InteractionEventConsumer.java
git commit -m "feat: sync like_count to MySQL note table on like/unlike events"
```

---

### Task 6: gateway — INCR view count on note detail

**Files:**
- Modify: `gateway/src/main/java/com/example/gateway/controller/NoteController.java`

- [ ] **Step 1: Read current NoteController**

Read the file first.

- [ ] **Step 2: Add Redis INCR after detail response**

Inject `StringRedisTemplate` (add Redis dependency to gateway pom.xml if not already present). In the `detail()` method, after successfully building the response:

```java
redisTemplate.opsForValue().increment("note:view:" + noteId);
```

This is fire-and-forget (non-blocking). Add `StringRedisTemplate` field injected via constructor.

- [ ] **Step 3: Commit**

```bash
git add gateway/src/main/java/com/example/gateway/controller/NoteController.java gateway/pom.xml
git commit -m "feat: increment view counter in Redis on note detail request"
```

---

### Task 7: search-sync-service — scaffold + ES config

**Files:**
- Create: `search-sync-service/pom.xml`
- Create: `search-sync-service/src/main/java/com/example/searchsync/SearchSyncApplication.java`
- Create: `search-sync-service/src/main/java/com/example/searchsync/config/ElasticsearchConfig.java`
- Create: `search-sync-service/src/main/resources/application.yml`
- Create: `search-sync-service/Dockerfile`

- [ ] **Step 1: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example</groupId>
        <artifactId>spring-cloud-test</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>search-sync-service</artifactId>
    <packaging>jar</packaging>
    <name>search-sync-service</name>

    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>co.elastic.clients</groupId>
            <artifactId>elasticsearch-java</artifactId>
            <version>8.12.0</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.dubbo</groupId>
            <artifactId>dubbo-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create application.yml**

```yaml
server:
  port: 8085

spring:
  application:
    name: search-sync-service
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_HOST:localhost}:8848
        enabled: true
  kafka:
    bootstrap-servers: ${KAFKA_HOST:localhost}:9092
    consumer:
      group-id: search-sync-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      max-poll-records: 500
      properties:
        spring.json.trusted.packages: "*"

dubbo:
  application:
    name: search-sync-service
    qos-enable: false
  registry:
    address: nacos://${NACOS_HOST:localhost}:8848
    register: false
  protocol:
    name: tri
    port: 20885

elasticsearch:
  host: ${ES_HOST:localhost}
  port: ${ES_PORT:9200}
```

- [ ] **Step 3: Create SearchSyncApplication.java**

```java
package com.example.searchsync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SearchSyncApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchSyncApplication.class, args);
    }
}
```

- [ ] **Step 4: Create ElasticsearchConfig.java**

```java
package com.example.searchsync.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.host:localhost}")
    private String host;

    @Value("${elasticsearch.port:9200}")
    private int port;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(org.apache.http.auth.AuthScope.ANY,
            new UsernamePasswordCredentials("", ""));

        RestClient restClient = RestClient.builder(new HttpHost(host, port, "http"))
            .setHttpClientConfigCallback(hc -> hc.setDefaultCredentialsProvider(creds))
            .build();

        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
```

- [ ] **Step 5: Create Dockerfile**

```dockerfile
FROM openjdk:17-slim
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

- [ ] **Step 6: Commit**

```bash
git add search-sync-service/
git commit -m "feat: scaffold search-sync-service module"
```

---

### Task 8: search-sync-service — Kafka consumer + ES indexer

**Files:**
- Create: `search-sync-service/src/main/java/com/example/searchsync/model/CanalMessage.java`
- Create: `search-sync-service/src/main/java/com/example/searchsync/service/EsDocMapper.java`
- Create: `search-sync-service/src/main/java/com/example/searchsync/service/EsIndexService.java`
- Create: `search-sync-service/src/main/java/com/example/searchsync/consumer/SearchSyncConsumer.java`
- Create: `search-sync-service/src/main/java/com/example/searchsync/config/KafkaConsumerConfig.java`

- [ ] **Step 1: Create CanalMessage model**

```java
package com.example.searchsync.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class CanalMessage {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String table;
    private String type; // INSERT, UPDATE, DELETE
    private List<String> pkNames;
    private List<JsonNode> data;
    private List<JsonNode> old;

    // Parse flat protobuf/flat message JSON from canal adapter
    public static CanalMessage fromJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            CanalMessage msg = new CanalMessage();
            msg.table = root.has("table") ? root.get("table").asText() : "";
            msg.type = root.has("type") ? root.get("type").asText() : "";
            msg.pkNames = new ArrayList<>();
            if (root.has("pkNames")) {
                for (JsonNode n : root.get("pkNames")) {
                    msg.pkNames.add(n.asText());
                }
            }
            msg.data = new ArrayList<>();
            if (root.has("data")) {
                for (JsonNode n : root.get("data")) {
                    msg.data.add(n);
                }
            }
            msg.old = new ArrayList<>();
            if (root.has("old")) {
                for (JsonNode n : root.get("old")) {
                    msg.old.add(n);
                }
            }
            return msg;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CanalMessage", e);
        }
    }

    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<String> getPkNames() { return pkNames; }
    public void setPkNames(List<String> pkNames) { this.pkNames = pkNames; }
    public List<JsonNode> getData() { return data; }
    public void setData(List<JsonNode> data) { this.data = data; }
    public List<JsonNode> getOld() { return old; }
    public void setOld(List<JsonNode> old) { this.old = old; }
}
```

- [ ] **Step 2: Create EsDocMapper**

```java
package com.example.searchsync.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

public class EsDocMapper {

    public static Map<String, Object> noteFromMySql(JsonNode row) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", asLong(row, "id"));
        doc.put("user_id", asLong(row, "user_id"));
        doc.put("title", asString(row, "title"));
        doc.put("content", asString(row, "content"));
        doc.put("summary", asString(row, "summary"));
        doc.put("tags", asString(row, "tags"));
        doc.put("category", asString(row, "category"));
        doc.put("view_count", asInt(row, "view_count"));
        doc.put("like_count", asInt(row, "like_count"));
        doc.put("status", asString(row, "status"));
        doc.put("created_at", asLong(row, "created_at"));
        return doc;
    }

    public static Map<String, Object> userFromMySql(JsonNode row) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", asLong(row, "user_id"));
        doc.put("username", asString(row, "username"));
        doc.put("nickname", asString(row, "nickname"));
        doc.put("avatar", asString(row, "avatar"));
        doc.put("status", asString(row, "status"));
        return doc;
    }

    private static String asString(JsonNode row, String field) {
        JsonNode node = row.get(field);
        return node != null && !node.isNull() ? node.asText() : "";
    }

    private static long asLong(JsonNode row, String field) {
        JsonNode node = row.get(field);
        return node != null && !node.isNull() ? node.asLong() : 0L;
    }

    private static int asInt(JsonNode row, String field) {
        JsonNode node = row.get(field);
        return node != null && !node.isNull() ? node.asInt() : 0;
    }
}
```

- [ ] **Step 3: Create EsIndexService**

```java
package com.example.searchsync.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EsIndexService {

    private static final Logger log = LoggerFactory.getLogger(EsIndexService.class);
    private static final String NOTES_INDEX = "notes";
    private static final String USERS_INDEX = "users";

    private final ElasticsearchClient esClient;

    public EsIndexService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public void indexNote(List<JsonNode> rows) {
        List<BulkOperation> ops = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, Object> doc = EsDocMapper.noteFromMySql(row);
            long id = (long) doc.get("id");
            ops.add(BulkOperation.of(b -> b
                .index(IndexOperation.of(i -> i.index(NOTES_INDEX).id(String.valueOf(id)).document(doc)))));
        }
        executeBulk(ops, NOTES_INDEX);
    }

    public void deleteNote(List<JsonNode> rows) {
        List<BulkOperation> ops = new ArrayList<>();
        for (JsonNode row : rows) {
            String id = String.valueOf(EsDocMapper.noteFromMySql(row).get("id"));
            ops.add(BulkOperation.of(b -> b
                .delete(DeleteOperation.of(d -> d.index(NOTES_INDEX).id(id)))));
        }
        executeBulk(ops, NOTES_INDEX);
    }

    public void indexUser(List<JsonNode> rows) {
        List<BulkOperation> ops = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, Object> doc = EsDocMapper.userFromMySql(row);
            long id = (long) doc.get("id");
            ops.add(BulkOperation.of(b -> b
                .index(IndexOperation.of(i -> i.index(USERS_INDEX).id(String.valueOf(id)).document(doc)))));
        }
        executeBulk(ops, USERS_INDEX);
    }

    public void deleteUser(List<JsonNode> rows) {
        List<BulkOperation> ops = new ArrayList<>();
        for (JsonNode row : rows) {
            String id = String.valueOf(EsDocMapper.userFromMySql(row).get("id"));
            ops.add(BulkOperation.of(b -> b
                .delete(DeleteOperation.of(d -> d.index(USERS_INDEX).id(id)))));
        }
        executeBulk(ops, USERS_INDEX);
    }

    private void executeBulk(List<BulkOperation> ops, String index) {
        if (ops.isEmpty()) return;
        try {
            BulkResponse response = esClient.bulk(BulkRequest.of(b -> b.operations(ops)));
            if (response.errors()) {
                response.items().stream()
                    .filter(item -> item.error() != null)
                    .forEach(item -> log.error("Bulk error on [{}]: {}", index, item.error().reason()));
            }
        } catch (Exception e) {
            log.error("Bulk index failed for [{}]", index, e);
        }
    }
}
```

- [ ] **Step 4: Create SearchSyncConsumer**

```java
package com.example.searchsync.consumer;

import com.example.searchsync.model.CanalMessage;
import com.example.searchsync.service.EsIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SearchSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(SearchSyncConsumer.class);

    private final EsIndexService esIndexService;

    public SearchSyncConsumer(EsIndexService esIndexService) {
        this.esIndexService = esIndexService;
    }

    @KafkaListener(topics = "search_sync", groupId = "search-sync-group")
    public void onMessage(String message) {
        try {
            CanalMessage msg = CanalMessage.fromJson(message);
            log.debug("Received: table={}, type={}, rows={}", msg.getTable(), msg.getType(),
                msg.getData() != null ? msg.getData().size() : 0);

            switch (msg.getTable()) {
                case "note":
                    if ("DELETE".equals(msg.getType())) {
                        esIndexService.deleteNote(msg.getData());
                    } else {
                        esIndexService.indexNote(msg.getData());
                    }
                    break;
                case "user_info":
                    if ("DELETE".equals(msg.getType())) {
                        esIndexService.deleteUser(msg.getData());
                    } else {
                        esIndexService.indexUser(msg.getData());
                    }
                    break;
                default:
                    log.warn("Unknown table: {}", msg.getTable());
            }
        } catch (Exception e) {
            log.error("Failed to process sync message", e);
        }
    }
}
```

- [ ] **Step 5: Create KafkaConsumerConfig**

```java
package com.example.searchsync.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "search-sync-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
```

- [ ] **Step 6: Compile search-sync-service**

```bash
mvn compile -pl search-sync-service
```

- [ ] **Step 7: Commit**

```bash
git add search-sync-service/
git commit -m "feat: add Kafka consumer + ES indexer for note and user sync"
```

---

### Task 9: search-service — scaffold + ES config

**Files:**
- Create: `search-service/pom.xml`
- Create: `search-service/src/main/java/com/example/searchservice/SearchServiceApplication.java`
- Create: `search-service/src/main/java/com/example/searchservice/config/ElasticsearchConfig.java`
- Create: `search-service/src/main/resources/application.yml`
- Create: `search-service/Dockerfile`

- [ ] **Step 1: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example</groupId>
        <artifactId>spring-cloud-test</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>search-service</artifactId>
    <packaging>jar</packaging>
    <name>search-service</name>

    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>co.elastic.clients</groupId>
            <artifactId>elasticsearch-java</artifactId>
            <version>8.12.0</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.dubbo</groupId>
            <artifactId>dubbo-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create application.yml**

```yaml
server:
  port: 8086

spring:
  application:
    name: search-service
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_HOST:localhost}:8848
        enabled: true

dubbo:
  application:
    name: search-service
    qos-enable: false
  registry:
    address: nacos://${NACOS_HOST:localhost}:8848
  protocol:
    name: tri
    port: 20886

elasticsearch:
  host: ${ES_HOST:localhost}
  port: ${ES_PORT:9200}
```

- [ ] **Step 3: Create SearchServiceApplication.java**

```java
package com.example.searchservice;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class SearchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
```

- [ ] **Step 4: Create ElasticsearchConfig.java**

```java
package com.example.searchservice.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.host:localhost}")
    private String host;

    @Value("${elasticsearch.port:9200}")
    private int port;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClient restClient = RestClient.builder(new HttpHost(host, port, "http")).build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
```

- [ ] **Step 5: Create Dockerfile**

```dockerfile
FROM openjdk:17-slim
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

- [ ] **Step 6: Commit**

```bash
git add search-service/
git commit -m "feat: scaffold search-service module"
```

---

### Task 10: search-service — NoteSearchService with Function Score

**Files:**
- Create: `search-service/src/main/java/com/example/searchservice/service/NoteSearchService.java`

- [ ] **Step 1: Create NoteSearchService**

```java
package com.example.searchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.example.common.NoteSearchRequest;
import com.example.common.NoteSearchResponse;
import com.example.common.NoteSearchResponse.NoteSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class NoteSearchService {

    private static final Logger log = LoggerFactory.getLogger(NoteSearchService.class);
    private static final String NOTES_INDEX = "notes";

    private final ElasticsearchClient esClient;

    public NoteSearchService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public NoteSearchResponse search(NoteSearchRequest request) {
        int from = (request.getPage() - 1) * request.getSize();
        int size = request.getSize();

        try {
            SearchResponse<Map> response = esClient.search(s -> s
                .index(NOTES_INDEX)
                .query(q -> q
                    .functionScore(fs -> fs
                        .query(inner -> inner
                            .bool(b -> {
                                b.must(m -> m.multiMatch(mm -> mm
                                    .fields("title^3", "content", "summary^2")
                                    .query(request.getQ())
                                ));
                                if (request.getCategory() != null && !request.getCategory().isEmpty()) {
                                    b.filter(f -> f.term(t -> t.field("category").value(request.getCategory())));
                                }
                                b.filter(f -> f.term(t -> t.field("status").value("PUBLISHED")));
                                return b;
                            })
                        )
                        .functions(fn -> fn
                            .scriptScore(ss -> ss
                                .script(sc -> sc
                                    .inline(in -> in
                                        .source("_score * Math.log(1 + doc['view_count'].value * 0.01 + doc['like_count'].value * 0.5 + 2)")
                                    )
                                )
                            )
                        )
                        .scoreMode(FunctionScoreMode.Multiply)
                        .boostMode(co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode.Replace)
                    )
                )
                .from(from)
                .size(size)
                .sort(sort -> {
                    if ("views".equals(request.getSort())) {
                        sort.field(f -> f.field("view_count").order(SortOrder.Desc));
                    } else if ("likes".equals(request.getSort())) {
                        sort.field(f -> f.field("like_count").order(SortOrder.Desc));
                    } else if ("time".equals(request.getSort())) {
                        sort.field(f -> f.field("created_at").order(SortOrder.Desc));
                    }
                    // "relevance" uses _score, which is default
                    return sort;
                })
                .trackTotalHits(th -> th.enabled(true)),
                Map.class
            );

            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            List<NoteSearchResult> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) hit.source();
                if (source != null) {
                    NoteSearchResult r = new NoteSearchResult();
                    r.setId(toLong(source.get("id")));
                    r.setUserId(toLong(source.get("user_id")));
                    r.setTitle((String) source.get("title"));
                    r.setSummary((String) source.get("summary"));
                    r.setTags((String) source.get("tags"));
                    r.setCategory((String) source.get("category"));
                    r.setViewCount(toInt(source.get("view_count")));
                    r.setLikeCount(toInt(source.get("like_count")));
                    r.setCreatedAt(toLong(source.get("created_at")));
                    results.add(r);
                }
            }

            return new NoteSearchResponse(total, request.getPage(), request.getSize(), results);
        } catch (Exception e) {
            log.error("Note search failed for q={}", request.getQ(), e);
            return new NoteSearchResponse(0, request.getPage(), request.getSize(), List.of());
        }
    }

    private long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) return Long.parseLong(s);
        return 0L;
    }

    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) return Integer.parseInt(s);
        return 0;
    }
}
```

**Function Score formula** (in Painless script):
```
_score * log(1 + view_count * 0.01 + like_count * 0.5 + 2)
```

The `+ 2` ensures a baseline multiplier of ~1.58 for documents with 0 views and 0 likes, preventing them from being scored to zero.

**Field boosts**: title^3, content^1, summary^2.

- [ ] **Step 2: Commit**

```bash
git add search-service/src/main/java/com/example/searchservice/service/NoteSearchService.java
git commit -m "feat: add NoteSearchService with function_score BM25 + view/like weights"
```

---

### Task 11: search-service — UserSearchService + SuggestService + RPC impl

**Files:**
- Create: `search-service/src/main/java/com/example/searchservice/service/UserSearchService.java`
- Create: `search-service/src/main/java/com/example/searchservice/service/SuggestService.java`
- Create: `search-service/src/main/java/com/example/searchservice/rpc/SearchRpcServiceImpl.java`

- [ ] **Step 1: Create UserSearchService**

```java
package com.example.searchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.common.UserSearchResponse;
import com.example.common.UserSearchResponse.UserSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class UserSearchService {

    private static final Logger log = LoggerFactory.getLogger(UserSearchService.class);
    private static final String USERS_INDEX = "users";

    private final ElasticsearchClient esClient;

    public UserSearchService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public UserSearchResponse search(String q, int page, int size) {
        int from = (page - 1) * size;

        try {
            SearchResponse<Map> response = esClient.search(s -> s
                .index(USERS_INDEX)
                .query(qq -> qq
                    .multiMatch(mm -> mm
                        .fields("username", "nickname^2")
                        .query(q)
                    )
                )
                .from(from)
                .size(size)
                .trackTotalHits(th -> th.enabled(true)),
                Map.class
            );

            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            List<UserSearchResult> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) hit.source();
                if (source != null) {
                    UserSearchResult r = new UserSearchResult();
                    r.setId(toLong(source.get("id")));
                    r.setUsername((String) source.get("username"));
                    r.setNickname((String) source.get("nickname"));
                    r.setAvatar((String) source.get("avatar"));
                    results.add(r);
                }
            }

            return new UserSearchResponse() {{
                setTotal(total); setPage(page); setSize(size); setResults(results);
            }};
        } catch (Exception e) {
            log.error("User search failed for q={}", q, e);
            UserSearchResponse empty = new UserSearchResponse();
            empty.setTotal(0); empty.setPage(page); empty.setSize(size); empty.setResults(List.of());
            return empty;
        }
    }

    private long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) return Long.parseLong(s);
        return 0L;
    }
}
```

- [ ] **Step 2: Create SuggestService**

```java
package com.example.searchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import com.example.common.SuggestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SuggestService {

    private static final Logger log = LoggerFactory.getLogger(SuggestService.class);

    private final ElasticsearchClient esClient;

    public SuggestService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public SuggestResponse suggest(String q) {
        List<SuggestResponse.Suggestion> results = new ArrayList<>();

        try {
            SearchResponse<Map> response = esClient.search(s -> s
                .index("notes", "users")
                .suggest(sug -> sug
                    .suggesters("note_suggest", ss -> ss
                        .prefix(q)
                        .completion(comp -> comp
                            .field("title.suggest")
                            .size(5)
                            .skipDuplicates(true)
                        )
                    )
                    .suggesters("user_suggest", ss -> ss
                        .prefix(q)
                        .completion(comp -> comp
                            .field("username.suggest")
                            .size(3)
                            .skipDuplicates(true)
                        )
                    )
                ),
                Map.class
            );

            if (response.suggest() != null) {
                // Note suggestions
                List<Suggestion<Map>> noteSuggestions = response.suggest().get("note_suggest");
                if (noteSuggestions != null) {
                    for (Suggestion<Map> sug : noteSuggestions) {
                        if (sug.completion() != null) {
                            for (CompletionSuggestOption<Map> opt : sug.completion().options()) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> source = (Map<String, Object>) opt.source();
                                Long id = source != null && source.get("id") != null
                                    ? ((Number) source.get("id")).longValue() : null;
                                results.add(new SuggestResponse.Suggestion(opt.text(), "note", id));
                            }
                        }
                    }
                }

                // User suggestions
                List<Suggestion<Map>> userSuggestions = response.suggest().get("user_suggest");
                if (userSuggestions != null) {
                    for (Suggestion<Map> sug : userSuggestions) {
                        if (sug.completion() != null) {
                            for (CompletionSuggestOption<Map> opt : sug.completion().options()) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> source = (Map<String, Object>) opt.source();
                                Long id = source != null && source.get("id") != null
                                    ? ((Number) source.get("id")).longValue() : null;
                                results.add(new SuggestResponse.Suggestion(opt.text(), "user", id));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Suggest failed for q={}", q, e);
        }

        return new SuggestResponse(results);
    }
}
```

- [ ] **Step 3: Create SearchRpcServiceImpl**

```java
package com.example.searchservice.rpc;

import com.example.common.NoteSearchRequest;
import com.example.common.NoteSearchResponse;
import com.example.common.SearchRpcService;
import com.example.common.SuggestResponse;
import com.example.common.UserSearchResponse;
import com.example.searchservice.service.NoteSearchService;
import com.example.searchservice.service.SuggestService;
import com.example.searchservice.service.UserSearchService;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class SearchRpcServiceImpl implements SearchRpcService {

    private final NoteSearchService noteSearchService;
    private final UserSearchService userSearchService;
    private final SuggestService suggestService;

    public SearchRpcServiceImpl(NoteSearchService noteSearchService, UserSearchService userSearchService, SuggestService suggestService) {
        this.noteSearchService = noteSearchService;
        this.userSearchService = userSearchService;
        this.suggestService = suggestService;
    }

    @Override
    public NoteSearchResponse searchNotes(NoteSearchRequest request) {
        return noteSearchService.search(request);
    }

    @Override
    public UserSearchResponse searchUsers(String q, int page, int size) {
        return userSearchService.search(q, page, size);
    }

    @Override
    public SuggestResponse suggest(String q) {
        return suggestService.suggest(q);
    }
}
```

- [ ] **Step 4: Compile search-service**

```bash
mvn compile -pl search-service
```

- [ ] **Step 5: Commit**

```bash
git add search-service/
git commit -m "feat: add UserSearchService, SuggestService, and SearchRpcServiceImpl"
```

---

### Task 12: gateway — SearchController + view count INCR

**Files:**
- Create: `gateway/src/main/java/com/example/gateway/controller/SearchController.java`
- Modify: `gateway/src/main/java/com/example/gateway/controller/NoteController.java`
- Modify: `gateway/src/main/resources/application.yml`
- Modify: `gateway/pom.xml` (add Redis dependency if needed)

- [ ] **Step 1: Create SearchController**

```java
package com.example.gateway.controller;

import com.example.common.*;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    @DubboReference
    private SearchRpcService searchRpcService;

    @GetMapping("/note")
    public Result<NoteSearchResponse> searchNotes(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "relevance") String sort) {
        NoteSearchRequest req = new NoteSearchRequest(q, page, size, category, sort);
        NoteSearchResponse resp = searchRpcService.searchNotes(req);
        return Result.success(resp);
    }

    @GetMapping("/user")
    public Result<UserSearchResponse> searchUsers(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserSearchResponse resp = searchRpcService.searchUsers(q, page, size);
        return Result.success(resp);
    }

    @GetMapping("/all")
    public Result<?> searchAll(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Multi-index: return both notes and users
        NoteSearchRequest noteReq = new NoteSearchRequest(q, page, size, null, "relevance");
        NoteSearchResponse notes = searchRpcService.searchNotes(noteReq);
        UserSearchResponse users = searchRpcService.searchUsers(q, page, size);

        return Result.success(new java.util.Map.Entry<>() {
            @Override
            public String getKey() { return "all"; }
            @Override
            public Object getValue() {
                return java.util.Map.of("notes", notes, "users", users);
            }
            @Override
            public Object setValue(Object value) { throw new UnsupportedOperationException(); }
        }.getValue());
    }

    @GetMapping("/suggest")
    public Result<SuggestResponse> suggest(@RequestParam String q) {
        SuggestResponse resp = searchRpcService.suggest(q);
        return Result.success(resp);
    }
}
```

Wait — the `searchAll` method has a typing issue. Let me use a proper approach:

```java
@GetMapping("/all")
public Result<Map<String, Object>> searchAll(
        @RequestParam String q,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size) {
    NoteSearchRequest noteReq = new NoteSearchRequest(q, page, size, null, "relevance");
    NoteSearchResponse notes = searchRpcService.searchNotes(noteReq);
    UserSearchResponse users = searchRpcService.searchUsers(q, page, size);
    Map<String, Object> combined = new HashMap<>();
    combined.put("notes", notes);
    combined.put("users", users);
    return Result.success(combined);
}
```

(Add `import java.util.HashMap; import java.util.Map;`)

- [ ] **Step 2: Add view INCR in NoteController**

Read the current `NoteController.java` first. Inject `StringRedisTemplate`. In `detail()` method, after building response:

```java
// async view increment
redisTemplate.opsForValue().increment("note:view:" + noteId);
```

If `StringRedisTemplate` is not available in gateway, add redis dependency to `gateway/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

And gateway `application.yml`:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

- [ ] **Step 3: Commit**

```bash
git add gateway/
git commit -m "feat: add SearchController and view count INCR in NoteController"
```

---

### Task 13: Docker Compose — ES, Canal, new services

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add ES + Canal + search services to docker-compose.yml**

Read the current `docker-compose.yml` first. Then add these service blocks in the Infrastructure section:

```yaml
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    container_name: elasticsearch
    environment:
      discovery.type: single-node
      xpack.security.enabled: false
      ES_JAVA_OPTS: -Xms512m -Xmx512m
    ports:
      - "9200:9200"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9200/_cluster/health"]
      interval: 10s
      timeout: 5s
      retries: 30
    networks:
      - live-community

  canal-server:
    image: canal/canal-server:v1.1.7
    container_name: canal-server
    environment:
      CANAL_ADDRESS: canal-server
      CANAL_PORT: 11111
      CANAL_DESTINATIONS: example
      CANAL_INSTANCE_MASTER_ADDRESS: mysql:3306
      CANAL_INSTANCE_DBUSERNAME: root
      CANAL_INSTANCE_DBPASSWORD: root
    ports:
      - "11111:11111"
    depends_on:
      mysql:
        condition: service_healthy
      kafka:
        condition: service_healthy
    networks:
      - live-community
```

Add in Application Services section:

```yaml
  search-sync-service:
    build: ./search-sync-service
    container_name: search-sync-service
    ports:
      - "8085:8085"
    environment:
      NACOS_HOST: nacos
      KAFKA_HOST: kafka
      ES_HOST: elasticsearch
      ES_PORT: "9200"
    depends_on:
      nacos: { condition: service_healthy }
      elasticsearch: { condition: service_healthy }
      kafka: { condition: service_healthy }
    networks:
      - live-community

  search-service:
    build: ./search-service
    container_name: search-service
    ports:
      - "8086:8086"
      - "20886:20886"
    environment:
      NACOS_HOST: nacos
      ES_HOST: elasticsearch
      ES_PORT: "9200"
    depends_on:
      nacos: { condition: service_healthy }
      elasticsearch: { condition: service_healthy }
    networks:
      - live-community
```

- [ ] **Step 2: Commit**

```bash
git add docker-compose.yml
git commit -m "feat: add ES, Canal, search-sync-service, search-service to docker-compose"
```

---

### Task 14: ES index templates + IK plugin init

**Files:**
- Create: `search-sync-service/src/main/resources/es-init.sh`

- [ ] **Step 1: Create ES init shell script (run manually or via entrypoint)**

```bash
#!/bin/bash
ES_URL="http://${ES_HOST:-localhost}:9200"

# Wait for ES to be ready
until curl -s "$ES_URL/_cluster/health" > /dev/null; do
  echo "Waiting for ES..."
  sleep 2
done

# Create notes index with IK + suggest
curl -X PUT "$ES_URL/notes" -H 'Content-Type: application/json' -d '{
  "settings": {
    "analysis": {
      "analyzer": {
        "ik_max_word": { "type": "custom", "tokenizer": "ik_max_word" },
        "ik_smart": { "type": "custom", "tokenizer": "ik_smart" }
      }
    }
  },
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
}'

# Create users index with IK + suggest
curl -X PUT "$ES_URL/users" -H 'Content-Type: application/json' -d '{
  "settings": {
    "analysis": {
      "analyzer": {
        "ik_max_word": { "type": "custom", "tokenizer": "ik_max_word" },
        "ik_smart": { "type": "custom", "tokenizer": "ik_smart" }
      }
    }
  },
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
      "status": { "type": "keyword" }
    }
  }
}'

echo "ES indices created successfully"
```

- [ ] **Step 2: Commit**

```bash
git add search-sync-service/src/main/resources/es-init.sh
git commit -m "feat: add ES index templates with IK analyzer and completion suggester"
```

---

### Task 15: Frontend — SearchPage + API integration

**Files:**
- Create: `front/src/pages/SearchPage.tsx`
- Modify: `front/src/api/index.ts`
- Modify: `front/src/App.tsx`

- [ ] **Step 1: Add search API functions to api/index.ts**

```typescript
export async function searchNotes(params: {
  q: string; page?: number; size?: number; category?: string; sort?: string;
}): Promise<NoteSearchResponse> {
  return request<NoteSearchResponse>('/api/search/note', { params });
}

export async function searchUsers(params: {
  q: string; page?: number; size?: number;
}): Promise<UserSearchResponse> {
  return request<UserSearchResponse>('/api/search/user', { params });
}

export async function searchSuggest(q: string): Promise<SuggestResponse> {
  return request<SuggestResponse>('/api/search/suggest', { params: { q } });
}
```

Read `front/src/types/index.ts` first. Then add the response types:

```typescript
export interface NoteSearchResult {
  id: number;
  userId: number;
  title: string;
  summary: string;
  tags: string;
  category: string;
  viewCount: number;
  likeCount: number;
  createdAt: number;
}

export interface NoteSearchResponse {
  total: number;
  page: number;
  size: number;
  results: NoteSearchResult[];
}

export interface UserSearchResult {
  id: number;
  username: string;
  nickname: string;
  avatar: string;
}

export interface UserSearchResponse {
  total: number;
  page: number;
  size: number;
  results: UserSearchResult[];
}

export interface Suggestion {
  text: string;
  type: 'note' | 'user';
  id: number;
}

export interface SuggestResponse {
  suggestions: Suggestion[];
}
```

- [ ] **Step 2: Create SearchPage.tsx**

```tsx
import { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { searchNotes, searchUsers, searchSuggest } from '../api';
import type { NoteSearchResult, UserSearchResult, Suggestion } from '../types';
import NoteCard from '../components/NoteCard';

type Tab = 'notes' | 'users';

export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const q = searchParams.get('q') || '';
  const [tab, setTab] = useState<Tab>('notes');
  const [input, setInput] = useState(q);
  const [notes, setNotes] = useState<NoteSearchResult[]>([]);
  const [users, setUsers] = useState<UserSearchResult[]>([]);
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const doSearch = useCallback(async () => {
    if (!q) return;
    setLoading(true);
    try {
      if (tab === 'notes') {
        const res = await searchNotes({ q, page: 1, size: 20 });
        setNotes(res.results);
        setTotal(res.total);
      } else {
        const res = await searchUsers({ q, page: 1, size: 20 });
        setUsers(res.results);
        setTotal(res.total);
      }
    } finally {
      setLoading(false);
    }
  }, [q, tab]);

  useEffect(() => {
    if (q) doSearch();
  }, [q, tab, doSearch]);

  useEffect(() => {
    if (input.length >= 2) {
      searchSuggest(input).then(res => setSuggestions(res.suggestions));
    } else {
      setSuggestions([]);
    }
  }, [input]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setSearchParams({ q: input });
  };

  const handleSuggestionClick = (s: Suggestion) => {
    setInput(s.text);
    setSearchParams({ q: s.text });
  };

  return (
    <div className="max-w-2xl mx-auto px-4 py-4">
      <form onSubmit={handleSearch} className="relative mb-4">
        <input
          type="text"
          value={input}
          onChange={e => setInput(e.target.value)}
          placeholder="搜索笔记或用户..."
          className="w-full px-4 py-3 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        {suggestions.length > 0 && (
          <ul className="absolute top-full left-0 right-0 bg-white border rounded-lg mt-1 shadow-lg z-50">
            {suggestions.map((s, i) => (
              <li
                key={i}
                className="px-4 py-2 hover:bg-gray-50 cursor-pointer flex justify-between"
                onClick={() => handleSuggestionClick(s)}
              >
                <span>{s.text}</span>
                <span className="text-xs text-gray-400">{s.type === 'note' ? '笔记' : '用户'}</span>
              </li>
            ))}
          </ul>
        )}
      </form>

      <div className="flex gap-2 mb-4 border-b">
        <button
          className={`px-4 py-2 ${tab === 'notes' ? 'border-b-2 border-blue-500 text-blue-600' : 'text-gray-500'}`}
          onClick={() => setTab('notes')}
        >
          笔记 ({tab === 'notes' ? total : ''})
        </button>
        <button
          className={`px-4 py-2 ${tab === 'users' ? 'border-b-2 border-blue-500 text-blue-600' : 'text-gray-500'}`}
          onClick={() => setTab('users')}
        >
          用户 ({tab === 'users' ? total : ''})
        </button>
      </div>

      {loading ? (
        <div className="text-center py-8 text-gray-400">搜索中...</div>
      ) : q ? (
        tab === 'notes' ? (
          notes.length > 0 ? (
            <div className="space-y-3">
              {notes.map(note => <NoteCard key={note.id} note={note} />)}
            </div>
          ) : (
            <div className="text-center py-8 text-gray-400">未找到相关笔记</div>
          )
        ) : (
          users.length > 0 ? (
            <div className="space-y-2">
              {users.map(user => (
                <div key={user.id} className="flex items-center gap-3 p-3 bg-white rounded-lg">
                  <img src={user.avatar || '/default-avatar.png'} className="w-10 h-10 rounded-full" />
                  <div>
                    <div className="font-medium">{user.nickname}</div>
                    <div className="text-sm text-gray-400">@{user.username}</div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-8 text-gray-400">未找到相关用户</div>
          )
        )
      ) : (
        <div className="text-center py-8 text-gray-400">输入关键词开始搜索</div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Add route in App.tsx**

```tsx
import SearchPage from './pages/SearchPage';

// inside Routes:
<Route path="/search" element={<AuthGuard><SearchPage /></AuthGuard>} />
```

- [ ] **Step 4: Add search entry in TabBar or header**

Read `TabBar.tsx` to understand the current tab structure. Add a search tab/icon.

- [ ] **Step 5: Commit**

```bash
git add front/
git commit -m "feat: add SearchPage with autocomplete, tab switching, and API integration"
```

---

### Task 16: Full build verification

- [ ] **Step 1: Compile all modules**

```bash
mvn compile
```

- [ ] **Step 2: Verify package**

```bash
mvn package -DskipTests
```

- [ ] **Step 3: Commit any remaining changes**

```bash
git add -A
git commit -m "chore: finalize search system integration"
```

---

### Task 17: Feature documentation

**Files:**
- Create: `docs/feature/08-搜索功能.md`

Write a comprehensive feature document following the same pattern as existing feature docs (see `05-Sentinel限流功能.md` for reference). Cover:
1. Architecture overview — Canal + Kafka + ES data flow diagram
2. Design patterns — Strategy (routing to different indices), Chain (Canal→Kafka→ES pipeline), Template (Function Score query builder)
3. Scoring formula explanation
4. Key code locations table
5. API reference
6. Interview Q&A
