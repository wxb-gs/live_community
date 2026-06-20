# Zookeeper 与 KRaft 入门指南

## 一句话概括

**ZooKeeper** 是分布式系统的"居委会主任"——管理元数据、协调选举、维护一致性，各种分布式组件（Kafka、Hadoop、HBase、Dubbo）都找它帮忙协调。**KRaft** 是 Kafka 社区为了让 Kafka 摆脱对 ZooKeeper 的依赖，自研的一套共识引擎——把 ZooKeeper 踢掉，Kafka 自己管自己。

两者的关系：**KRaft 是 ZK 在 Kafka 场景中的替代者，不是通用替代者。**

---

## 1. ZooKeeper 到底是什么

### 1.1 一句话定义

ZooKeeper 是一个**分布式协调服务**。它提供了一个高可用的、强一致性的、树状结构的小数据存储，分布式系统用它来存元数据、选主、感知节点上下线。

### 1.2 数据模型：像文件系统

```
                         /
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
      /brokers        /consumers      /config
          │              │              │
    ┌─────┼─────┐        │              │
    ▼     ▼     ▼        ▼              ▼
  /ids  /topics        /group1       /kafka
    │                  │
    ▼                  ▼
  /0  /1  /2        /offsets
  │
  ▼
{"host":"10.0.0.1","port":9092}
```

每个路径（叫 ZNode）可以存一小段数据 + 监听变化：

| 概念 | 说明 | 类比 |
|------|------|------|
| **ZNode** | 树中的一个节点，可存数据（默认 1MB 上限） | 文件系统中的文件或目录 |
| **持久节点** | 创建后永久存在，除非手动删除 | 普通文件 |
| **临时节点** | 客户端断开连接自动删除 | 进程打开的文件描述符 |
| **顺序节点** | 名称后带自增序号（如 `/lock-0000000001`） | 取号排队 |
| **Watcher** | 监听节点变化（数据变更、子节点增减等），一次性触发 | inotify 文件监听 |

### 1.3 ZooKeeper 在项目中怎么用

```
┌─────────────────────────────────────────────┐
│              ZooKeeper 集群                  │
│          zk1       zk2       zk3            │
│          (Leader)  (Follower)(Follower)      │
│              │                              │
│       写请求都先经过 Leader                   │
│       Leader 通过 ZAB 协议同步到 Followers    │
│       读请求可直接从 Follower 处理            │
└──────────────┬──────┬───────┬───────────────┘
               │      │       │
      ┌────────┘      │       └────────┐
      ▼               ▼                ▼
┌──────────┐   ┌──────────┐    ┌──────────┐
│  Kafka   │   │  Hadoop  │    │  Dubbo   │
│ broker   │   │ NameNode │    │ service  │
│ 信息     │   │ 主备切换  │    │ 注册发现  │
└──────────┘   └──────────┘    └──────────┘
```

**具体场景：**

1. **Kafka** — 在 ZK 中存 broker 列表、topic 分区信息、Controller 选举、consumer offset（老版本）
2. **Hadoop** — NameNode HA，主备切换时用 ZK 的临时节点抢锁
3. **HBase** — RegionServer 上下线感知，Master 选举
4. **Dubbo** — 服务注册与发现（虽然 Dubbo 也支持 Nacos/ZK/Redis 等注册中心）
5. **分布式锁** — 利用临时顺序节点 + Watcher 实现公平锁

---

## 2. ZAB 协议 —— ZooKeeper 的一致性保障

ZooKeeper 不直接用 Paxos 或 Raft，而是自研了一套叫 **ZAB**（ZooKeeper Atomic Broadcast）的协议。

### 2.1 ZAB 的四种状态

```
        启动/超时发现没 Leader
        ┌──────────┐
        │          ▼
    ┌───┴──────────────┐
    │  LOOKING         │  寻找 Leader（选举中）
    └──────┬───────────┘
           │ 选举完成
           ▼
    ┌──────────────────┐
    │  FOLLOWING       │  我是 Follower，跟 Leader 同步
    └──────────────────┘

    ┌──────────────────┐
    │  LEADING         │  我是 Leader，处理写请求 + 广播
    └──────────────────┘
```

