# Dubbo RPC 集成设计文档

## 目标

将 Gateway 调用 upload-service 的方式从 HTTP 路由转发改为 Dubbo RPC 调用。

## 架构变化

```
Client HTTP Request (JSON)
    ↓
Gateway (Spring Boot Web, 8080)
  ├─ @RestController /api/upload/presigned
  ├─ @DubboReference UploadRpcService
  └─ @SentinelResource 限流兜底
    ↓ Dubbo RPC (Triple 协议, 20880)
upload-service (Spring Boot + Dubbo, 20880)
  ├─ @DubboService UploadServiceImpl
  └─ MinioClient → MinIO (9000)
```

### 模块变化

| 模块 | 变化 |
|------|------|
| `common` | 新增 `UploadRpcService` 接口、`PresignedUrlRequest`、`PresignedUrlResponse` |
| `gateway` | 去掉 SCG/Nacos-discovery/Sentinel-gateway 依赖，加 spring-web/dubbo-starter/nacos-registry，新增 `UploadController`，改 `application.yml` |
| `upload-service` | 加 dubbo-starter/nacos-registry，新增 `@DubboService` 实现类，`application.yml` 加 Dubbo 配置 |

## 接口契约

所有新增类型放在 `common` 模块，package `com.example.common`：

- `UploadRpcService` — Dubbo 服务接口，方法 `PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest)`
- `PresignedUrlRequest` — 含 `fileName`、`contentType`
- `PresignedUrlResponse` — 含 `uploadUrl`、`objectKey`、`expiresAt`

## 关键决策

- **Triple 协议**：Dubbo 3.x 原生 HTTP/2 协议，端口 20880
- **保留 upload-service HTTP 端口**：方便健康检查和直接调试
- **Nacos 复用**：同时作为 Dubbo 注册中心和 Sentinel 规则存储
- **Sentinel 限流保留在 Gateway**：用 `@SentinelResource` 注解，超限返回 HTTP 429
- **Sentinel 规则**：注解方式 `@SentinelResource(value = "upload-presigned", blockHandler = "rateLimitFallback")`，QPS 50

## 依赖变更

### gateway
移除：spring-cloud-starter-gateway、spring-cloud-alibaba-sentinel-gateway、spring-cloud-starter-alibaba-nacos-discovery
新增：spring-boot-starter-web、dubbo-spring-boot-starter（3.x）、dubbo-registry-nacos、common 模块依赖

### upload-service
新增：dubbo-spring-boot-starter（3.x）、dubbo-registry-nacos

## 配置变更

### gateway application.yml
- 去掉 `spring.cloud.gateway.routes`、`spring.cloud.sentinel.*`
- 新增 `dubbo.application.name=gateway`、`dubbo.registry.address=nacos://localhost:8848`、`dubbo.protocol.name=tri`、`dubbo.protocol.port=20880`

### upload-service application.yml
- 新增 `dubbo.application.name=upload-service`、`dubbo.registry.address=nacos://localhost:8848`、`dubbo.protocol.name=tri`、`dubbo.protocol.port=20880`

## 测试

- 现有 `UploadControllerTest` 不依赖 Dubbo（HTTP 层不变），无需修改
- 可选新增 Gateway 层的集成测试（Mock Dubbo 服务）
