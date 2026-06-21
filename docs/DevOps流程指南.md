# DevOps 流程指南

## 1. 什么是 DevOps

DevOps 不是工具，是一套**文化、实践和工具链**的组合，目标是缩短"代码提交 → 生产运行"的周期，同时保证质量和稳定性。

```
传统模式（瀑布/筒仓）:

Dev 团队 ──→ [扔过去] ──→ Ops 团队
  "能跑"                      "别出事"
           ← 对抗关系 →

DevOps 模式:

Dev + Ops 共享同一目标:
  快速交付 ✅  + 稳定运行 ✅  = 业务价值
```

### CALMS 框架

| 维度 | 含义 | 实践 |
|------|------|------|
| **C**ulture（文化） | 打破 Dev 和 Ops 对立 | 共同 KPI、不甩锅 |
| **A**utomation（自动化） | 一切能自动的就自动 | CI/CD、IaC、自动化测试 |
| **L**ean（精益） | 消除浪费，小步迭代 | 小批次发布、MVP |
| **M**easurement（度量） | 数据驱动改进 | DORA 指标、告警、监控 |
| **S**haring（分享） | 知识透明流动 | Postmortem、文档、轮值 on-call |

---

## 2. 核心流程全景

```
┌─────────────────────────────────────────────────────────────────┐
│                      DevOps 全生命周期                            │
│                                                                 │
│  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐         │
│  │  Plan   │ → │  Code   │ → │  Build  │ → │  Test   │         │
│  │  规划    │   │  编码    │   │  构建    │   │  测试    │         │
│  └─────────┘   └─────────┘   └─────────┘   └─────────┘         │
│       ↑                                          │               │
│       │                                          ▼               │
│  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐         │
│  │ Monitor │ ← │ Operate │ ← │ Deploy  │ ← │ Release │         │
│  │  监控    │   │  运维    │   │  部署    │   │  发布    │         │
│  └─────────┘   └─────────┘   └─────────┘   └─────────┘         │
│                                                                 │
│  ────────────── CI (Continuous Integration) ─────────────→      │
│  ───────────── CD (Continuous Delivery/Deployment) ──────→      │
└─────────────────────────────────────────────────────────────────┘
```

### 各阶段说明

| 阶段 | 做什么 | 产出 |
|------|--------|------|
| **Plan** | 需求拆解、技术方案、排期 | User Story、技术方案文档 |
| **Code** | 写代码 + 本地自测 + Code Review | 分支、PR、通过 Review |
| **Build** | 编译、打包、镜像构建 | JAR、Docker Image |
| **Test** | 单元测试、集成测试、性能测试 | 测试报告 |
| **Release** | 版本标记、变更日志、发布单 | Tag、Release Notes |
| **Deploy** | 灰度/滚动/蓝绿发布 | 生产环境更新 |
| **Operate** | 配置管理、日志巡检、故障处理 | Runbook、告警处理记录 |
| **Monitor** | 指标采集、告警、SLA 监控 | 仪表盘、告警通知 |

---

## 3. CI/CD 详解

### 3.1 CI（持续集成）

开发者频繁（每天多次）将代码合并到主干，每次合并自动触发构建和测试。

```
Developer push → Git Repo
  → Webhook 触发
    → CI Pipeline:
        1. 拉取代码
        2. mvn compile / npm install
        3. Checkstyle / Lint
        4. mvn test / npm test
        5. 生成测试报告
        6. 通知结果（通过 / 失败）
```

**基本原则**：
- 每个人每天至少合并一次到主干
- 构建失败必须第一时间修复
- 构建必须在 10 分钟内完成
- 测试必须能本地运行

### 3.2 CD（持续交付 / 持续部署）

| | Continuous Delivery | Continuous Deployment |
|---|---|---|
| **含义** | 代码随时可发布，但由人决定何时发布 | 通过 CI 后自动部署到生产 |
| **人工干预** | 有（手动触发部署按钮） | 无（全自动） |
| **适用** | 金融、合规严格场景 | 互联网、高频迭代 |
| **本项目** | ✅ 推荐 | |

