# Nacos 入门指南

## 一句话概括

Nacos（**Na**ming and **Co**nfiguration **S**ervice）是阿里巴巴开源的一个"服务注册发现 + 配置管理"二合一中间件。你可以把它理解为微服务世界的"电话本 + 遥控器"——所有服务启动后向它报到，服务之间通过它互相找到对方；同时你可以通过它实时修改配置，不用重启服务。

---

## 1. Nacos 在项目中到底干了什么

以我们这个 `spring_cloud_test` 项目为例：

```
                    ┌─────────┐
                    │  Nacos  │  :8848
                    └────┬────┘
         注册           注册│           注册
    ┌───────┘        ┌──────┴──────┐        └───────┐
    ▼                ▼              ▼                ▼
 gateway        upload-service  leaf-service    note-service
 (消费者)          (提供者)        (提供者)        (提供者+消费者)
```

**步骤拆解：**

1. **服务启动时** — `upload-service` 启动后向 Nacos 发送心跳："我是 upload-service，我的 IP 是 172.21.0.6，端口 20880，提供 UploadRpcService 服务"
2. **服务发现时** — `gateway` 问 Nacos："谁提供了 NoteRpcService？" Nacos 返回 note-service 的地址列表
3. **Dubbo 调用时** — gateway 拿到地址后，直接通过 Dubbo Triple 协议调用 note-service，不经过 Nacos
4. **服务下线时** — note-service 停止发送心跳，Nacos 15 秒后将其从列表移除

**关键认知：Nacos 只在"找地址"这一步参与，实际的数据传输（RPC 调用）是服务之间直连的。**

---

## 2. Nacos 的两大核心功能

### 2.1 服务注册与发现（Naming Service）

```
服务提供方                        Nacos                          服务消费方
  │                                │                                │
  │── 注册(IP, 端口, 服务名) ──→   │                                │
  │── 心跳(每5秒) ──→             │                                │
  │                                │  ←── 订阅(NoteRpcService) ────│
  │                                │  ──→ 推送提供者列表 ──→        │
  │                                │                                │
  │  ←────────── RPC 直连 (不经过Nacos) ──────────→                │
```

**对应配置（本项目 `application.yml`）：**

```yaml
# 服务端 (upload-service)
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848    # Nacos 地址

dubbo:
  registry:
    address: nacos://localhost:8848    # Dubbo 使用 Nacos 作为注册中心
  protocol:
    name: tri                          # 使用 Triple 协议
    port: 20880
```

### 2.2 配置管理（Configuration Service）

虽然本项目没有用，但这是 Nacos 的另一半核心功能：

```yaml
# 在 Nacos 控制台创建一个配置：
# Data ID: gateway-dev.yml
# Group: DEFAULT_GROUP
# 内容: server.port: 9090

# 应用端只需：
spring:
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
```

修改 Nacos 控制台中的配置 → 应用自动刷新，**无需重启**。这就是"遥控器"的含义。

---

## 3. 对比：Nacos vs 其他注册中心

这是面试高频题。按市场占有率排序：

| 特性 | **Nacos** | **Eureka** | **Consul** | **Zookeeper** | **etcd** |
|------|-----------|------------|------------|---------------|----------|
| **CAP** | AP/CP 可切换 | AP | CP | CP | CP |
| **一致性协议** | 自研 Distro(AP) + Raft(CP) | 无（Peer to Peer） | Raft | ZAB | Raft |
| **健康检查** | TCP/HTTP/MySQL 主动探测 | 客户端心跳 | Agent 探测 | 客户端心跳(TTL) | 客户端心跳 |
| **配置管理** | ✅ 内置 | ❌ 需配合 Spring Cloud Config | ✅ 内置 | ❌ 需配合其他工具 | ❌ |
| **控制台** | ✅ 功能完善的 Web UI | ✅ 基础 UI | ✅ Web UI | ❌ 需第三方 | ❌ |
| **多数据中心** | ✅ | ❌ | ✅ | ❌ | ❌ |
| **雪崩保护** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **跨语言** | HTTP/gRPC SDK | Java 为主，有 REST | HTTP/DNS | 客户端绑定 | gRPC |
| **学习成本** | 中（阿里生态） | 低 | 中高 | 中高 | 中 |
| **适合场景** | Spring Cloud Alibaba、Dubbo | Netflix OSS 生态 | 多数据中心、Service Mesh | Hadoop/Kafka/Dubbo 老项目 | Kubernetes |

### 选型建议

```
你的技术栈是 Spring Cloud Alibaba + Dubbo？
  → 直接用 Nacos，不用纠结。同门产品，无缝集成。

你的技术栈是 Spring Cloud Netflix + Eureka？
  → Eureka 2.x 已停止维护，建议迁移到 Nacos。

你需要多数据中心强一致性？
  → Consul 更适合（Raft 成熟度更高）。

你只需要 KV 存储、已有 Kubernetes 集群？
  → etcd 够了（K8s 自带）。

你维护的是老 Hadoop/Kafka 系统？
  → Zookeeper 已在那里，没必要换。
```

---

## 4. 面试高频问题

### Q1: Nacos 的 AP 和 CP 模式有什么区别？怎么选？

**答：**

- **AP（Availability + Partition Tolerance）**：优先保证可用性。Nacos 集群中多数节点挂了，剩余节点仍能对外提供服务，但可能返回旧数据。**服务注册发现默认用 AP**（通过自研 Distro 协议实现）。
- **CP（Consistency + Partition Tolerance）**：优先保证一致性。集群多数节点挂了，剩余节点拒绝写入，保证数据一致。**配置管理默认用 CP**（通过 Raft 协议实现）。

