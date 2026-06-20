# Dubbo 入门指南

## 一句话概括

Apache Dubbo 是阿里巴巴开源的一款高性能 Java RPC 框架。你可以把它理解为微服务之间的"专线电话"——服务 A 调用服务 B 的方法就像调用本地方法一样，Dubbo 帮你搞定网络传输、序列化、负载均衡、服务发现等所有底层细节。

---

## 1. Dubbo 在项目中到底干了什么

以我们这个 `spring_cloud_test` 项目为例：

```
Client (浏览器/Postman)
  │  HTTP GET /api/upload/presigned?fileName=test.jpg&contentType=image/jpeg
  ▼
┌─────────────────────────────────────────────────────────────┐
│ gateway :8080                                               │
│   UploadController.presignedUrl()                           │
│     │                                                       │
│     │  uploadRpcService.generatePresignedUrl(req)           │
│     │  ↑ 看着像本地方法调用，其实是 Dubbo 远程调用            │
│     └────────────────────────┐                              │
└──────────────────────────────┼──────────────────────────────┘
                               │
         Dubbo Triple 协议      │  二进制、长连接、多路复用
         (Nacos 告知地址后       │
          直连，不经过网关)      │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ upload-service :20880                                       │
│   UploadRpcServiceImpl.generatePresignedUrl(req)            │
│     │                                                       │
│     └── MinioClient.getPresignedObjectUrl() → MinIO :9000   │
│                                                             │
│   返回 PresignedUrlResponse {uploadUrl, objectKey, ...}     │
└─────────────────────────────────────────────────────────────┘
```

**步骤拆解：**

1. **服务启动时** — `upload-service` 向 Nacos 注册："我叫 upload-service，IP 是 172.21.0.6，端口 20880，提供了 `UploadRpcService` 接口"
2. **调用发起时** — `gateway` 的 `UploadController` 调用 `uploadRpcService.generatePresignedUrl(req)`，这是一个 `@DubboReference` 注入的代理对象
3. **Dubbo 代理拦截** — 代理对象把方法调用转换成 Dubbo Triple 协议的二进制请求，通过 Nacos 获取 `upload-service` 的地址列表，选一个发过去
4. **服务端处理** — `upload-service` 的 `UploadRpcServiceImpl`（`@DubboService`）收到请求，执行真正的业务逻辑，返回结果
5. **结果返回** — 响应沿原路返回，gateway 拿到 `PresignedUrlResponse` 对象，就像调了一个本地方法

**关键认知：Dubbo 是"让远程调用像本地调用一样简单"的框架。它不负责"找地址"（那是 Nacos 的活），不负责"转发流量"（那是 Gateway 的活），它只负责"把 A 服务的调用请求高效地传给 B 服务"。**

---

## 2. Dubbo 的核心概念

### 2.1 角色模型

```
┌──────────────┐         ┌──────────────┐
│   Provider   │         │   Consumer   │
│  (提供者)     │         │  (消费者)     │
│              │         │              │
│ @DubboService│◄────────│@DubboReference│
│ 实现接口      │   RPC   │ 注入接口      │
└──────┬───────┘         └──────┬───────┘
       │ 注册                    │ 订阅
       ▼                        ▼
┌─────────────────────────────────────┐
│            Registry (Nacos)         │
│           "谁提供了什么服务"          │
└─────────────────────────────────────┘
```

| 角色 | 本项目中的对应 | 注解 |
|------|--------------|------|
| **Provider** (提供者) | upload-service, leaf-service, note-service | `@DubboService` |
| **Consumer** (消费者) | gateway, note-service（调用了 leaf-service） | `@DubboReference` |
| **Registry** (注册中心) | Nacos :8848 | `dubbo.registry.address=nacos://...` |

### 2.2 对应配置（本项目）

**Provider 端** (upload-service 的 `application.yml`)：

