2026-01 ~ 2026-03高并发内容分享社区个人项目
项目描述：独立开发仿小红书社区，支持海量用户的内容发布、社交互动、Feed 流推送及实时搜索、AI摘要等功能。

技术栈：Java 21、Spring Cloud Alibaba、Nacos、GateWay、Feign、MySQL、Redis、Kafka、Spring Security、Canal

* 登录认证：基于 Spring Security + JWT 实现网关层统一鉴权与动态路由；设计双 Token 续签机制，通过 Redis 存储 RefreshToken 状态，设置 15 分钟 + 7 天过期时间，兼顾性能和安全。
* 笔记服务：搭建基于美团 Leaf 的分布式 ID 服务，支持号段和雪花算法，创建唯一草稿，发布时通过预签名 + 前端直传至 OSS，并引入 Cassandra 存储笔记详情与评论数据，使用 CompletableFuture 并行查询进行优化。
* 点赞系统：设计 Redis + Kafka 异步持久化架构，利用消费端聚合写入优化 MySQL IO 性能。单节点压测纯缓存链路 QPS 达 5.3k，异步落库全链路 QPS 达 3.1k。通过定时对账保证数据可靠，实现高吞吐与最终一致性的平衡。
* Feed流推送：设计推拉结合混合架构，普通用户发布异步推至粉丝 Inbox，大 V 发布采用拉模式仅写入 Outbox，采用 Redis ZSet 缓存条目列表，有效平衡了写扩散与读扩散的系统负载。
* 搜索系统：基于 Canal + Kafka 构建 ES 实时索引同步链路，确保内容发布的秒级搜索可见；利用 Function Score 融合 BM25 算法与点赞等业务权重优化排序，并应用 Completion Suggester 实现快速前缀联想。