### 2.2 写请求流程

```
Client                        Leader                      Follower-1        Follower-2
  │                              │                             │                 │
  │── 写入 /brokers/ids/0 ──→   │                             │                 │
  │                              │── PROPOSAL(事务提案) ──→    │                 │
  │                              │── PROPOSAL ──────────────────────────────→  │
  │                              │                             │                 │
  │                              │  ←── ACK(同意) ────────────│                 │
  │                              │  ←── ACK ───────────────────────────────────│
  │                              │                             │                 │
  │                              │  收到过半数 ACK → 提交       │                 │
  │                              │── COMMIT ──────────────→   │                 │
  │                              │── COMMIT ──────────────────────────────────→│
  │                              │                             │                 │
  │  ←── 写入成功 ───────────────│                             │                 │
```

**核心规则：**

1. 所有写操作必须经过 Leader
2. Leader 将写操作包装成 Proposal，广播给所有 Follower
3. 收到**超过半数** Follower 的 ACK（确认）后，Leader 发送 COMMIT
4. 所有节点收到 COMMIT 后才将数据应用到内存

这个流程和 Raft 高度相似，实际上 ZAB 可以看作 Raft 的"表亲"——思想相同，实现细节不同。

### 2.3 ZAB 的两种模式

| 模式 | 用途 | 特点 |
|------|------|------|
| **广播模式**（正常运行时） | 处理写请求、同步数据 | Leader→Follower 广播 Proposal + 两阶段提交 |
| **恢复模式**（选举时） | 选新 Leader、同步落后数据 | 新 Leader 必须拥有最新已提交 Proposal |

### 2.4 为什么 ZooKeeper 的节点数必须是奇数

```
3 节点集群：可以容忍 1 台挂了（2 台 > 半数）
4 节点集群：也只能容忍 1 台挂了（3 台 > 半数才过半）
5 节点集群：可以容忍 2 台挂了

→ 4 节点比 3 节点多费一台机器，可靠性没提高
→ 所以 ZK 集群用 3 / 5 / 7 个节点（奇数），不用偶数
```

---

## 3. ZooKeeper 的痛点 —— 为什么 Kafka 要踢掉它

Kafka 多年来一直依赖 ZooKeeper，但随着规模增长，问题越来越明显：

### 痛点 1：两套系统，运维负担翻倍

```
运维需要：
  ├── 精通 Kafka（分区、副本、Producer、Consumer...）
  ├── 精通 ZooKeeper（ZAB、JVM 调优、会话超时...）
  └── 保证两者之间的网络、安全组、证书配置正确
```

Kafka 出问题时需要同时排查 Kafka 日志和 ZK 日志，排查成本指数级上升。

### 痛点 2：元数据一致性依赖 ZK 的提交速度

```
Kafka Controller 执行一个分区迁移：

  Kafka Controller → ZK Leader → ZK Follower 半数 ACK → ZK COMMIT
       → Kafka Controller 收到 ZK Watch 回调 → 通知所有 Broker

  链路长 + ZK 写入瓶颈 + Watch 是异步回调 → 延迟不可控
```

ZK 写是单线程串行的，大量分区变更时 ZK 成为瓶颈。

### 痛点 3：数据冗余，元数据有两份

```
Kafka 本地磁盘：log.dirs 下有分区数据 + 元数据快照
ZooKeeper：也存了 broker/topic/分区/consumer group 元数据

→ 两份数据，可能出现不一致
→ ZK 数据丢了要重建，Kafka 数据丢了也要重建
```

### 痛点 4：Watch 机制不够灵活

- Watcher 是一次性的，触发后要重新注册
- 重新注册期间可能丢失事件
- 大量 Watcher 导致 ZK 集群压力大（著名的"herd effect"羊群效应）

这些痛点直接催生了 KRaft。

---

## 4. KRaft —— Kafka 的"自我管理"方案

### 4.1 一句话定义

