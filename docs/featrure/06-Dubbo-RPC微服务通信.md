# Dubbo Triple RPC 微服务通信

## 1. 功能概述

### 1.1 什么是 Dubbo？

Apache Dubbo 是阿里巴巴开源的高性能 Java RPC 框架。它提供：
- 透明的远程方法调用（像调本地方法一样调远程服务）
- 服务注册与发现
- 负载均衡、容错、路由
- 多种协议支持（Triple、Dubbo2、REST、gRPC）

### 1.2 本项目中的 Dubbo 架构

```
┌──────────────────────────────────────────────────────┐
│                     Nacos 注册中心                     │
│                 (localhost:8848)                       │
│                                                       │
│  注册的服务:                                           │
│  - upload-service (Triple, :20880)                    │
│  - leaf-service   (Triple, :20881)                    │
│  - note-service   (Triple, :20882)                    │
└──────┬──────────────┬──────────────┬─────────────────┘
       │              │              │
   [注册]          [注册]          [注册]
       │              │              │
  ┌────┴────┐   ┌────┴────┐   ┌────┴──────────────┐
  │ upload  │   │  leaf   │   │    note-service    │
  │ service │   │ service │   │                    │
  │         │   │         │   │ @DubboService      │
  │ @Dubbo  │   │ @Dubbo  │   │   NoteRpcService   │
  │ Service │   │ Service │   │                    │
  │ :20880  │   │ :20881  │   │ @DubboReference    │
  └─────────┘   └─────────┘   │   LeafRpcService ──→ leaf-service
                              └────────────────────┘
       ↑              ↑              ↑
       │              │              │
       │   [Dubbo Triple 协议调用]   │
       │              │              │
  ┌────┴──────────────┴──────────────┴────┐
  │              gateway                   │
  │                                        │
  │  @DubboReference UploadRpcService     │
  │  @DubboReference NoteRpcService       │
  │  (纯消费者，不暴露 Dubbo 服务)         │
  └────────────────────────────────────────┘
```

### 1.3 为什么选 Dubbo 而不是 Spring Cloud 原生方案？

| 维度 | Dubbo Triple | OpenFeign + Ribbon | gRPC |
|------|-------------|-------------------|------|
| 协议 | Triple（基于 HTTP/2） | HTTP/1.1 | HTTP/2 |
| 序列化 | Protobuf / Hessian2 / Java | JSON / XML | Protobuf |
| 性能 | **高**（二进制 + 长连接） | 中等（文本 + 短连接池） | **最高**（二进制 + 多路复用） |
| 服务治理 | 丰富（负载均衡、容错、路由） | 依赖 Spring Cloud | 需额外组件 |
| 跨语言 | 支持（Triple 兼容 gRPC） | 仅 Java | **原生支持** |
| 学习成本 | 中等 | 低（Spring 风格） | 高（.proto 定义） |
| Spring 集成 | Spring Cloud Alibaba | 原生集成 | 需额外集成 |

**选 Dubbo 的原因**：
1. **性能**：Triple 协议基于 HTTP/2，支持多路复用和二进制传输，比 HTTP/1.1 JSON 快很多
2. **服务治理**：内置负载均衡、容错、路由，比 Feign 更灵活
3. **跨语言兼容**：Triple 兼容 gRPC 协议，未来可以接入其他语言的服务
4. **生态**：与 Nacos/Sentinel 同为 Alibaba 出品，集成度最高

---

## 2. 详细实现分析

### 2.1 服务提供者（Provider）配置

```yaml
# upload-service/application.yml
dubbo:
  application:
    name: upload-service          # 服务名（在注册中心的标识）
  registry:
    address: nacos://localhost:8848  # 注册中心地址
  protocol:
    name: tri                      # 使用 Triple 协议
    port: 20880                    # Dubbo 端口
```

```java
// UploadRpcServiceImpl.java
@DubboService                       // 标记为 Dubbo 服务提供者
public class UploadRpcServiceImpl implements UploadRpcService {
    @Override
    public PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request) {
        // 实现逻辑
    }
}
```

**`@DubboService` 替代了旧版的 `@Service`（Dubbo 的）**：在 Dubbo 3.x 中，`@DubboService` 明确替代了原来的 `@Service`（避免与 Spring 的 `@Service` 混淆）。