```yaml
dubbo:
  application:
    name: upload-service          # 应用名
  registry:
    address: nacos://localhost:8848   # 注册中心地址
  protocol:
    name: tri                     # 使用 Triple 协议
    port: 20880                   # 监听端口
```

**Consumer 端** (gateway 的 `application.yml`)：

```yaml
dubbo:
  application:
    name: gateway
  registry:
    address: nacos://localhost:8848
  # 消费者不需要 protocol.port，因为不暴露服务
```

**Java 代码：**

```java
// === Provider 端 (note-service) ===
@DubboService                                    // 把这个实现注册到 Dubbo
public class NoteRpcServiceImpl implements NoteRpcService {
    @Override
    public CreateDraftResponse createDraft(CreateDraftRequest request) {
        return noteService.createDraft(request);  // 真正的业务逻辑
    }
}

// === Consumer 端 (gateway) ===
@RestController
public class NoteController {
    @DubboReference(check = false)               // 从 Dubbo 获取远程代理
    private NoteRpcService noteRpcService;        // 接口类型，不是实现类

    @PostMapping("/draft")
    public Result<CreateDraftResponse> createDraft(@RequestBody CreateDraftRequest req) {
        CreateDraftResponse resp = noteRpcService.createDraft(req);  // 像本地调用一样
        return Result.ok(resp);
    }
}
```

`check = false` 的意思是启动时不检查 Provider 是否已就绪，避免启动顺序依赖。

---

## 3. Dubbo 的核心能力

### 3.1 透明远程调用

Consumer 只依赖接口（`NoteRpcService`），完全不感知网络细节。Dubbo 在运行时生成动态代理，把方法调用序列化成二进制数据发出去，再把响应反序列化成 Java 对象。

### 3.2 协议支持

| 协议 | 特点 | 适用场景 |
|------|------|---------|
| **triple** (本项目使用) | 基于 HTTP/HTTP2，兼容 gRPC，Protobuf 序列化 | 跨语言、穿透网关、云原生 |
| **dubbo** | 自研 TCP 协议，Hessian2 序列化 | 纯 Java 微服务，极致性能 |
| **rest** | HTTP + JSON | 对外 API、非 Java 客户端直接调用 |

Triple 是 Dubbo 3.x 的主推协议，解决了老 dubbo 协议的两个痛点：穿透网关和跨语言。

### 3.3 集群容错

```
消费者调用远程服务时，Dubbo 内置了这些策略：

  Failover (默认)    ── 失败后重试其他节点
  Failfast           ── 失败立即报错，不重试
  Failsafe           ── 失败直接忽略（打日志）
  Failback           ── 失败后定时重发
  Forking            ── 并行调用多个节点，取第一个返回
  Broadcast          ── 广播给所有节点
```

### 3.4 负载均衡

```
  Random (默认)      ── 随机，可设权重
  RoundRobin         ── 轮询
  LeastActive        ── 最少活跃调用数
  ConsistentHash     ── 一致性哈希（同参数请求到同节点）
```

### 3.5 服务治理

Dubbo Admin 提供了可视化管理台，可以看到所有服务、调用关系、动态调整权重和路由规则。

---

## 4. 对比：Dubbo vs OpenFeign vs gRPC

这是面试高频题。三者都是 RPC/远程调用方案，但设计理念和适用场景差异很大。

### 4.1 定位差异