一句话：**注册中心挂了最多找不到新服务，配置中心挂了可能导致线上故障，所以注册用 AP，配置用 CP。**

### Q2: Nacos 和 Eureka 的区别？

**答：**

核心区别是 Eureka 是纯 AP 的注册中心，没有配置管理功能。具体对比：

1. **健康检查**：Eureka 靠客户端心跳（30s 一次），Nacos 支持服务端主动探测（TCP/HTTP）
2. **保护机制**：Eureka 有自我保护模式（不剔除任何服务），Nacos 有更细粒度的雪崩保护
3. **数据存储**：Eureka 在内存中，Nacos 可持久化到 MySQL
4. **功能范围**：Nacos = Eureka + Spring Cloud Config（配置管理）

### Q3: Nacos 服务下线的感知延迟是多久？

**答：**

- 正常下线：服务主动调用 `deregister`，**秒级感知**
- 异常宕机：心跳超时 15s + 剔除延迟 = **约 30s** 消费者感知
- 优化方案：调整 `spring.cloud.nacos.discovery.heart-beat-interval`（默认 5s）和 `heart-beat-timeout`（默认 15s）

### Q4: 项目中为什么用了 Nacos，Dubbo 还配置了 Nacos 注册地址？

**答：**

这是两个层面的事：
```yaml
spring.cloud.nacos.discovery.server-addr: localhost:8848   # Spring Cloud 层面：服务发现、健康检查
dubbo.registry.address: nacos://localhost:8848             # Dubbo 层面：RPC 服务注册
```

- Spring Cloud Nacos Discovery：让服务出现在 Nacos 的服务列表里（HTTP 层面）
- Dubbo Registry Nacos：让 Dubbo RPC 接口注册到 Nacos（RPC 层面）

两者可以独立配置，但在 Spring Cloud Alibaba 体系中通常指向同一个 Nacos 实例。

### Q5: Nacos 集群怎么部署？

**答：**

```
生产环境最少 3 节点（Raft 协议要求奇数节点）：
  nacos-1: 192.168.1.10:8848
  nacos-2: 192.168.1.11:8848
  nacos-3: 192.168.1.12:8848

  MySQL 集群（配置数据持久化，必须）
  Nginx/Keepalived VIP（客户端统一入口，推荐）
```

**特别注意**：Nacos 2.x 以后增加了 gRPC 端口（默认 9848），负载均衡器需要同时转发 8848 和 9848。

### Q6: Nacos 1.x vs 2.x 的主要变化？

**答：**

- 2.x 新增 gRPC 长连接通道（端口 9848），替代 HTTP 短轮询
- 服务发现从 HTTP pull 变为 gRPC push，感知延迟从秒级降到毫秒级
- 性能提升约 10 倍

### Q7: 你项目中 Nacos 挂了会怎样？

**答：**

分两层回答：

1. **已有调用不受影响**：Dubbo 客户端本地缓存了服务地址列表，Nacos 挂了不影响已有连接的 RPC 调用
2. **新服务发现受影响**：新启动的服务无法注册，消费者无法获取最新的提供者列表，服务扩缩容感知不到

**结论**：Nacos 是"寻址"依赖，不是"调用"依赖。它挂了服务还能跑，但不能再有任何变化。

---

## 5. 动手实验

### 5.1 看 Nacos 里有什么

启动本项目后，打开 http://localhost:18848/nacos （Docker）或 http://localhost:8848/nacos （本地），默认账号密码 `nacos/nacos`。

在"服务管理 → 服务列表"中可以看到：

```
服务名                          实例数   健康实例   协议
gateway                         1        1          tri
upload-service                  1        1          tri
leaf-service                    1        1          tri
note-service                    1        1          tri
```

点进 `note-service` → 详情，能看到 Dubbo 注册的 RPC 接口：
```
com.example.common.NoteRpcService
  方法: createDraft, publishNote, getNoteDetail, addComment
```

### 5.2 模拟故障

```bash
# 1. 停掉 note-service
docker compose stop note-service

# 2. 尝试调用（gateway 缓存了旧地址，可能成功也可能失败）
curl "http://localhost:8080/api/note/detail?noteId=1"

# 3. 30 秒后 Nacos 剔除该实例
# 4. 重新启动 note-service
docker compose start note-service

# 5. 几秒内恢复
curl "http://localhost:8080/api/note/detail?noteId=2"
```

---

## 6. 常见误区

| 误区 | 实际 |
|------|------|
| "Nacos 是 API 网关" | 不是，Nacos 不管流量转发，只告诉调用方"去找谁" |
| "Nacos 挂了服务全挂" | 不会，Dubbo/Spring Cloud 本地有地址缓存，已连接的服务不受影响 |
| "Nacos 和 Zookeeper 一样" | Nacos 多了配置管理、Web 控制台、多种健康检查方式 |
| "注册中心越大越好" | 注册中心应该轻量，实际流量不经过它，不要和 Gateway 混淆 |

---

## 延伸阅读

- [Nacos 官方文档](https://nacos.io/docs/latest/)
- [为什么 Nacos 2.x 要用 gRPC](https://nacos.io/docs/latest/architecture/)
- [CAP 理论在 Nacos 中的实践](https://nacos.io/docs/latest/architecture/)

**记住一句话：Nacos = 微服务的电话本，有它找路方便，没它还能凭记忆走，但不能再去新地方。**
