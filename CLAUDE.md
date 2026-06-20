# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

```bash
mvn compile                          # Compile all modules
mvn test                             # Run all tests
mvn test -pl upload-service -Dtest=UploadControllerTest   # Single test class
mvn clean package -DskipTests        # Package all modules
```

## Project Architecture

Spring Cloud Alibaba + Dubbo multi-module Maven project — MinIO direct-upload with Sentinel rate limiting, Meituan Leaf distributed ID, and Cassandra-backed notes service.

### Modules

| Module | Role | Port | Key Framework |
|--------|------|------|---------------|
| `common` | Shared library | — | Plain Java (`Result<T>`, Dubbo RPC contract DTOs) |
| `gateway` | REST entrypoint + Dubbo consumer | 8080 | Spring Boot Web, Dubbo, Sentinel |
| `upload-service` | Dubbo provider + MinIO client | 8081 / 20880 | Spring Boot Web, Dubbo, MinIO SDK |
| `leaf-service` | Distributed ID (Meituan Leaf) | 8082 / 20881 | Spring Boot Web, Dubbo, JDBC, MySQL |
| `note-service` | Notes + comments with Cassandra | 8083 / 20882 | Spring Boot Web, Dubbo, MinIO, Cassandra |

### Call Flow — Upload

```
Client (HTTP GET /api/upload/presigned?fileName=&contentType=)
  → gateway:8080  UploadController (@RestController)
    → @DubboReference UploadRpcService.generatePresignedUrl(req)
      → [Dubbo Triple, Nacos discovery]
        → upload-service:20880  UploadRpcServiceImpl (@DubboService)
          → MinioClient.getPresignedObjectUrl()  →  MinIO:9000
          → PresignedUrlResponse {uploadUrl, objectKey, expiresAt}
  ← Result<PresignedUrlResponse> (JSON)
```

### Call Flow — Notes

```
Client (HTTP POST /api/note/draft)
  → gateway:8080  NoteController (@RestController)
    → @DubboReference NoteRpcService.createDraft(req)
      → [Dubbo Triple, Nacos discovery]
        → note-service:20882  NoteRpcServiceImpl (@DubboService)
          → @DubboReference LeafRpcService.generateSegmentId("note")
            → leaf-service:20881  LeafRpcServiceImpl (@DubboService)
              → SegmentIdGenerator.getId()  [double-buffer, MySQL-backed]
          → NoteRepository.save()  →  Cassandra:9042
  ← Result<CreateDraftResponse> (JSON)

Client (HTTP POST /api/note/publish)
  → gateway:8080  NoteController
    → NoteRpcService.publishNote(req)  → note-service
      → MinioClient.getPresignedObjectUrl()  →  MinIO:9000 (bucket: notes)
      → NoteRepository.save()  →  Cassandra:9042
  ← Result<NoteDetailResponse> (JSON, includes presigned uploadUrl)

Client (HTTP GET /api/note/detail?noteId=)
  → gateway:8080  NoteController
    → NoteRpcService.getNoteDetail(noteId)  → note-service
      → CompletableFuture:
          ├─ NoteRepository.findById()     →  Cassandra:9042
          └─ CommentRepository.findByNoteId()  →  Cassandra:9042
  ← Result<NoteDetailResponse> (JSON, note + comments)
```

### Leaf Distributed ID Architecture

**Segment mode** (号段模式): Double-buffered, MySQL-backed number range allocation. Table `leaf_alloc` (biz_tag, max_id, step). When current segment exhausts, switches to pre-loaded next segment while async-loading another. Biz tags: `note`, `comment`.

**Snowflake mode** (雪花算法): 41-bit timestamp (epoch 1700000000000) + 5-bit datacenter + 5-bit worker + 12-bit sequence. Clock-backwards tolerant (≤5ms wait, >5ms reject). Worker/datacenter IDs from `leaf.snowflake.*` config.

**Endpoints**: `GET /api/leaf/segment?key=`, `GET /api/leaf/snowflake`

### Dubbo Configuration

**gateway** (`application.yml`): consumer only — `dubbo.application.name=gateway`, `dubbo.registry.address=nacos://localhost:8848`, no protocol port.

**upload-service** (`application.yml`): provider — same registry, plus `dubbo.protocol.name=tri`, `dubbo.protocol.port=20880`.

**leaf-service** (`application.yml`): provider — same registry, plus `dubbo.protocol.name=tri`, `dubbo.protocol.port=20881`. Also consumer of N/A (standalone).

**note-service** (`application.yml`): provider + consumer — same registry, `dubbo.protocol.name=tri`, `dubbo.protocol.port=20882`. Consumes `LeafRpcService` from leaf-service.

**Contract** (`common` module): `UploadRpcService`, `LeafRpcService`, `NoteRpcService` interfaces + all DTOs (all `Serializable`).

### Sentinel

`@SentinelResource(value = "upload-presigned", blockHandler = "rateLimitFallback")` on gateway's `UploadController.presignedUrl()`. Fallback returns `Result.error(429, "Too many requests...")`. No Nacos datasource needed — block handler is inline.