| 维度 | **Dubbo** | **OpenFeign** | **gRPC** |
|------|-----------|---------------|----------|
| **本质** | RPC 框架（完整的服务治理平台） | HTTP 客户端（声明式 REST 调用） | RPC 框架（跨语言、高性能） |
| **协议** | 自研 TCP / Triple(HTTP2) / REST | HTTP/1.1 | HTTP/2 + Protobuf |
| **序列化** | Hessian2 / Protobuf / JSON 可切换 | JSON（Spring 默认） | Protobuf（唯一选择） |
| **通信模型** | TCP 长连接 + 多路复用（triple 协议） | HTTP 短连接（每次请求建连） | HTTP/2 长连接 + 多路复用 |
| **服务发现** | 内置（Nacos/ZK/Redis 等） | 依赖 Spring Cloud（Ribbon/LoadBalancer） | 需要额外组件（DNS/K8s/Envoy） |
| **负载均衡** | 内置多种策略 | 依赖 Spring Cloud LoadBalancer | 通常由 K8s Service/Envoy 处理 |
| **跨语言** | Triple 协议支持（Java/Go/Node.js 等） | 仅 Java（Spring 生态） | 多语言一等公民 |
| **IDL** | Java Interface（原生） + Protobuf（可选） | 无 IDL，靠 Java 接口注解 | `.proto` 文件为唯一真理源 |
| **学习成本** | 中 | 低 | 中高（Protobuf 上手有成本） |
| **生态定位** | Spring Cloud Alibaba 核心 | Spring Cloud Netflix 核心 | CNCF 毕业项目、K8s 生态 |

### 4.2 技术选型决策树

```
你的技术栈是 Spring Cloud Alibaba + Dubbo？
  → 用 Dubbo，不用纠结。Nacos 注册 + Dubbo Triple 是本项目的标准组合。

你要的是 HTTP REST 调用、需要浏览器直接访问？
  → 用 OpenFeign。Dubbo 也可以对外暴露 REST，但 OpenFeign 的 HTTP+JSON 模式更直接。

你需要跨语言（Go 服务调 Java、Python 调 Go）？
  → 用 gRPC 或 Dubbo Triple。Protobuf 作为 IDL 能生成所有语言的代码。

你追求极致性能、十万级 QPS？
  → Dubbo 老协议（TCP + Hessian2）或 gRPC 都行。OpenFeign 的 HTTP/1.1 短连接不适合。

你在 Spring 全家桶已有复杂服务治理需求（限流、降级、灰度）？
  → Dubbo。Sentinel + Dubbo 服务治理是阿里系二件套，OpenFeign + Sentinel 也能做但链路更长。
```

### 4.3 性能对比（定性）

```
吞吐量：  Dubbo(TCP) ≈ gRPC > Dubbo(Triple) >> OpenFeign
延迟：    Dubbo(TCP) ≈ gRPC < Dubbo(Triple) << OpenFeign
跨语言：  gRPC > Dubbo(Triple) >> Dubbo(TCP) ≈ OpenFeign
易用性：  OpenFeign > Dubbo >> gRPC
```

说明：Dubbo 老协议（TCP 直连）和 gRPC 性能接近。OpenFeign 基于 HTTP/1.1 短连接，每次请求都有 TCP 三次握手开销，在内部高并发调用场景下差距显著。

### 4.4 实际代码对比

以本项目 note-service 的"创建草稿"为例，看三种方案的写法差异：

**Dubbo：**

```java
// common 模块：定义接口（契约）
public interface NoteRpcService {
    CreateDraftResponse createDraft(CreateDraftRequest request);
}

// note-service（Provider）：实现接口
@DubboService
public class NoteRpcServiceImpl implements NoteRpcService {
    public CreateDraftResponse createDraft(CreateDraftRequest request) {
        return noteService.createDraft(request);
    }
}

// gateway（Consumer）：引用接口
@DubboReference
private NoteRpcService noteRpcService;    // 注入即可用

public Result<?> createDraft(@RequestBody CreateDraftRequest req) {
    return Result.ok(noteRpcService.createDraft(req));  // 像本地方法
}
```

**OpenFeign：**

```java
// 定义一个 Feign 接口
@FeignClient(name = "note-service", url = "http://note-service:8083")
public interface NoteFeignClient {
    @PostMapping("/api/note/draft")
    Result<CreateDraftResponse> createDraft(@RequestBody CreateDraftRequest request);
}

// Consumer 端注入 Feign 接口
@Autowired
private NoteFeignClient noteFeignClient;

public Result<?> createDraft(@RequestBody CreateDraftRequest req) {
    return noteFeignClient.createDraft(req);  // HTTP POST 到 note-service:8083/api/note/draft
}
```