```
CI 通过后:

Release:
  → mvn release:prepare（版本升级、打 Tag）
  → 生成 Release Notes
  → 推送 Tag 到 Git

Deploy（手动触发）:
  → 拉取对应版本 Tag
  → docker build -t xxx:version .
  → docker push registry/xxx:version
  → docker compose / k8s apply 更新目标环境
```

---

## 4. 分支策略

### 4.1 Git Flow（传统）

```
main         ★────── ★────────── ★
               \     / \        /
release         ★──★   ★──────★
                 \       /
develop           ★─★─★─★─★─★
                    \ /
feature              ★──★
```

**适用**：有固定发布周期的传统项目（如每个月发版）。

### 4.2 GitHub Flow（推荐）

```
main  ★───────── ★────────── ★
        \         \          /
feature   ★─★─★    ★─★─★─★
         (PR+CI)    (PR+CI)
```

**规则**：
1. `main` 永远可部署
2. 所有改动从 `main` 开出分支
3. 分支命名：`feature/xxx`、`fix/xxx`、`chore/xxx`
4. PR + Code Review + CI 通过 → 合并回 `main`
5. 合并后立即部署

**本项目的选择**：GitHub Flow，单体仓库多模块，每个 PR 独立可测试。

### 4.3 分支命名规范

| 前缀 | 用途 | 示例 |
|------|------|------|
| `feature/` | 新功能 | `feature/search-service` |
| `fix/` | Bug 修复 | `fix/login-timeout` |
| `docs/` | 文档 | `docs/api-reference` |
| `refactor/` | 重构 | `refactor/extract-common` |
| `chore/` | 杂务（构建、依赖） | `chore/upgrade-spring-boot` |

---

## 5. 测试金字塔

```
┌──────────────────────┐
│      E2E Tests       │  ← 少量，全链路，最慢
│     (端到端测试)       │
└──────────────────────┘
┌────────────────────┐
│   Integration Tests │  ← 适中，跨模块，中速
│   (集成测试)         │
└────────────────────┘
┌──────────────────────┐
│     Unit Tests       │  ← 大量，单方法，最快
│     (单元测试)        │
└──────────────────────┘
```

| 层 | 占比 | 速度 | 稳定性 | 本项目中 |
|----|------|------|--------|---------|
| Unit | 70% | 毫秒 | 高 | Controller/Service 单测 |
| Integration | 20% | 秒 | 中 | Dubbo RPC + DB 集成 |
| E2E | 10% | 分钟 | 低 | HTTP 请求全链路验证 |

---

## 6. 部署策略

| 策略 | 操作方式 | 停机 | 回滚速度 | 适合 |
|------|---------|------|---------|------|
| **滚动部署** | 逐个替换 Pod | 无 | 秒级（kubectl rollback） | 无状态服务 |
| **蓝绿部署** | 两套完整环境切换 | 无 | 秒级（流量切回旧版） | 大版本、不兼容升级 |
| **金丝雀部署** | 少量流量打新版本 | 无 | 秒级 | 需要灰度验证 |
| **重建部署** | 全量停旧启新 | 有 | 分钟级 | 开发环境 |

### 针对本项目（Docker Compose）

```bash
# 重建部署开发环境
docker compose build upload-service
docker compose up -d --no-deps upload-service

# 生产环境可用滚动（K8s Deployment rolling update）
kubectl set image deployment/upload-service upload-service=xxx:v2.0.0
kubectl rollout status deployment/upload-service
```

---

## 7. Docker 化标准

### 7.1 Dockerfile 模式

本项目的标准 Dockerfile：

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 7.2 DCE（Distroless Container）演进

```
阶段 1（当前）: JRE Image → ~180MB
阶段 2（优化）: jlink 裁剪 → ~60MB
阶段 3（进阶）: Distroless + GraalVM Native → ~20MB
```

### 7.3 Docker Compose 编排

本项目已使用 `docker compose` 编排 9 个服务，详见 `docs/Docker部署指南.md`。

---

## 8. 监控与可观测性

### 8.1 三根支柱

