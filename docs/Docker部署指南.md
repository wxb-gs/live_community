# Docker Compose 部署指南

## 架构总览

```
                    ┌─────────────┐
                    │   gateway   │  :8080  (REST 入口)
                    └──────┬──────┘
                           │ Dubbo (Triple)
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
  │upload-service│ │ leaf-service │ │ note-service │
  │  :8081/20880 │ │  :8082/20881 │ │  :8083/20882 │
  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
         │                │                │
         ▼                ▼                ▼
  ┌──────────┐   ┌──────────┐     ┌───────────┐
  │  MinIO   │   │  MySQL   │     │ Cassandra │
  │  :9000   │   │  :3306   │     │  :9042    │
  └──────────┘   └──────────┘     └───────────┘
         ▲
         │ (前端直传, 预签名PUT)
         │
  ┌──────────┐
  │  Client  │
  └──────────┘

  服务注册 & 发现: Nacos :8848
```

## 系统要求

| 组件 | 最低要求 |
|------|---------|
| Docker | 28.x+ (已安装: `docker --version`) |
| Docker Compose | v2.40.x+ (已安装: `docker compose version`) |
| 磁盘 | ~10GB (镜像 + 数据卷) |
| 内存 | 建议 8GB+ (全部服务运行约需 4-5GB) |

## 文件结构

```
spring_cloud_test/
├── docker-compose.yml              # 完整编排文件
├── gateway/Dockerfile              # gateway 镜像
├── upload-service/Dockerfile       # upload-service 镜像
├── leaf-service/Dockerfile         # leaf-service 镜像
├── note-service/Dockerfile         # note-service 镜像
├── leaf-service/src/main/resources/sql/init_leaf.sql    # MySQL 初始化
└── note-service/src/main/resources/sql/init_cassandra.cql  # Cassandra 手动脚本
```

## 使用的镜像

| 镜像 | 用途 | 来源 |
|------|------|------|
| `eclipse-temurin:17-jre-alpine` | Java 运行时 | Docker Hub (自动拉取) |
| `nacos/nacos-server:v2.3.0` | 服务注册 & 发现 | Docker Hub (自动拉取) |
| `mysql:8.0` | Leaf 号段存储 | Docker Hub (自动拉取) |
| `minio/minio:latest` | 对象存储 OSS | Docker Hub (自动拉取) |
| `minio/mc:latest` | MinIO 客户端(建 bucket) | Docker Hub (自动拉取) |
| `cassandra:latest` | 笔记 & 评论存储 | Docker Hub (自动拉取) |

应用镜像由本地 Dockerfile 构建（~250MB/个）。

## 快速启动

### 1. 构建 JAR 包

```bash
# 在项目根目录执行
mvn clean package -DskipTests
```

产物：
- `gateway/target/gateway-1.0.0-SNAPSHOT.jar`
- `upload-service/target/upload-service-1.0.0-SNAPSHOT.jar`
- `leaf-service/target/leaf-service-1.0.0-SNAPSHOT.jar`
- `note-service/target/note-service-1.0.0-SNAPSHOT.jar`

### 2. 构建镜像 + 启动全部服务

```bash
docker compose up -d --build
```

### 3. 查看启动状态

```bash
docker compose ps
```

期望状态：全部 `healthy` / `Up`

```bash
docker compose logs -f          # 全部日志
docker compose logs -f gateway  # 单个服务日志
```

### 4. 停止 & 清理

```bash
docker compose down             # 停止，保留数据卷
docker compose down -v          # 停止，删除所有数据(重置)
```

## 启动顺序 & 依赖关系

```
1. nacos       ── 先启动，所有服务依赖它注册
2. mysql       ── 自动执行 init_leaf.sql 建库建表
3. minio       ── 健康检查通过后，minio-init 建 bucket
4. cassandra   ── 健康检查通过 (cqlsh)
5. leaf-service   ── 依赖 nacos + mysql
6. upload-service ── 依赖 nacos + minio
7. note-service   ── 依赖 nacos + minio + cassandra + leaf-service
8. gateway        ── 依赖 nacos + upload-service + note-service
```

`depends_on` + `condition: service_healthy` 保证了正确的启动顺序。

## 端口映射

| 服务 | 对外端口 | 容器内端口 | 说明 |
|------|---------|-----------|------|
| Nacos | 18848, 19848 | 8848, 9848 | HTTP API + gRPC |
| MySQL | 13306 | 3306 | JDBC |
| MinIO | 19000, 19001 | 9000, 9001 | API + Console |
| Cassandra | 19042 | 9042 | CQL |
| gateway | 8080 | 8080 | REST 入口 |
| upload-service | 8081, 20880 | 8081, 20880 | HTTP + Dubbo |
| leaf-service | 8082, 20881 | 8082, 20881 | HTTP + Dubbo |
| note-service | 8083, 20882 | 8083, 20882 | HTTP + Dubbo |