### 2.2 服务消费者（Consumer）配置

```yaml
# gateway/application.yml
dubbo:
  application:
    name: gateway
  registry:
    address: nacos://localhost:8848
  # 注意：gateway 没有 protocol 配置，因为不暴露 Dubbo 服务
```

```java
// gateway/UploadController.java
@DubboReference(check = false)       // 引用远程服务
private UploadRpcService uploadRpcService;
```

**`check = false` 的含义**：
- `check = true`（默认）：启动时检查 Dubbo 服务是否可用，不可用则启动失败
- `check = false`：启动时不检查，延迟到第一次调用时连接
- 本项目用 `false` 是因为：gateway 可能在 upload-service 之前启动（容器编排的依赖控制不是 100% 可靠），不能用启动检查阻塞

### 2.3 note-service 的双重角色

```java
// NoteRpcServiceImpl.java - 作为 Provider
@DubboService
public class NoteRpcServiceImpl implements NoteRpcService { ... }

// NoteService.java - 作为 Consumer
@DubboReference(check = false)
private LeafRpcService leafRpcService;
```

note-service 是唯一一个**既是 Provider 又是 Consumer** 的服务：
- 对外提供笔记 CRUD 的 RPC 接口（被 gateway 调用）
- 对内消费 leaf-service 的 ID 生成接口（获取 noteId 和 commentId）

```yaml
dubbo:
  application:
    name: note-service
  registry:
    address: nacos://localhost:8848
  protocol:                    # Provider 配置
    name: tri
    port: 20882
  consumer:                    # Consumer 配置（可选，使用默认即可）
    check: false
```

### 2.4 Triple 协议

Triple 是 Dubbo 3.0 引入的新协议，基于 HTTP/2：

```
┌──────────────────────────────────────────┐
│              HTTP/2 帧                    │
├──────────────────────────────────────────┤
│  支持 Stream 多路复用                      │
│  二进制帧传输                              │
│  头部压缩 (HPACK)                         │
│  服务端推送                                │
├──────────────────────────────────────────┤
│  Triple 协议层                             │
├──────────────────────────────────────────┤
│  兼容 gRPC 协议（可跨语言调用）             │
│  支持 Protobuf / Hessian2 序列化           │
│  支持 Unary + Server/Client/Bi Stream     │
└──────────────────────────────────────────┘
```

**相比 Dubbo2 协议的优势**：
1. 穿透网关更友好（基于 HTTP/2，Nginx/Envoy 原生支持）
2. 跨语言互通（Triple = gRPC compatible）
3. 流式调用支持（Server Stream、Client Stream、Bidirectional Stream）
4. 更好的移动端支持

### 2.5 接口契约设计（common 模块）

所有 RPC 接口和 DTO 都在 `common` 模块中定义：

```
common/
├── UploadRpcService.java      ← 上传 RPC 接口
├── LeafRpcService.java        ← ID 生成 RPC 接口
├── NoteRpcService.java        ← 笔记 RPC 接口
├── PresignedUrlRequest.java   ← 请求 DTO
├── PresignedUrlResponse.java  ← 响应 DTO
├── IdResponse.java
├── CreateDraftRequest.java
├── CreateDraftResponse.java
├── ...
└── Result.java                ← 通用响应包装（仅 gateway REST 使用）
```

**为什么接口和 DTO 要放在 common 模块？**
- **接口共享**：Provider 实现接口，Consumer 只依赖接口（无需知道实现细节）
- **DTO 共享**：序列化/反序列化需要完全相同的类定义
- **避免重复**：否则每个模块都要复制一份 DTO 类
- **版本一致性**：common 模块的版本统一，避免 Provider 和 Consumer 使用不同版本的 DTO

---

## 3. 实现难点

### 3.1 Dubbo 序列化与 Java 序列化

```java
public class PresignedUrlRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    // ...
}
```

所有 DTO 都实现了 `java.io.Serializable`。Dubbo Triple 默认使用 Hessian2 序列化，它比 Java 原生序列化：
- 更快（跨语言设计的二进制协议）
- 更小（序列化后体积通常是 Java 的 1/3~1/5）
- 但要求类有无参构造函数和 getter/setter