### Source Layout

```
common/src/main/java/com/example/common/
  Result.java                  — Generic API response wrapper
  UploadRpcService.java        — Dubbo RPC: upload operations
  LeafRpcService.java          — Dubbo RPC: ID generation (segment + snowflake)
  NoteRpcService.java          — Dubbo RPC: note CRUD + comments
  PresignedUrlRequest.java     — Upload request DTO
  PresignedUrlResponse.java    — Upload response DTO
  IdResponse.java              — ID response DTO
  CreateDraftRequest.java      — Draft request DTO
  CreateDraftResponse.java     — Draft response DTO
  PublishNoteRequest.java      — Publish request DTO
  NoteDetailResponse.java      — Note detail DTO (includes comments list)
  CommentRequest.java          — Comment request DTO
  CommentResponse.java         — Comment response DTO

gateway/src/main/java/com/example/gateway/
  GatewayApplication.java      — @SpringBootApplication + @EnableDubbo
  controller/
    UploadController.java      — @RestController, UploadRpcService, @SentinelResource
    NoteController.java        — @RestController, NoteRpcService (draft/publish/detail/comment)

upload-service/src/main/java/com/example/upload/
  UploadServiceApplication.java — @SpringBootApplication + @EnableDubbo + @EnableDiscoveryClient
  config/MinioConfig.java       — MinioClient bean
  controller/UploadController.java — Direct REST access (health checks)
  rpc/UploadRpcServiceImpl.java   — @DubboService, implements UploadRpcService

leaf-service/src/main/java/com/example/leaf/
  LeafServiceApplication.java   — @SpringBootApplication + @EnableDubbo + @EnableDiscoveryClient
  segment/
    model/Segment.java          — AtomicLong-based ID range holder
    model/SegmentBuffer.java    — Double-buffer with read/write lock
    SegmentIdGenerator.java     — Core: MySQL-backed segment allocation + async prefetch
  snowflake/
    SnowflakeIdGenerator.java   — Standard 64-bit snowflake, clock-backward tolerant
  controller/LeafController.java   — REST: GET /api/leaf/segment, /api/leaf/snowflake
  rpc/LeafRpcServiceImpl.java     — @DubboService, implements LeafRpcService

note-service/src/main/java/com/example/note/
  NoteServiceApplication.java   — @SpringBootApplication + @EnableDubbo + @EnableDiscoveryClient
  config/CassandraConfig.java   — Cassandra session + keyspace config
  config/MinioConfig.java       — MinioClient bean (notes bucket)
  entity/NoteEntity.java        — Cassandra @Table("note")
  entity/CommentEntity.java     — Cassandra @Table("comment"), PK (note_id, comment_id)
  repository/NoteRepository.java    — CassandraRepository<NoteEntity, Long>
  repository/CommentRepository.java — CassandraRepository + @Query findByNoteId
  service/NoteService.java      — Core logic: draft/publish/detail/comment with CompletableFuture
  controller/NoteController.java   — REST: POST /api/note/draft, /publish, /comment; GET /detail
  rpc/NoteRpcServiceImpl.java     — @DubboService, implements NoteRpcService
```

### Key Tech Stack

Java 17 · Spring Boot 3.2.6 · Spring Cloud 2023.0.3 · Spring Cloud Alibaba 2023.0.1.0 · Dubbo 3.3.0 (Triple protocol) · MinIO SDK 8.5.10 · Nacos 2.x · Sentinel · MySQL 8.0 (Leaf segment) · Spring Data Cassandra 4.2.6

### Test Notes

- `UploadControllerTest` needs `dubbo.registry.address=N/A` and `spring.cloud.nacos.discovery.enabled=false` to start without Nacos/MinIO
- `presignedUrl_shouldReturn200_whenValidParams` requires MinIO running on `localhost:9000`
- Gateway has no tests yet
- Leaf segment tests need MySQL with `live_community` database and `leaf_alloc` table
- Note service tests need Cassandra with `notes` keyspace

## Docker Compose Deployment

```bash
mvn clean package -DskipTests     # Build fat JARs (each ~60-80MB)
docker compose up -d --build      # Build images + start all 9 services
docker compose ps                 # Verify all healthy
docker compose down -v            # Full teardown
```

Full guide with architecture diagrams, startup order, and API verification examples: `docs/Docker部署指南.md`

## Local Startup Order (without Docker)

1. Nacos (`localhost:8848`, standalone mode)
2. MySQL (`localhost:3306`, database `live_community`, table `leaf_alloc`)
3. MinIO (`localhost:9000`, create buckets `uploads` + `notes`)
4. Cassandra (`localhost:9042`, keyspace `notes` auto-created by note-service)
5. leaf-service (registers with Nacos + Dubbo, connects to MySQL)
6. upload-service (registers with both Nacos discovery and Dubbo)
7. note-service (registers with Nacos + Dubbo, connects to Cassandra + MinIO)
8. gateway (connects to Dubbo registry, discovers all services)

Full guide: `docs/启动指南.md`
