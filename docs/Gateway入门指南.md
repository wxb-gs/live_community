# Gateway 入门指南

## 一句话概括

Gateway（网关）是微服务系统的"前台门迎"——所有外部请求先到它这，它负责接收、路由、限流、鉴权、日志，然后转发给后面的微服务，再把结果返回给客户端。客户端以为只有一个服务，其实背后可能有一大群微服务在协作。

---

## 1. Gateway 在项目中到底干了什么

以我们这个 `spring_cloud_test` 项目为例：

```
Client (浏览器 / Postman / App)
  │
  │ "我只知道 gateway:8080 这一个入口"
  │
  ▼
┌──────────────────────────────────────────────────────────────┐
│                    gateway :8080                              │
│                                                              │
│  ┌──────────────────┐  ┌──────────────────┐                  │
│  │ UploadController │  │  NoteController  │                  │
│  │                  │  │                  │                  │
│  │ GET /api/upload/ │  │ POST /api/note/  │                  │
│  │       presigned  │  │          draft   │                  │
│  │                  │  │ GET  /api/note/  │                  │
│  │ @SentinelResource│  │         detail   │                  │
│  │ (限流保护)        │  │ POST /api/note/  │                  │
│  │                  │  │        comment   │                  │
│  └───────┬──────────┘  │ POST /api/note/  │                  │
│          │              │        publish   │                  │
│          │              └───────┬──────────┘                  │
│          │                      │                             │
│          │  @DubboReference     │  @DubboReference            │
│          ▼                      ▼                             │
│     UploadRpcService       NoteRpcService                    │
│     (只是接口代理)           (只是接口代理)                     │
└─────────┼──────────────────────┼─────────────────────────────┘
          │                      │
          │ Dubbo Triple         │ Dubbo Triple
          │ (Nacos发现服务地址)    │ (Nacos发现服务地址)
          ▼                      ▼
┌──────────────────┐   ┌──────────────────┐
│  upload-service  │   │  note-service    │
│     :20880       │   │     :20882       │
└──────────────────┘   └──────────────────┘
```

**步骤拆解：**

1. **请求到达** — 客户端发起 `GET /api/upload/presigned?fileName=test.jpg&contentType=image/jpeg`
2. **Gateway 路由匹配** — Spring MVC 根据路径 `/api/upload/**` 匹配到 `UploadController`
3. **Sentinel 限流** — `@SentinelResource` 检查该资源的 QPS 是否超限，超了返回 429
4. **参数校验** — Controller 提取请求参数，构造 Dubbo 请求 DTO
5. **Dubbo 调用** — `@DubboReference` 自动完成服务发现 → 负载均衡 → 远程调用
6. **结果返回** — 把 Dubbo 返回值包装成统一格式 `Result<T>` 返回客户端

**关键认知：gateway 是一个"译电员"——客户端说 HTTP，后端服务说 Dubbo，gateway 在两者之间翻译，让客户端不用了解内部调用细节。**

---

## 2. API 网关的本质

先搞清楚"网关"这个词在不同语境下的含义：

```
广义：API 网关（设计模式）
  任何系统对外的统一入口都是网关
  可以是 Nginx、Kong、Spring Cloud Gateway、Zuul、甚至手写的 Spring Boot 应用

狭义：Spring Cloud Gateway（框架）
  Spring 体系的响应式网关框架，基于 WebFlux + Netty
  本项目没有用这个，用的是 Spring Boot Web + Dubbo 自建网关

本项目用的是广义网关 —— 手写 Spring Boot @RestController 做入口层
```

### 2.1 网关的核心职责

```
                     ┌───────────┐
                     │  Gateway  │
                     └─────┬─────┘
          ┌────────────────┼────────────────┐
          │                │                │
    ┌─────▼─────┐   ┌─────▼─────┐   ┌─────▼─────┐
    │  路由转发   │   │  安全防护   │   │  协议转换   │
    │           │   │           │   │           │
    │ /api/note/│   │ 限流 鉴权   │   │ HTTP→Dubbo│
    │ → note-   │   │ 日志 CORS  │   │ REST→RPC  │
    │  service  │   │ 请求过滤    │   │           │
    └───────────┘   └───────────┘   └───────────┘
```