**为什么还要 `serialVersionUID`？** 虽然 Hessian2 不依赖 `serialVersionUID`，但保留它是为了兼容性降级——如果未来切回 Java 序列化，不会因为缺失 UID 而报错。

### 3.2 Nacos 注册中心的健康检查

服务注册到 Nacos 后，Nacos 会定期检测服务是否存活。但有两个时机问题：

1. **服务启动到注册的延迟**：服务启动后，Spring 容器完全初始化后才会注册到 Nacos，这段时间其他服务调用会失败
2. **服务下线到注销的延迟**：服务异常退出时，Nacos 需要等到心跳超时（默认 15 秒）才会摘除该实例

**缓解措施**：
- `check = false` 解决了启动时序问题
- Dubbo 客户端有重试机制（默认 2 次）
- 配合 Nacos 的健康检查端点

### 3.3 超时配置

当前项目没有显式配置 Dubbo 超时时间，使用 Dubbo 默认值（1 秒）。这对于 ID 生成和上传 URL 生成来说可能偏短。

**推荐配置**：

```yaml
dubbo:
  provider:
    timeout: 3000          # 3 秒超时
  consumer:
    timeout: 5000          # 5 秒超时（consumer 可以比 provider 稍长）
```

超时时间的设置需要考虑：
- 号段模式的 DB 查询（step 加载）通常 < 100ms
- 预签名 URL 生成（网络调用 MinIO）通常 < 200ms
- 但要考虑冷启动、GC 暂停、网络抖动等极端情况

### 3.4 本地开发 vs Docker 部署的网络差异

```yaml
# 本地开发
dubbo.registry.address: nacos://localhost:8848

# Docker 部署
DUBBO_REGISTRY_ADDRESS: nacos://nacos:8848
```

**关键坑点**：Dubbo 注册到 Nacos 时使用的是**服务所在容器的 hostname/IP**。如果容器间网络不可达（如使用 bridge 网络），消费者可能拿到了无法连接的地址。解决方式：
1. 使用 Docker 自定义网络（`live-community` bridge）确保容器间互通
2. 配置 `DUBBO_PROTOCOL_HOST` 为可路由的地址
3. 本项目使用 Docker Compose 的 `networks.live-community` 来保证

---

## 4. RPC 框架对比深度解析

### 4.1 Dubbo Triple vs OpenFeign

```
Dubbo Triple:
  Client ──HTTP/2 长连接──→ Server
  每次调用复用同一个 TCP 连接
  二进制序列化（Hessian2）
  约 10-50KB overhead

OpenFeign:
  Client ──HTTP/1.1 短连接池──→ Server
  每次调用从连接池取出连接，用完放回
  JSON 文本序列化（Jackson）
  约 100-500KB overhead
```

性能差别来自两个层面：
1. **连接层**：长连接 vs 连接池——长连接免去了每次的 TCP 握手开销
2. **序列化层**：二进制 vs 文本——二进制编码后体积更小，传输更快

### 4.2 Dubbo Triple vs gRPC

Triple 在设计上兼容 gRPC：
- **相同**：都基于 HTTP/2，都支持 Protobuf，都支持流式调用
- **不同**：Triple 额外支持 Hessian2/JSON 序列化，gRPC 只能用 Protobuf
- **不同**：Triple 通过 Nacos 注册发现，gRPC 需要额外的服务发现组件

**为什么不用 gRPC 原生？**
1. Spring Cloud Alibaba 对 Dubbo 有原生支持
2. 不需要定义 `.proto` 文件和编译步骤（共享 Java 接口更简单）
3. Nacos 注册发现与 Dubbo 深度集成

---

## 5. 面试准备

### 5.1 高频问题

**Q: 为什么用 RPC 而不是 RESTful HTTP？**
> 1. **性能**：RPC 用长连接 + 二进制序列化，比 HTTP 短连接 + JSON 快 5~10 倍
> 2. **服务治理**：Dubbo 内置负载均衡、容错、路由，HTTP 需要额外的组件
> 3. **接口契约**：Java 接口即为契约，编译期类型检查，不需要手动维护 REST 规范
> 4. **微服务内部通信用 RPC，对外 API 用 REST**：这是经典的架构分层——内部高性能，外部标准化

