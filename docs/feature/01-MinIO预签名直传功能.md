# MinIO 预签名直传功能

## 1. 功能概述

### 1.1 什么是预签名上传？

预签名上传（Presigned Upload）是一种服务端授权、客户端直传的文件上传方案。服务端生成一个带签名的临时 URL，客户端拿到这个 URL 后可以直接将文件上传到 MinIO（对象存储），**文件流不经过应用服务器**。

### 1.2 为什么需要预签名直传？

| 方案 | 数据流 | 服务端压力 | 适用场景 |
|------|--------|-----------|---------|
| 服务端中转上传 | Client → Server → MinIO | 高（内存、带宽双倍消耗） | 小文件、需要服务端二次加工 |
| **预签名直传** | Client → MinIO | 极低（仅生成 URL） | 大文件、高并发上传 |
| 客户端直接上传 | Client → MinIO | 无 | 不安全（需暴露 accessKey/secretKey） |

预签名直传是**安全与性能的平衡点**：客户端无需持有对象存储凭证，服务端只做授权不搬运数据。

### 1.3 本项目的调用链路

```
Client (浏览器/App)
  ↓ HTTP GET /api/upload/presigned?fileName=photo.jpg&contentType=image/jpeg
gateway:8080  UploadController (@RestController)
  ↓ @DubboReference UploadRpcService.generatePresignedUrl(req)
  ↓ Dubbo Triple 协议 (Nacos 注册中心发现)
upload-service:20880  UploadRpcServiceImpl (@DubboService)
  ↓ MinioClient.getPresignedObjectUrl() → MinIO:9000
  ↑ PresignedUrlResponse {uploadUrl, objectKey, expiresAt}
Client
  ↓ HTTP PUT <uploadUrl> (直接上传文件二进制流到 MinIO)
MinIO:9000
```

**关键点**：gateway 不存 MinIO 配置，MinIO SDK 只在 upload-service 中存在。职责分离清晰。

---

## 2. 详细实现分析

### 2.1 Object Key 设计

```java
// UploadRpcServiceImpl.java:33-34
String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
String objectKey = datePath + "/" + UUID.randomUUID() + "_" + request.getFileName();
```

**设计考量**：
- **日期分区** `yyyy/MM/dd`：按天分目录，便于 MinIO 生命周期管理（如 30 天后自动过期）、也便于人工排查
- **UUID 前缀**：防止文件名冲突（两个用户同时上传 `photo.jpg` 不会覆盖）
- **保留原始文件名**：方便下载时识别文件类型

**对比方案**：
| 方案 | 示例 | 优点 | 缺点 |
|------|------|------|------|
| 纯 UUID | `a1b2c3d4.jpg` | 最简单，绝对不会冲突 | 不可读，无法按时间管理 |
| 日期+UUID+原名（当前） | `2026/06/20/uuid_photo.jpg` | 可读性好，便于生命周期管理 | 路径较长 |
| 用户ID+日期 | `1001/2026/06/20/photo.jpg` | 按用户隔离 | 暴露用户 ID，单用户文件名可能冲突 |

### 2.2 预签名 URL 过期时间

```java
// application.yml
minio:
  presigned-expiry: 300  # 默认 300 秒 = 5 分钟
```

```java
// 生成 URL 时指定过期时间
.expiry(presignedExpiry, TimeUnit.SECONDS)
```

**为什么是 5 分钟？**
- 太短（如 30 秒）：客户端可能来不及完成上传，用户体验差
- 太长（如 1 小时）：安全风险增加，被盗用的 URL 窗口期大
- 5 分钟是一个常见的折中值：足够用户发起上传，又不至于太危险

**expiresAt 字段的作用**：响应中返回 `expiresAt`，客户端可以判断 URL 是否即将过期，提前重新申请。

### 2.3 MinIO 配置管理

```java
// MinioConfig.java
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {
    private String endpoint;    // MinIO 地址
    private String accessKey;   // 访问密钥
    private String secretKey;   // 秘密密钥

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
```

**为什么用 `@ConfigurationProperties` 而不是 `@Value`？**
- `@ConfigurationProperties` 支持松散绑定（`minio.access-key` = `minio.accessKey`）
- 类型安全：如果配置缺失，启动时报错而非运行时 NPE
- 可以通过 IDE 的 spring-boot-configuration-processor 生成元数据，获得自动补全

---

## 3. 实现难点

### 3.1 Dubbo 序列化约束