**gRPC：**

```protobuf
// 1. 先写 .proto 文件
service NoteService {
  rpc CreateDraft (CreateDraftRequest) returns (CreateDraftResponse);
}
message CreateDraftRequest {
  int64 user_id = 1;
  string title = 2;
  string content = 3;
}
message CreateDraftResponse {
  int64 note_id = 1;
  string status = 2;
}
```

```bash
# 2. 用 protoc 编译器生成 Java 代码（每次改 .proto 都要重新生成）
protoc --java_out=. note.proto
```

```java
// 3. 使用生成的代码（无法像 Dubbo 那样直接用业务接口）
stub.createDraft(request, StreamObserver<CreateDraftResponse> responseObserver);
```

**核心差异：**

| | Dubbo | OpenFeign | gRPC |
|------|-------|-----------|------|
| **编程模型** | 面向接口，极简 | 注解声明式 | 面向 `.proto` 生成代码 |
| **代码侵入** | 低（接口即契约） | 低（注解即契约） | 高（`.proto` 是真理源，生成代码不透明） |
| **接口变更** | 改 Java 接口即可 | 改 Java 接口即可 | 改 `.proto` → 重新生成 → 改实现 |
| **是否需 Controller** | 不需要（直接暴露 RPC） | 需要（必须先有 REST Controller） | 不需要（gRPC Server 直接暴露） |

---

## 5. 面试高频问题

### Q1: Dubbo 的调用流程是怎样的？

**答：**

```
1. Consumer 通过 @DubboReference 拿到一个动态代理对象
2. 调用代理对象的方法时，Dubbo 拦截调用
3. 从 Registry（Nacos）获取 Provider 地址列表
4. 根据负载均衡策略选出一个 Provider 节点
5. 将方法名、参数、版本号等打包成 Dubbo 协议的二进制数据
6. 通过 Netty 长连接发送给 Provider
7. Provider 反序列化请求，找到对应的 @DubboService 实现类
8. 反射调用实现方法，拿到返回值
9. 将返回值序列化后，沿原连接返回 Consumer
10. Consumer 反序列化得到结果对象，返回给调用方
```

一句话：**代理拦截 → 注册发现 → 负载均衡 → 序列化 → 网络传输 → 反序列化 → 反射调用 → 原路返回。**

### Q2: Dubbo 和 Spring Cloud (OpenFeign) 怎么选？

**答：**

这不是一道"谁更好"的题，而是看技术栈和需求：

| 选 Dubbo | 选 OpenFeign |
|----------|--------------|
| 技术栈是 Spring Cloud Alibaba | 技术栈是 Spring Cloud Netflix |
| 需要更高性能（TCP 长连接 vs HTTP 短连接） | 需要对外暴露 REST API 给前端/第三方 |
| 需要更丰富的服务治理（权重、路由、降级） | 团队以 Spring Boot 为主，不引入新概念 |
| 不需要跨语言（纯 Java 技术栈） | 接口本身就是 RESTful 风格 |
| 内部微服务间高性能调用 | 能接受 HTTP 开销，追求简单 |

实际上两者可以共存：对外用 Spring Cloud Gateway（HTTP），对内用 Dubbo（RPC）。本项目就是这个模式——gateway 对外提供 REST API，对内通过 Dubbo 调用 upload/leaf/note 等服务。

### Q3: Dubbo 的 Triple 协议和老的 Dubbo 协议有什么区别？

**答：**

老 Dubbo 协议是阿里自研的 TCP 协议，特点是高性能、私有二进制格式。

Triple 协议是 Dubbo 3.x 推出的新协议，基于 HTTP/HTTP2：