**本项目 gateway 实际做了：**

| 职责 | 本项目实现 | 代码位置 |
|------|-----------|---------|
| **请求路由** | Spring MVC `@RequestMapping` 匹配路径到 Controller | `UploadController`, `NoteController` |
| **协议转换** | Controller 接收 HTTP → 通过 Dubbo 调用后端 RPC | `@DubboReference` 注入 |
| **限流保护** | `@SentinelResource` 注解拦截 | `UploadController.presignedUrl()` |
| **统一响应** | `Result<T>` 包装所有返回值 | `common` 模块 |
| **跨域(CORS)** | Spring Boot 内置 CORS 配置 | 可在 `GatewayApplication` 中配置 |

### 2.2 本项目为什么不直接用后端服务的 HTTP 端口

```
方案 A（不推荐）：客户端直接调各个服务
  Client → upload-service:8081  (获取上传 URL)
  Client → note-service:8083    (创建笔记)
  Client → leaf-service:8082    (生成 ID —— 这太荒谬了)

  问题：
  - 客户端要知道所有服务的地址和端口
  - 每个服务都要做限流/鉴权/日志（重复）
  - 暴露内部架构，安全性差
  - 客户端要处理多种协议

方案 B（本项目）：统一网关入口
  Client → gateway:8080 → 路由到对应服务
  Client 只需要知道 gateway:8080 一个地址

  好处：
  - 客户端视角只有"一个后端"
  - 限流/鉴权/日志集中在网关层做一次
  - 内部服务可以随意扩缩容、换端口、改协议，客户端无感知
```

---

## 3. 主流网关技术对比

### 3.1 Spring Cloud Gateway vs Zuul vs Nginx vs Kong

| 特性 | **Spring Cloud Gateway** | **Zuul 1.x** | **Zuul 2.x** | **Nginx** | **Kong** |
|------|----------------------|----------|----------|-------|------|
| **类型** | Java 网关框架 | Java 网关框架 | Java 网关框架 | 通用反向代理 | API 网关平台 |
| **编程模型** | 响应式（WebFlux + Netty） | 阻塞式（Servlet） | 异步（Netty） | C 语言配置 | Nginx/OpenResty + Lua 插件 |
| **性能** | 高（Reactor 模型） | 低（每连接一线程） | 高 | 极高（C 语言） | 高 |
| **Spring 集成** | 一等公民（Spring Cloud） | 一等公民（Spring Cloud Netflix） | 一般 | 无（需搭配） | 插件生态 |
| **动态路由** | 支持（基于服务发现） | 需 Ribbon | 需 Ribbon | 配置文件 + reload | Admin API 动态下发 |
| **限流** | 内置（令牌桶） | 无（需 Sentinel） | 无 | 第三方（limit_req_zone） | 内置插件 |
| **鉴权** | 内置 Filter | 内置 Filter | 内置 Filter | 需 Lua/OpenResty | 内置插件（OAuth2/JWT） |
| **Web 控制台** | 无 | 无 | 无 | 无 | ✅ Kong Manager |
| **学习成本** | 中 | 低 | 中 | 低（基础），高（深度） | 中高 |
| **适合场景** | Spring Cloud 微服务 | 老 Spring Cloud Netflix 项目 | 需要异步的 Netflix 栈 | 静态资源/负载均衡 | 多团队共享网关平台 |

### 3.2 Spring Cloud Gateway 核心概念

如果本项目用 Spring Cloud Gateway（而不是手写 Controller），代码会是这样：