**Q: `@DubboReference` 和 `@Autowired` 有什么区别？**
> - `@DubboReference`：注入远程 Dubbo 服务代理（RPC 调用，走网络）
> - `@Autowired`：注入本地 Spring Bean（直接调用，走内存）
> - Dubbo 会自动为 `@DubboReference` 创建代理对象，代理对象内部完成序列化、网络传输、反序列化
> - 对开发者来说，调用方式完全一样——这是 RPC 透明化的核心价值

**Q: 如果 Dubbo 调用失败，怎么处理？**
> Dubbo 提供了多种容错策略：
> 1. **Failover（默认）**：失败自动切换（重试），通常配置 `retries=2`
> 2. **Failfast**：快速失败，立即抛异常，适合非幂等操作
> 3. **Failsafe**：失败安全，吞掉异常，适合日志等非关键操作
> 4. **Failback**：失败后台恢复，定时重发，适合消息通知
> 5. **Forking**：并行调用多个 provider，取第一个成功的结果
> 6. **Broadcast**：广播调用所有 provider
> 本项目使用默认的 Failover，适合 ID 生成和上传 URL 生成（幂等操作）。

**Q: Nacos 挂了会影响已经建立的 Dubbo 连接吗？**
> 不会立即影响。Dubbo 客户端会缓存服务列表到本地：
> 1. 注册中心可用时，定时从 Nacos 拉取最新服务列表
> 2. 注册中心不可用时，使用本地缓存的列表继续调用
> 3. 已建立的连接不受影响，只有新的服务发现会失败
> 4. Nacos 恢复后，客户端重新连接并同步最新列表

### 5.2 进阶讨论

**Q: 为什么不直接把 gateway 去掉，让客户端直接调 upload-service？**
> 1. **安全**：gateway 是统一入口，可以做认证、授权、限流
> 2. **解耦**：客户端不需要知道 Dubbo 协议和内部服务地址
> 3. **协议转换**：对外 HTTP REST，对内 Dubbo Triple，网关做协议适配
> 4. **统一管控**：日志、监控、链路追踪都可以在网关层统一处理
> 5. 这是标准的 API Gateway 模式——BFF（Backend For Frontend）

**Q: provider 端抛出的异常能在 consumer 端捕获吗？**
> 可以，但有条件：
> 1. 异常类必须在 common 模块中定义（两边类路径一样）
> 2. 异常必须是 `RuntimeException` 的子类（Dubbo 默认不会包装 RuntimeException）
> 3. 受检异常（Checked Exception）会被包装为 `RuntimeException`
> 4. 本项目的 `RuntimeException` 会原样传到 consumer 端

**Q: 如何实现服务灰度发布？**
> Dubbo 支持通过路由规则实现灰度发布：
> 1. 给服务实例打标签（如 `version=1.0.0`, `group=stable`）
> 2. 消费者指定路由规则：特定 header 的请求路由到特定版本
> 3. 配合 Nacos 的元数据管理，可以动态调整路由规则
> 4. 或者直接用 `dubbo.tag` 配合流量染色

---

## 6. 关键代码位置

| 文件 | 作用 |
|------|------|
| `common/UploadRpcService.java` | 上传 RPC 接口契约 |
| `common/LeafRpcService.java` | ID 生成 RPC 接口契约 |
| `common/NoteRpcService.java` | 笔记 RPC 接口契约 |
| `upload-service/rpc/UploadRpcServiceImpl.java` | 上传服务提供者实现 |
| `leaf-service/rpc/LeafRpcServiceImpl.java` | ID 服务提供者实现 |
| `note-service/rpc/NoteRpcServiceImpl.java` | 笔记服务提供者实现 |
| `gateway/controller/UploadController.java` | 上传接口 Dubbo 消费者 |
| `gateway/controller/NoteController.java` | 笔记接口 Dubbo 消费者 |
| `note-service/service/NoteService.java` | ID 生成 Dubbo 消费者（双重角色） |
| `gateway/GatewayApplication.java` | `@EnableDubbo` 启动注解 |