| | 老 Dubbo 协议 | Triple 协议 |
|------|-------------|-----------|
| **基础** | 自研 TCP 协议 | HTTP/HTTP2 标准 |
| **序列化** | Hessian2（默认） | Protobuf（默认），兼容 JSON |
| **穿透网关** | 困难（私有协议） | 简单（基于 HTTP，网关直接转发） |
| **跨语言** | 困难 | 容易（兼容 gRPC，其他语言用 gRPC SDK 即可） |
| **多路复用** | 连接级 | 流级（HTTP2 特性） |
| **性能** | 极高 | 略低于老协议，但对绝大多数场景足够 |

**一句话：老协议追求极致性能，Triple 追求通用性和云原生兼容。** 本项目用 Triple，因为 Spring Cloud 体系下需要穿透网关。

### Q4: Dubbo 的注册中心挂了会怎样？

**答：**

这个问题和 Nacos 那题的答案类似，但定位不同：

1. **已有调用不受影响**：Consumer 本地缓存了 Provider 地址列表，注册中心挂了后 Dubbo 用缓存地址继续调用，已有连接正常
2. **新服务发现受影响**：新启动的 Provider 无法被 Consumer 感知，扩缩容失效
3. **Dubbo 有本地缓存文件**：默认在 `~/.dubbo/` 目录下，注册中心恢复后会自动更新

**结论：注册中心是"寻址"依赖，不是"调用"依赖。这点 Dubbo 和 Spring Cloud 的设计一致——降级时尽量保调用。**

### Q5: Dubbo 怎么处理服务调用超时和重试？

**答：**

```yaml
dubbo:
  provider:
    timeout: 3000              # 超时 3 秒
    retries: 2                 # 失败后重试 2 次（默认是 Failover 策略）
  consumer:
    timeout: 5000              # Consumer 端也可设超时
```

注意事项：
- 重试只在 **幂等** 操作中开启（查询类），写操作（创建、更新、删除）建议设置 `retries: 0`
- 超时会传播到调用链下游，避免雪崩（设置合理的超时时间很重要）
- Dubbo 默认超时 1000ms，生产环境通常调整到 3000-5000ms

### Q6: Dubbo 的序列化方式有哪些？怎么选？

**答：**

| 序列化 | 特点 | 推荐场景 |
|--------|------|---------|
| **Hessian2** (老默认) | 二进制、跨语言差、JDK 对象兼容好 | 纯 Java 项目 |
| **Protobuf** (Triple 默认) | 二进制、跨语言强、Schema 演进 | 跨语言、云原生 |
| **Fastjson2** | JSON、可读性好、性能不错 | 调试方便、需要可读性 |
| **Kryo** | 二进制、Java 专用、性能极高 | 纯 Java 追求性能 |
| **JDK** | Java 原生、性能差 | 不推荐生产使用 |

本项目 Triple 协议默认用 Protobuf，因为它兼容 gRPC 生态且跨语言能力强。

### Q7: 你项目中 note-service 既是 Provider 又是 Consumer？

**答：**

对。note-service 的角色：

```
作为 Provider（提供者）：暴露 NoteRpcService 给 gateway 调用
   └── @DubboService
        public class NoteRpcServiceImpl implements NoteRpcService { ... }

作为 Consumer（消费者）：调用 leaf-service 的 LeafRpcService 获取分布式 ID
   └── @DubboReference(check = false)
        private LeafRpcService leafRpcService;
```

这在微服务中很常见——一个服务既为上游提供服务，也依赖下游的其他服务。Dubbo 天然支持这种角色混合，配置上只需要声明 protocol 端口（作为 Provider）同时也可以引用其他服务（作为 Consumer）。

### Q8: Dubbo 和 gRPC 到底什么关系？Dubbo 3.x 在模仿 gRPC 吗？

**答：**

Dubbo 3.x 的 Triple 协议是"兼容 gRPC，但不等于 gRPC"：