```yaml
# application.yml — 声明式路由
spring:
  cloud:
    gateway:
      routes:
        - id: upload-route
          uri: lb://upload-service           # lb:// 表示用负载均衡访问
          predicates:
            - Path=/api/upload/**             # 路径匹配 → 触发该路由
          filters:
            - StripPrefix=0                   # 路径前缀处理
            - name: RequestRateLimiter        # 限流过滤器
              args:
                redis-rate-limiter.replenishRate: 50
                redis-rate-limiter.burstCapacity: 100

        - id: note-route
          uri: lb://note-service
          predicates:
            - Path=/api/note/**
```

```
请求 → Predicate（匹配条件）→ Filter 链（加工请求）→ 转发到 uri

Predicate（断言）：  "这个请求该走这条路吗？"
  - Path=/api/upload/**     路径匹配
  - Header=X-Version,v2     请求头匹配
  - Method=GET               请求方法匹配
  - Query=key,value         查询参数匹配
  - After=2025-01-01...     时间匹配

Filter（过滤器）：  "转发前/返回后需要做什么加工？"
  - AddRequestHeader        加请求头
  - RequestRateLimiter      限流
  - Retry                   重试
  - CircuitBreaker          熔断
```

**Spring Cloud Gateway 的三要素：Route = Predicate（匹配什么） + Filter（怎么加工） + URI（转发到哪）**

### 3.3 本项目为何没用 Spring Cloud Gateway

本项目之前确实用过 Spring Cloud Gateway，后来改造为 Dubbo 时替换了。原因：

```
Spring Cloud Gateway 的局限：
  - 基于 WebFlux，和 Dubbo（基于 Servlet/Netty）的线程模型不兼容
  - 路由到 Dubbo 服务需要额外的适配层
  - 对于本项目这种"网关直调 Dubbo RPC"的模式，不如手写 Controller 直接

本项目方案的优势：
  - 能直接用 @DubboReference 注入代理，代码最简洁
  - 一个进程内完成 HTTP→Dubbo 转换，没有额外网络跳转
  - 用 Spring Boot Web（Servlet 容器），和 Dubbo 的线程模型天然兼容

代价：
  - 每加一个对外接口都要手写 Controller 方法（SCG 可以用配置加路由）
  - 但本项目接口少（4 个 upload/note 接口），手写成本很低
```

---

## 4. 网关的典型面经：BFF 模式

本项目的 gateway 其实像一个**简化版 BFF**（Backend For Frontend）。

### 4.1 什么是 BFF

```
                      ┌──────────┐
                      │  手机 App │
                      └────┬─────┘
                           │
                    ┌──────▼───────┐
                    │  BFF (App)   │  ← 为 App 定制的网关：
                    └──────┬───────┘    返回字段精简、一次请求聚合多个后端
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        user-service  order-service  product-service

                      ┌──────────┐
                      │  Web 前端  │
                      └────┬─────┘
                           │
                    ┌──────▼───────┐
                    │  BFF (Web)   │  ← 为 Web 定制的网关：
                    └──────┬───────┘    返回字段更丰富、不同的权限逻辑
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        user-service  order-service  product-service
```

BFF 的核心思想：**前端需要什么就给什么，不需要前端自己去组合多个后端接口。**

### 4.2 本项目的 BFF 影子

虽然本项目只有一个 gateway，但它已经在做 BFF 的事了：

```java
// NoteController 聚合了多个后端操作：
// - 创建草稿：调 note-service + leaf-service 生成 ID
// - 发布笔记：调 note-service（含 MinIO 预签名）
// - 查询详情：调 note-service（内部 CompletableFuture 并行查笔记+评论）

// 这些聚合逻辑对客户端完全透明，客户端只看到简单的 REST API
```

---

## 5. 面试高频问题

### Q1: Spring Cloud Gateway 和 Zuul 的区别？怎么选？

**答：**

核心区别在于 I/O 模型：