KRaft（Kafka Raft）是 Kafka 2.8+ 引入的**内置共识引擎**。它让 Kafka 不依赖外部 ZooKeeper，完全用自身的 Raft 协议实现元数据管理和 Leader 选举。

### 4.2 架构对比

**Kafka + ZooKeeper 模式（老方式）：**

```
┌──────────┐  ┌──────────┐  ┌──────────┐
│  Broker  │  │  Broker  │  │  Broker  │
│ (Controller)│ │          │  │          │
└─────┬────┘  └──────────┘  └──────────┘
      │
      │  Controller 和 ZK 交互
      ▼
┌──────────────────────────┐
│       ZooKeeper 集群      │
│                          │
│  存 broker、topic、分区、  │
│  consumer group 等元数据  │
└──────────────────────────┘
      依赖外部系统！
```

**KRaft 模式（新方式）：**

```
┌──────────┐  ┌──────────┐  ┌──────────┐
│  Broker  │  │  Broker  │  │  Broker  │
│  (元数据  │◄─│  (元数据  │◄─│  (元数据  │
│   Leader) │  │  Follower)│  │  Follower)│
└──────────┘  └──────────┘  └──────────┘
      │             │              │
      └─────────────┼──────────────┘
                    │
           Raft 协议直接在 Kafka 进程内运行
           元数据存为一个特殊的内置 Topic: @metadata
```

### 4.3 KRaft 的核心设计

```
┌──────────────────────────────────────────────┐
│              KRaft 共识层                      │
│                                              │
│  1. Raft 协议在 Broker 进程内运行               │
│  2. 选举一个 Quorum Controller 作为 Leader     │
│  3. 所有元数据变更写 Raft 日志                  │
│  4. 日志复制到所有 Quorum 节点                  │
│  5. 提交后应用到内存元数据缓存                   │
│  6. 元数据持久化为内置 Topic: @metadata        │
│                                              │
│  角色分离：                                    │
│  - Controller 节点：处理元数据、选主 （KRaft）    │
│  - Broker 节点：处理数据（和以前一样）            │
│  - 一个节点可以同时扮演两个角色（小型集群）        │
└──────────────────────────────────────────────┘
```

### 4.4 KRaft 配置示例

```properties
# KRaft 模式下的 server.properties

# 指定 KRaft 模式（不需要 zookeeper.connect 了）
process.roles=broker,controller

# 节点 ID（替代 broker.id）
node.id=1

# Quorum 投票者列表（替代 zookeeper.connect）
controller.quorum.voters=1@kafka1:9093,2@kafka2:9093,3@kafka3:9093

# 元数据日志目录
metadata.log.dir=/var/lib/kafka/metadata

# 不再需要这一行！
# zookeeper.connect=zk1:2181,zk2:2181,zk3:2181
```

对比一下，老方式需要运维 ZK 集群、配 `zookeeper.connect`、处理会话超时；新方式只需要 Kafka 自己。

### 4.5 KRaft 带来的收益

| 维度 | 改进 |
|------|------|
| **运维复杂度** | 从两套系统 → 一套系统，不再需要 ZK 运维技能 |
| **元数据一致性** | 元数据在 Kafka 内部，不会出现两套数据不一致的问题 |
| **分区迁移速度** | Controller 本地读写，不经过 ZK，百万分区迁移秒级完成 |
| **启动时间** | 不用等 ZK 连接和加载，启动更快 |
| **部署规模** | 支持百万分区（ZK 模式下几万分区就到瓶颈了） |
| **安全性** | 减少一个需要配置安全策略的外部组件 |

---

## 5. 详细对比

### 5.1 ZooKeeper vs KRaft