| 支柱 | 回答的问题 | 工具 |
|------|-----------|------|
| **Metrics（指标）** | "系统有没有问题？" | Prometheus + Grafana |
| **Logging（日志）** | "出问题时发生了什么？" | ELK / Loki |
| **Tracing（链路追踪）** | "请求走过了哪些服务？" | Jaeger / SkyWalking |

### 8.2 DORA 四大指标

| 指标 | 含义 | 精英团队水平 |
|------|------|------------|
| **部署频率** | 代码部署到生产的频率 | 按需（每天多次） |
| **变更前置时间** | 从提交代码到上线的时间 | < 1 小时 |
| **变更失败率** | 部署导致故障的比例 | 0-15% |
| **故障恢复时间** | 从故障到恢复的时间 | < 1 小时 |

### 8.3 告警设计

```
┌──────────┐     ┌──────────┐     ┌──────────┐
│ 收集指标   │ →   │ 判定规则   │ →   │ 通知渠道   │
│          │     │          │     │          │
│ CPU      │     │ > 80%   │     │ 钉钉/企微 │
│ 内存      │     │ > 90%   │     │ 邮件      │
│ 接口延迟   │     │ p99 > 1s│     │ 电话 (P0) │
│ 错误率     │     │ > 1%    │     │          │
│ Kafka LAG │     │ > 5000  │     │          │
└──────────┘     └──────────┘     └──────────┘

告警分级:
  P0: 生产不可用 → 电话 + 即时响应 (5min)
  P1: 功能受损   → 即时消息 + 15min 内响应
  P2: 降级告警   → 通知 + 1h 内处理
  P3: 预警       → 邮件 / 工单
```

---

## 9. 本项目的 DevOps 落地

### 9.1 当前状态

| 环节 | 工具/做法 | 状态 |
|------|----------|------|
| 代码管理 | Git + GitHub | ✅ 已有 |
| 分支策略 | GitHub Flow | ✅ 已有 |
| 构建 | `mvn clean package` | ✅ 已有 |
| 容器化 | Docker + docker compose | ✅ 已有 |
| CI | 待集成 GitHub Actions | ⏳ 待做 |
| 测试 | JUnit 5 + Spring Test | ✅ 已有 |
| 部署 | Docker Compose | ✅ 已有 |
| 监控 | Sentinel（限流）+ 日志 | 🔨 部分 |

### 9.2 推荐下一步

```
1. 接入 CI（GitHub Actions）
   → push 自动编译 + 跑测试
   → PR 展示测试结果

2. 接入 CD
   → Tag push 触发自动构建镜像
   → 自动推送到镜像仓库

3. 接入可观测性
   → Spring Boot Actuator + Prometheus
   → Grafana 仪表盘
```

---

## 10. 常见问题

### Q: DevOps 和 SRE 有什么区别？

DevOps 是**文化和方法论**（怎么组织团队和流程），SRE 是**工程实践**（用软件工程方法做运维）。通常 SRE 团队承担 DevOps 中的 "Operate + Monitor" 职责。

### Q: CI 和 CD 一定要同时做吗？

不。CI 是最基础的第一步（不 CI 连代码能不能跑都不知道）。CD 可以分阶段：先做到"随时可部署的制品"（Continuous Delivery），再追求"自动部署"（Continuous Deployment）。

### Q: 是否应该强制 Code Review？

是的。Calver 统计：Code Review 能发现 50-70% 的 Bug，远高于测试。至少一人 Approve 后才可合并。

### Q: 容器化是必须的吗？

不是，但强烈推荐。好处：
- 环境一致性（"在我机器上能跑"从此消失）
- 一键启动完整环境（docker compose up）
- 天然支持 CI/CD
- 回顾性部署（出问题随时切回旧版本 Image）

---

## 11. 推荐学习路径

1. **理解 CI/CD 概念**（本文 1-3 节）
2. **选一个分支策略并严格执行**（本文第 4 节）
3. **写 Dockerfile + docker compose**（本文第 7 节，参考 `docs/Docker部署指南.md`）
4. **接入 GitHub Actions 跑 CI** — 先做到 push 自动编译 + 测试
5. **深入阅读**：[The DevOps Handbook](https://itrevolution.com/product/the-devops-handbook-second-edition/)、[DORA 年度报告](https://dora.dev/)