| | Spring Cloud Gateway | Zuul 1.x | Zuul 2.x |
|------|---------------------|----------|----------|
| **I/O 模型** | 非阻塞（Netty + Reactor） | 阻塞（Servlet 每连接一线程） | 非阻塞（Netty） |
| **依赖** | Spring WebFlux | Spring MVC | 独立 Netty Server |
| **维护状态** | 活跃（Spring 官方） | **已停维** | 极少使用 |
| **性能** | 高 | 低（线程数限制吞吐量） | 高（但社区太小） |
| **Spring Boot 3.x** | 完整支持 | 不支持 | 不支持 |

一句话总结：**现在做 Spring Cloud 网关，只用 Spring Cloud Gateway。Zuul 1.x 已经停维，Zuul 2.x 就没真正火过。**

### Q2: 为什么有了 Spring Cloud Gateway，很多公司还在用 Nginx/Kong？

**答：**

这是"什么时候用框架、什么时候用中间件"的问题：

**Nginx 更适合的场景：**
- 静态资源服务
- 四层负载均衡（TCP/UDP 代理）
- 极高的吞吐量要求（C 语言实现，性能天花板高）
- 运维团队对 Java 不熟

**Spring Cloud Gateway 更适合的场景：**
- Spring Cloud 微服务体系内
- 需要和 Nacos/Sentinel 深度集成
- 需要用 Java 写复杂的自定义 Filter 逻辑
- 开发团队是 Java 技术栈

**Kong 更适合的场景：**
- 多团队共享一个网关平台
- 需要 Web 控制台管理路由
- 需要插件市场（几百个现成插件）
- 非 Java 技术栈、不想写代码配网关

**实际很多公司的组合：**
```
Client → Nginx (WAF/SSL终止/静态资源) → Spring Cloud Gateway (业务路由/鉴权/限流) → 微服务
```
前端 Nginx 扛 SSL 和边界防护，后端 Gateway 做业务路由。

### Q3: 网关层的限流和服务层的限流有什么不同？

**答：**

| | 网关层限流 | 服务层限流 |
|------|-----------|----------|
| **位置** | 请求刚进系统，离用户最近 | 请求已经到了具体服务 |
| **粒度** | 粗糙（按路由/接口限） | 精细（按方法/资源限） |
| **目的** | 在大门口挡人，保护所有后方服务 | 保护某一个具体服务不被冲垮 |
| **本项目中** | `@SentinelResource` 在 gateway 的 Controller | 目前没有，可在 Dubbo Provider 上加 |
| **优点** | 及早拒绝，不浪费后端资源 | 可以针对具体业务逻辑差异化限流 |

**最佳实践：两层都做，网关层做粗粒度（总入口 QPS），服务层做细粒度（具体方法 QPS）。**

### Q4: 本项目为什么不用 Spring Cloud Gateway 而用手写 Controller？

**答：**

1. **Dubbo 集成**：本项目网关需要直接注入 `@DubboReference` 调用后端 Dubbo 服务。Spring Cloud Gateway 基于 WebFlux，和 Dubbo 的线程模型不兼容，需要额外的适配层
2. **接口数量少**：只有 4 个对外接口，手写 Controller 的工作量远小于配置 SCG
3. **代码可读性**：手写 Controller 的控制流一目了然（接收参数 → 调 Dubbo → 返回结果），不需要理解路由/过滤器/断言等概念
4. **调试友好**：出问题时断点打在 Controller 里，能看到完整调用栈。SCG 基于 WebFlux 的响应式链路调试困难

**结论：不是 Spring Cloud Gateway 不好，而是对于本项目这个场景（低接口数 + Dubbo 直调），手写更合适。**

### Q5: 网关怎么保证高可用？

**答：**

网关本身是一个微服务，高可用手段和其他服务一样：