| 维度 | **ZooKeeper** | **KRaft** |
|------|---------------|-----------|
| **定位** | 通用分布式协调服务 | Kafka 专用的内置共识引擎 |
| **共识协议** | ZAB（自研） | Raft（标准化） |
| **部署方式** | 独立进程，单独集群 | Kafka Broker 进程内运行 |
| **数据模型** | 树状 ZNode（像文件系统） | Raft 日志 + 线性 KV Store |
| **数据容量** | 小数据（每个 ZNode ≤ 1MB） | 元数据主题，容量大得多 |
| **Watch/通知** | 一次性 Watcher，需要重复注册 | 持续订阅，基于日志重放 |
| **适用场景** | Kafka / Hadoop / HBase / Dubbo 等 | 仅 Kafka |
| **单点问题** | ZK 本身可能成为瓶颈 | 无额外单点 |
| **运维成本** | 两套系统，需要分别运维 | 只运维 Kafka |
| **版本要求** | — | Kafka 2.8+（试验性）、3.3+（生产可用） |
| **成熟度** | 15+ 年生产验证 | 相对新（2021-），但已在主流公司大规模使用 |

### 5.2 KRaft 的 Raft vs ZooKeeper 的 ZAB

| | Raft (KRaft) | ZAB (ZooKeeper) |
|------|-------------|------|
| **标准化** | 学术界公开论文（2014），多语言实现 | ZooKeeper 自研，无标准实现 |
| **可理解性** | 以"容易理解"为设计目标 | 协议细节比 Raft 复杂 |
| **Leader 选举** | 随机超时 + RequestVote | 基于 ZXID 的比较 |
| **日志复制** | AppendEntries RPC | Proposal + ACK + COMMIT 两阶段 |
| **成员变更** | 联合共识（Joint Consensus） | 重配置（Reconfig） |
| **读一致性** | 默认 Leader 读（线性一致性可选） | 默认允许 Follower 读（可能读到旧数据） |

实际上两者在核心思路上高度相似——都是 Leader-based 的共识协议，过半数提交，Leader 挂了重新选举。

---

## 6. 选型与迁移

### 6.1 Kafka 选型建议

```
你在用 Kafka 3.3+ 新部署集群？
  → 直接用 KRaft 模式。省一套 ZK 集群，运维成本低很多。

你在用老版本 Kafka + ZooKeeper？
  → 先升级到 3.5+，再迁移到 KRaft。官方提供了平滑迁移工具。

你的 Kafka 集群规模很大（几千 broker、几十万分区）？
  → 迁移到 KRaft 收益巨大。ZK 模式下 Controller 切换慢、分区迁移慢，
     KRaft 模式下这些操作快几个数量级。

你只是个人学习/小项目、跑 Docker Compose？
  → 用 KRaft。少个容器，配置简单，启动快。
```

### 6.2 ZooKeeper 现在还有用吗

有。ZooKeeper 仍然是通用分布式协调的事实标准，它的领地不受威胁：

```
ZooKeeper 继续发光发热的地方：
  ├── Hadoop NameNode HA（暂时没有替代方案）
  ├── HBase RegionServer 协调
  ├── 旧版 Dubbo（存量项目）
  ├── 分布式锁/选主/发布订阅（各种自研系统）
  └── 大量存量 Kafka 集群（迁移需要时间）

KRaft 只取代了 Kafka + ZK 这一对组合。
Hadoop/HBase 生态还没有"踢掉 ZK"的方案。
```

---

## 7. 面试高频问题

### Q1: 为什么 Kafka 要自己做 KRaft，不用 Raft 开源实现？

**答：**

确实有成熟的 Raft 开源实现（如 etcd 的 Raft 库），但 Kafka 团队选择自己写 KRaft，原因是：

1. **深度集成**：元数据事件需要和 Kafka 的 Topic/Partition 语义直接绑定，外部 Raft 库需要大量适配
2. **性能优化**：Kafka 特有的元数据变更频率极高（百万分区），通用 Raft 实现没优化过这种场景
3. **零拷贝日志**：KRaft 的元数据日志直接复用 Kafka 的存储层，通用实现做不到
4. **运维一致性**：KRaft 的错误码、监控指标、日志格式和 Kafka 完全对齐，排查问题不需要切换心智

一句话：**用通用 Raft 库能跑，但要做得极致只能自己写。**

### Q2: ZAB 和 Raft 在选举上有何不同？

**答：**

