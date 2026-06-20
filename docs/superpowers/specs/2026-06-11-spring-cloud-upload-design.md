# Spring Cloud 上传框架 — 设计文档

## 目标

搭建一套最小可用的 Spring Cloud 微服务框架，为前端提供 MinIO 直传 presigned URL，并通过 Sentinel 在网关层对上传接口进行限流保护。

## 技术栈

| 组件 | 版本 | 作用 |
|------|------|------|
| Spring Boot | 3.2.x | 基础框架 |
| Spring Cloud | 2023.0.x | 微服务框架 |
| Spring Cloud Alibaba | 2023.0.x | Nacos + Sentinel 集成 |
| Spring Cloud Gateway | — | 网关，路由转发 + Sentinel 限流 |
| Nacos | 2.3.x (server) | 注册中心 + 配置中心 |
| Sentinel | 1.8.x (dashboard) | 限流规则管理 + 实时监控 |
| MinIO | 8.5.x (client SDK) | 对象存储，生成 presigned URL |

## 模块结构

```
spring-cloud-test/
├── pom.xml                    # 父 POM，统一依赖管理
├── gateway/                   # 网关模块
│   ├── pom.xml
│   └── src/main/java/...
│       └── GatewayApplication.java
├── upload-service/            # 上传服务
│   ├── pom.xml
│   └── src/main/java/...
│       └── UploadServiceApplication.java
└── common/                    # 公共模块
    ├── pom.xml
    └── src/main/java/...      # 统一响应体 Result<T>、异常定义
```

## 核心数据流

```
前端                      Gateway                 upload-service          MinIO
 │                          │                         │                     │
 │  GET /api/upload/        │                         │                     │
 │  presigned?fileName=     │                         │                     │
 │  &contentType=           │                         │                     │
 ├─────────────────────────►│                         │                     │
 │                          │  Sentinel 限流检查      │                     │
 │                          ├────────────────────────►│                     │
 │                          │                         │  生成 presigned URL  │
 │                          │                         ├────────────────────►│
 │                          │                         │◄────────────────────┤
 │                          │  ◄─── 200 {url...} ────┤                     │
 │  ◄── 200 {url,expires} ──┤                         │                     │
 │                          │                         │                     │
 │  PUT presigned URL ───────────────────────────────────────────────────►│
 │  (前端直传，不经过后端)                                                  │
```

## Gateway 配置

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
    gateway:
      routes:
        - id: upload-service
          uri: lb://upload-service
          predicates:
            - Path=/api/upload/**
    sentinel:
      datasource:
        ds1:
          nacos:
            server-addr: localhost:8848
            data-id: gateway-flow-rules
            group-id: DEFAULT_GROUP
            data-type: json
            rule-type: gw-flow
      scg:
        fallback:
          response-status: 429
          response-body: '{"code":429,"msg":"请求太频繁，请稍后再试"}'
```

## Sentinel 限流规则

存储在 Nacos，`data-id: gateway-flow-rules`，`group: DEFAULT_GROUP`：

```json
[
  {
    "resource": "upload-service",
    "resourceMode": 0,
    "grade": 1,
    "count": 50,
    "intervalSec": 1,
    "controlBehavior": 0,
    "burst": 0
  }
]
```

- `grade: 1` = QPS 模式，默认每秒 50 次
- `resourceMode: 0` = 按路由 ID 限流
- 规则在 Nacos 中修改后实时生效

## Upload Service 接口

```
GET /api/upload/presigned?fileName=xxx&contentType=xxx

请求参数:
  - fileName (required): 原始文件名
  - contentType (required): MIME 类型，如 image/png

返回:
  - uploadUrl: 前端直接用此 URL 做 PUT 上传
  - objectKey: 对象存储路径（yyyy/MM/dd/uuid_filename）
  - expiresAt: 过期时间（默认 5 分钟）
```

MinIO 配置：

```yaml
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket: uploads
  presigned-expiry: 300
```

## 启动方式

1. 启动 Nacos Server（`standalone` 模式）
2. 启动 Sentinel Dashboard（`java -jar sentinel-dashboard.jar`）
3. 在 Nacos 控制台创建 `gateway-flow-rules` 配置
4. 启动 `gateway`，注册到 Nacos
5. 启动 `upload-service`，注册到 Nacos
6. 启动 MinIO（`minio server /data`）

## 约束

- 限流仅保护 presigned URL 接口，不做全局限流
- 不做用户认证，后续按需叠加
- presigned URL 过期时间默认 5 分钟
- Nacos 和 Sentinel Dashboard 需提前部署