DTO 必须实现 `java.io.Serializable` 并声明 `serialVersionUID`：

```java
public class PresignedUrlRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    // ...
}
```

**为什么？** Dubbo Triple 协议底层默认使用 Java 序列化（或 Hessian2），跨 JVM 传输必须可序列化。缺少 `serialVersionUID` 会导致不同版本间反序列化失败。

### 3.2 异常处理策略

```java
try {
    String url = minioClient.getPresignedObjectUrl(...);
    return new PresignedUrlResponse(url, objectKey, expiresAt);
} catch (Exception e) {
    throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
}
```

**当前设计**：将 MinIO SDK 的受检异常包装为 `RuntimeException` 抛出。Dubbo 会将异常序列化传递给调用方（gateway），gateway 默认的异常处理会返回 HTTP 500。

**改进方向**：可以定义业务异常（如 `MinioOperationException`），在 gateway 层做统一的异常翻译。

### 3.3 直接访问 vs 通过网关

upload-service 自身也有一个 `UploadController`（REST 接口），直接暴露 HTTP 接口。这是两个访问路径：

- **gateway → Dubbo → upload-service**：网关路径，有 Sentinel 限流保护
- **直连 upload-service:8081**：用于内部调试和健康检查

**为什么要有两个入口？** Dubbo 接口提供给微服务间调用，REST 接口方便开发调试和 K8s 探活。生产环境应只通过网关暴露。

---

## 4. 面试准备

### 4.1 高频问题

**Q: 预签名 URL 的安全性如何保证？**
> 1. URL 中包含基于 secretKey 计算的签名，篡改任何参数签名会失效
> 2. 有过期时间限制（本项目 5 分钟），过期后 URL 不可用
> 3. URL 只授权 PUT 操作（可限制 method），不能用于 GET/DELETE
> 4. 可进一步限制上传大小（`content-length-range`），本项目未实现
> 5. secretKey 只在服务端存储，客户端拿不到

**Q: 如果用户拿到了别人的 presigned URL 怎么办？**
> 1. URL 有效期短（5 分钟），攻击窗口有限
> 2. Object Key 包含 UUID，无法被猜测
> 3. 可以在生成 URL 时通过 Post Policy 限制更多条件（本项目简化处理）
> 4. 真正敏感的系统应在应用层再加一层权限校验

**Q: 为什么用 MinIO 而不是直接用阿里云 OSS / AWS S3？**
> MinIO 完全兼容 S3 API，可以在本地开发环境运行，也能部署到生产。它的优势：
> 1. 自托管，数据不经过第三方
> 2. 开发环境与生产环境一致（不会出现"开发用 MinIO、生产用 OSS"导致的 SDK 差异 bug）
> 3. 性能极好（Go 编写，单机可达 10GB/s 吞吐）
> 4. 如果未来需要迁移到 S3/OSS，只需改 endpoint 和凭证，代码零改动

### 4.2 进阶讨论

**Q: 大文件上传怎么处理？**
> 预签名 URL 支持分片上传（Multipart Upload）。流程：
> 1. 服务端生成 `initiate-multipart-upload` 的预签名 URL
> 2. 客户端分片上传，每片获取一个预签名 URL
> 3. 全部上传完成后，服务端生成 `complete-multipart-upload` URL
> 4. 本项目做的是简单上传（单次 PUT），大文件场景需要扩展

**Q: 上传完成后如何通知服务端？**
> 两种方案：
> 1. **回调模式**：MinIO 支持 Bucket Notification，上传完成后回调服务端 webhook
> 2. **客户端通知**（本项目接近）：客户端上传成功后调 `/api/note/publish`，关联 objectKey 到业务数据
> 3. 一般推荐回调模式更可靠（不依赖客户端行为）

---

## 5. 关键代码位置

| 文件 | 作用 |
|------|------|
| `common/UploadRpcService.java` | Dubbo RPC 接口契约 |
| `common/PresignedUrlRequest.java` | 请求 DTO（fileName, contentType） |
| `common/PresignedUrlResponse.java` | 响应 DTO（uploadUrl, objectKey, expiresAt） |
| `gateway/controller/UploadController.java` | 对外 REST 入口 + Sentinel 限流 |
| `upload-service/rpc/UploadRpcServiceImpl.java` | 核心实现：生成 pre-signed URL |
| `upload-service/config/MinioConfig.java` | MinIO 客户端 Bean 配置 |
| `upload-service/controller/UploadController.java` | 直连测试入口 |