| | ZAB (ZooKeeper) | Raft (KRaft) |
|------|---------|------|
| **选举触发** | Follower 超时没收到心跳 → LOOKING 状态 | Follower 超时没收到心跳 → Candidate 状态 |
| **选票标准** | 谁的 ZXID 大谁优先（ZXID = epoch + counter） | 谁的 Term 大 + 日志最新谁优先 |
| **选举过程** | 广播自己的 ZXID，收到更高 ZXID 就让位 | 自增 Term，广播 RequestVote，过半同意变 Leader |
| **选举后** | 新 Leader 先同步，再开始广播 | 新 Leader 先同步（匹配日志），再开始服务 |

本质相同：**最新数据的节点优先当选，过半投票决定胜负。**

### Q3: ZooKeeper 能作为注册中心，为什么 Kafka 还要踢掉它？

**答：**

这是两个不同层面的问题：

- **ZooKeeper 作为注册中心**：这是它对外提供的功能（服务注册/发现），被 Dubbo/Nacos 等系统使用
- **Kafka 依赖 ZooKeeper 管理内部元数据**：这是 Kafka 自己对 ZK 的依赖（存 broker 列表、分区信息）

Kafka 踢掉 ZK 不意味着 ZK 不好，而是说明：

1. Kafka 对 ZK 的依赖过重，成了瓶颈
2. 运维两套分布式系统的成本太高（故障要两边排查）
3. Kafka 只需要 ZK 功能的子集（KV 存储 + Watch），自己实现更高效

**ZK 适合做注册中心（元数据量小、变更低频），不适合做高频大数据量的元数据存储。**

### Q4: ZooKeeper 和 Nacos 在注册中心这个角色上有什么区别？

**答：**

| | ZooKeeper | Nacos |
|------|-----------|-------|
| **CAP** | CP（强一致性） | AP/CP 可切换 |
| **健康检查** | 客户端心跳（TTL 临时节点） | TCP/HTTP/MySQL 主动探测 |
| **配置管理** | 没有（需要配合其他工具） | 内置 |
| **Web 控制台** | 需要第三方 | 内置 |
| **性能** | 写操作有 Leader 瓶颈 | AP 模式下无中心瓶颈 |
| **容量** | 小数据（不适合存大量配置） | 支持大量配置项 |

Nacos 为了做配置管理（CP 场景需要强一致），内部也用了 Raft（CP 模式下）。本质上 Nacos CP 模式 ≈ ZK 的角色 + 配置管理能力。

### Q5: KRaft 模式下一个 Topic 的元数据是怎么流转的？

**答：**

```
1. Admin 创建 Topic "orders"，3 分区，3 副本
      │
      ▼
2. KRaft Leader (Controller) 收到请求
      │
      ▼
3. Leader 把变更写入 Raft 日志:
   [CreateTopicRecord: name=orders, partitions=3, rf=3]
      │
      ▼
4. 通过 Raft 复制到所有 Quorum 节点 (过半 ACK)
      │
      ▼
5. 提交后应用到内存元数据 (MetadataImage)
      │
      ▼
6. Controller 把分区分配给具体的 Broker:
   分区 0 → broker-1/2/3 (leader=b1), 分区 1 → broker-2/3/1 (leader=b2)
      │
      ▼
7. Controller 向 Broker 发送 LeaderAndIsr 请求，启动分区
      │
      ▼
8. @metadata Topic 持久化所有元数据（恢复时可重放）
```

对比 ZK 模式：步骤 3-5 要去 ZK 走一圈，步骤 5 要等 Watch 回调，延迟大得多。

### Q6: 从 ZK 模式迁移到 KRaft 模式需要停机吗？

**答：**

Kafka 3.5+ 提供了**在线迁移**方案（rolling upgrade）：

```
1. 先在每台 broker 配置中加 zookeeper.connect + controller.quorum.voters 两套配置
2. 设置 zookeeper.metadata.migration.enable=true 开启双写模式
   → 元数据同时写 ZK 和 KRaft 日志
3. 逐个滚动重启 Broker
4. 确认 KRaft 元数据完整后，设置 metadata.version=3.5-IV2
5. 移除 zookeeper.connect 配置
6. 关闭双写，迁移完成

整个过程中 Kafka 集群不停止服务。
```