## 环境变量映射

Docker Compose 通过环境变量覆盖 Spring Boot 配置，将 `localhost` 替换为 Docker 内部服务名：

| 环境变量 | 覆盖的 application.yml 配置 |
|----------|---------------------------|
| `SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/live_community?...` | `spring.datasource.url` |
| `SPRING_CLOUD_NACOS_DISCOVERY_SERVER-ADDR=nacos:8848` | `spring.cloud.nacos.discovery.server-addr` |
| `DUBBO_REGISTRY_ADDRESS=nacos://nacos:8848` | `dubbo.registry.address` |
| `MINIO_ENDPOINT=http://minio:9000` | `minio.endpoint` |
| `SPRING_CASSANDRA_CONTACT-POINTS=cassandra` | `spring.cassandra.contact-points` |

## 数据持久化

| 卷名 | 挂载路径 | 内容 |
|------|---------|------|
| `mysql-data` | `/var/lib/mysql` | Leaf 号段数据 |
| `minio-data` | `/data` | 上传文件 |
| `cassandra-data` | `/var/lib/cassandra` | 笔记 & 评论 |

## 验证测试

### 1. Nacos 控制台

浏览器打开 http://localhost:18848/nacos ，应看到注册的服务列表：
- `gateway`
- `upload-service`
- `leaf-service`
- `note-service`

### 2. MinIO Console

浏览器打开 http://localhost:19001 ，用户名/密码 `minioadmin/minioadmin`。
应看到 `uploads` 和 `notes` 两个 bucket。

### 3. Leaf ID 生成

```bash
# 号段模式
curl http://localhost:8082/api/leaf/segment?key=note
# → {"id":1,"mode":"segment"}

# 雪花模式
curl http://localhost:8082/api/leaf/snowflake
# → {"id":1234567890123456789,"mode":"snowflake"}
```

### 4. 上传预签名 URL (通过网关)

```bash
curl "http://localhost:8080/api/upload/presigned?fileName=test.jpg&contentType=image/jpeg"
# → {"code":200,"msg":"success","data":{"uploadUrl":"http://...","objectKey":"...","expiresAt":...}}
```

### 5. 笔记草稿 → 发布 → 详情

```bash
# 创建草稿
curl -X POST http://localhost:8080/api/note/draft \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"title":"Hello","content":"笔记内容..."}'
# → {"code":200,"msg":"success","data":{"noteId":1,"status":"DRAFT"}}

# 发布笔记 (获取预签名上传URL)
curl -X POST http://localhost:8080/api/note/publish \
  -H "Content-Type: application/json" \
  -d '{"noteId":1,"fileName":"cover.jpg","contentType":"image/jpeg"}'
# → {"code":200,"msg":"success","data":{"uploadUrl":"http://...","status":"PUBLISHED",...}}

# 查看笔记详情 (CompletableFuture 并行查询笔记+评论)
curl "http://localhost:8080/api/note/detail?noteId=1"
# → {"code":200,"msg":"success","data":{"noteId":1,"title":"Hello","comments":[],...}}

# 添加评论
curl -X POST http://localhost:8080/api/note/comment \
  -H "Content-Type: application/json" \
  -d '{"noteId":1,"userId":2,"content":"好文章！"}'
# → {"code":200,"msg":"success","data":{"commentId":1,"noteId":1,...}}
```

## 常见问题

### Q: 某个服务启动失败、反复重启？

```bash
docker compose logs leaf-service | tail -50
```

### Q: Nacos 中没有注册服务？

等待 1-2 分钟，Dubbo 服务注册有延迟。查看日志确认：
```bash
docker compose logs upload-service | grep -i "dubbo\|register"
```

### Q: MinIO bucket 没创建？

检查 `minio-init` 容器是否成功退出：
```bash
docker compose logs minio-init
```

### Q: Cassandra 启动慢？

Cassandra 首次启动需要初始化，约 60-90 秒。`start_period: 60s` 已配置，耐心等待健康检查通过。

### Q: 想要完全重置？

```bash
docker compose down -v      # 删除容器 + 数据卷
docker compose up -d --build # 重新构建并启动
```

## 开发模式

如果只在本地开发部分服务，可以混合模式运行：

```bash
# 只启动基础设施
docker compose up -d nacos mysql minio cassandra minio-init

# 本地 IDE 中启动应用服务 (修改 application.yml 中 localhost 即可)
```

Docker Compose 已将基础设施端口映射到宿主机，应用服务仍可用 `localhost` 连接。