- **协议层兼容**：Triple 基于 HTTP2 + Protobuf，一个标准的 gRPC 客户端可以直接调用 Dubbo Triple 服务
- **但 Dubbo 不止是 RPC**：Dubbo 还提供了完整的服务治理（注册、配置、路由、降级、限流、监控），gRPC 本身不提供这些，需要配合其他基础设施
- **编程模型不同**：Dubbo 强调"面向接口编程"，gRPC 强调"面向 .proto 生成代码"
- **生态不同**：Dubbo 深度绑定 Spring Cloud Alibaba 生态，gRPC 绑定 CNCF/K8s 生态

**结论：Dubbo 3.x 在通信层"对齐"了 gRPC 标准，但在服务治理层"超越"了 gRPC 的能力范畴。**

---

## 6. 动手实验

### 6.1 看 Dubbo 的服务注册

启动本项目后，打开 Nacos 控制台 http://localhost:18848/nacos（Docker）或 http://localhost:8848/nacos（本地）。

在"服务管理 → 服务列表"中能看到 Dubbo 注册的服务：

```
服务名                          实例数
gateway                         1
upload-service                  1
leaf-service                    1
note-service                    1
```

点进 `note-service` → 详情，能看到 Dubbo 注册的 RPC 接口：

```
com.example.common.NoteRpcService
  方法: createDraft, publishNote, getNoteDetail, addComment
```

### 6.2 追踪一次完整的 Dubbo 调用

```bash
# 1. 在 gateway 日志中看到 Dubbo 调用
docker compose logs -f gateway | grep -i dubbo

# 2. 发起请求
curl -X POST http://localhost:8080/api/note/draft \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"title":"Hello","content":"Dubbo test"}'

# 3. 查看 note-service 日志，确认 Provider 收到请求
docker compose logs -f note-service | grep -i "draft\|dubbo"
```

### 6.3 观察 Dubbo 直连

```
gateway 调 note-service 时：
  ── 流量不经过 Nacos
  ── 流量不经过 gateway（gateway 是调用方，不是代理）
  ── 直接 TCP 连接 gateway → note-service:20882
```

这意味着：**停掉 Nacos 不影响已有调用，停掉 gateway 也不影响 upload-service 和 leaf-service 之间的调用。**

---

## 7. 常见误区

| 误区 | 实际 |
|------|------|
| "Dubbo 是网关" | 不是。网关负责对外暴露 HTTP，Dubbo 负责内部 RPC 调用。本项目 gateway 用 Dubbo 消费服务，但 gateway 本身不是 Dubbo |
| "Dubbo 和 Spring Cloud 互斥" | 不互斥。Spring Cloud Alibaba 就是两者的桥梁，可以同时使用 |
| "Triple 就是 gRPC" | 不是。Triple 兼容 gRPC 协议，但编程模型和服务治理能力不同 |
| "RPC 就是 REST" | 不是。RPC 面向方法调用，REST 面向资源操作，设计哲学不同 |
| "Dubbo 只能在阿里的注册中心上用" | 不是。Dubbo 支持 Nacos/Zookeeper/Redis/Consul/etcd 等多种注册中心 |
| "用了 Dubbo 就不能调 HTTP 接口" | 不是。Dubbo 3.x 的 Triple 协议基于 HTTP，支持 REST 风格调用 |

---

## 延伸阅读

- [Apache Dubbo 官方文档](https://dubbo.apache.org/zh/docs/)
- [Dubbo 3.x Triple 协议设计](https://dubbo.apache.org/zh/docs/concepts/rpc-protocol/)
- [Dubbo vs gRPC 深度对比](https://dubbo.apache.org/zh/docs/concepts/extensibility/)

**记住一句话：Dubbo 是微服务之间的高性能专线，你写"本地方法调用"，它帮你变成"远程方法调用"，中间的序列化、网络传输、负载均衡、容错恢复全透明。**