但这是高级操作，生产环境需要先在测试环境验证。小集群直接停机重建更简单。

### Q7: etcd 也用 Raft，为什么 Kafka 不用 etcd 替代 ZooKeeper？

**答：**

这个问题经常被问到，因为 etcd 和 ZooKeeper 功能高度重叠。

Kafka 团队在 KIP-500 中讨论过这个选项，最终选择了自研 KRaft，原因：

1. **外部依赖问题依旧存在**：换成 etcd 还是两套系统，运维问题没解决
2. **etcd 的数据模型和 Kafka 元数据不匹配**：etcd 是扁平的 KV Store，ZK 有树状结构 + Watch，都不是 Kafka 想要的
3. **etcd 的容量和性能不是为 Kafka 的元数据频率设计的**
4. **更关键的是，Kafka 不想再依赖任何外部系统来做元数据管理**

**结论：问题不是 ZK 和 etcd 哪个好，而是 Kafka 不想再有外部依赖。**

---

## 8. 动手实验

### 8.1 ZooKeeper 客户端操作

```bash
# 连接 ZK（如果 Docker 里有 ZK 的话）
docker run -it --rm zookeeper:3.8 zkCli.sh -server localhost:2181

# 创建节点
create /hello "world"

# 读取数据
get /hello
# → world

# 创建临时节点（断开连接后自动删除）
create -e /session "temp-data"

# 监听变化（一次性）
get -w /hello
# 另一个终端 set /hello "new-value" → 收到通知

# 创建顺序节点
create -s /lock/req- ""
# → /lock/req-0000000001
```

### 8.2 KRaft 快速体验

```bash
# Docker Compose 启动一个单节点 KRaft Kafka
# docker-compose.yml
services:
  kafka:
    image: apache/kafka:3.7.0
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT

# 验证：查看元数据日志
docker exec kafka ls /var/lib/kafka/data/__cluster_metadata-0/
# → 看到 snapshot / log 文件，就是 KRaft 的元数据持久化
```

---

## 9. 常见误区

| 误区 | 实际 |
|------|------|
| "ZooKeeper 过时了、被淘汰了" | 不是。ZK 仍是 Hadoop/HBase/存量 Kafka 的核心依赖，只是 Kafka 新版本有了替代方案 |
| "KRaft = ZooKeeper 的实现" | 不是。KRaft 只服务 Kafka，不能用来做通用的分布式协调或注册中心 |
| "KRaft 用了 Raft，所以比 ZK 强" | 不是。Raft 和 ZAB 能力相当，KRaft 的优势是"内置"而不是协议更强 |
| "ZooKeeper 是数据库" | 不是。ZK 是协调服务，存的是元数据（小数据量），不能替代数据库存业务数据 |
| "ZooKeeper 的 CAP 是 AP" | 不是。ZK 是 CP（强一致性），Leader 挂了期间不可写，保证数据一致 |
| "Kafka 3.X 必须用 KRaft" | 不是。Kafka 3.X 依然兼容 ZK 模式，只是推荐新部署用 KRaft |

---

## 延伸阅读

- [ZooKeeper 官方文档](https://zookeeper.apache.org/doc/current/)
- [ZAB 协议论文](https://zookeeper.apache.org/doc/current/zookeeperTutorial.html)
- [KIP-500: Replace ZooKeeper with a Self-Managed Metadata Quorum](https://cwiki.apache.org/confluence/display/KAFKA/KIP-500%3A+Replace+ZooKeeper+with+a+Self-Managed+Metadata+Quorum)
- [Raft 协议可视化动画](https://raft.github.io/)
- [KRaft 模式配置指南](https://kafka.apache.org/documentation/#kraft)

**记住一句话：ZooKeeper 是用 ZAB 协议保证一致性的通用分布式协调员，KRaft 是 Kafka 用 Raft 协议给自己造的专属协调员——前者是别人的东西自己要维护，后者是自己的东西自己管。**