```
方案 1：多实例 + 前端负载均衡（Nginx/Keepalived）

          ┌──────────┐
          │  Nginx   │  VIP: 192.168.1.100
          │  / LVS   │
          └────┬─────┘
       ┌───────┼───────┐
       ▼       ▼       ▼
   gateway-1 gateway-2 gateway-3    ← 三实例，Nacos 注册
   :8080     :8080     :8080

方案 2：K8s 部署
  - Deployment replicas: 3
  - Service / Ingress 做负载均衡
  - 自动健康检查 + 自动重启

方案 3：Docker Compose（本项目）
  - 单实例部署
  - 通过 depends_on + condition: service_healthy 保证依赖顺序
  - 不是高可用方案，但开发/测试足够
```

**关键：网关是无状态的（不需要存 session），水平扩展很容易。**

### Q6: 网关和服务网格（Service Mesh）的网关有什么区别？

**答：**

这是两个容易被混淆的"网关"概念：

| | API 网关 | Service Mesh 网关 |
|------|---------|-------------------|
| **代表** | Spring Cloud Gateway, Kong, Nginx | Istio Gateway / Envoy |
| **流量方向** | 南北向（外部 → 内部） | 东西向（服务间）为主，也可管南北向 |
| **管理对象** | HTTP/REST API | 所有 TCP 流量 |
| **配置方式** | 代码 / YAML / Web UI | K8s CRD（YAML + kubectl） |
| **透明性** | 应用感知（路由配置显式写） | 应用无感知（Sidecar 注入） |
| **适合场景** | Spring Cloud 微服务 | K8s + Istio 云原生技术栈 |

本项目的场景是典型的 API 网关，不需要 Service Mesh。

---

## 6. 动手实验

### 6.1 看 gateway 收到了什么请求

```bash
# 发送请求
curl -v "http://localhost:8080/api/upload/presigned?fileName=test.jpg&contentType=image/jpeg"

# 看 gateway 日志
docker compose logs -f gateway
```

### 6.2 追踪一次完整链路

```
1. 请求进入 gateway
   → gateway 日志: "Handling request GET /api/upload/presigned"

2. Spring MVC 路由到 UploadController.presignedUrl()
   → Sentinel 检查 QPS

3. Dubbo 发起远程调用
   → gateway 日志: "Dubbo consumer invoke UploadRpcService.generatePresignedUrl"

4. upload-service 处理
   → upload-service 日志: "Received presigned URL request"

5. 结果沿路返回
   → gateway → HTTP Response → Client
```

### 6.3 模拟 gateway 挂了

```bash
# 停掉 gateway
docker compose stop gateway

# 尝试请求
curl "http://localhost:8080/api/upload/presigned?fileName=test.jpg"
# → Connection refused

# 但后端 Dubbo 服务之间的调用不受影响
#（不过本项目后端间没有直接调用，只有 note→leaf 这一个）
```

**这验证了：gateway 只是对外入口，不参与后端服务内部通信。**

---

## 7. 常见误区

| 误区 | 实际 |
|------|------|
| "Gateway = Spring Cloud Gateway" | 不是。Gateway 是设计模式，SCG 只是 Spring 体系的一个实现 |
| "网关就是反向代理" | 网关可以做反向代理，但还做了鉴权/限流/协议转换/日志，比反向代理职责多 |
| "有了网关就不需要 Nginx" | 生产环境常有 Nginx(SSL/静态) → Gateway(业务路由) 两段式 |
| "网关挂了系统就挂了" | 只影响对外入口，后端服务之间的内部调用不受影响 |
| "网关会增加延迟" | 会有额外一跳（~几 ms），但这个代价换来的安全/限流/统一管理完全值得 |
| "网关只能转发 HTTP" | 本项目的 gateway 就做了 HTTP→Dubbo 协议转换，网关可以转发任意协议 |

---

## 延伸阅读

- [Spring Cloud Gateway 官方文档](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
- [BFF 模式详解 (Sam Newman)](https://samnewman.io/patterns/architectural/bff/)
- [API Gateway vs Service Mesh](https://konghq.com/blog/api-gateway-vs-service-mesh)

**记住一句话：Gateway 是微服务的"前门"——客户只敲这一扇门，门里面有多少个房间、房间怎么走、谁在哪个房间，客户不用知道。**
