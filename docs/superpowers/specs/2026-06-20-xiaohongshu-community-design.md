# 小红书风格社区分享前端 — 设计文档

**日期**: 2026-06-20
**状态**: 已确认

---

## 1. 项目概述

基于现有 Spring Cloud Alibaba + Dubbo 微服务后端，用 React 构建仿小红书的社区分享界面。

### 1.1 范围

**第一版包含**：
- 首页瀑布流 Feed（浏览已发布笔记）
- 笔记详情页（正文 + 评论列表 + 发表评论）
- 发布笔记页（创建草稿 → 上传图片 → 发布）
- 底部 TabBar 导航

**第一版不包含**：
- 用户登录/注册（使用模拟用户）
- 个人主页
- 点赞/收藏/关注
- 消息通知
- 搜索功能

### 1.2 模拟用户

前端写死模拟用户信息，绕过认证层：

```typescript
// src/config.ts
export const MOCK_USER = {
  userId: 1001,
  username: '小红薯用户',
  avatar: '', // 预留，使用默认头像
}
```

---

## 2. 技术选型

| 类别 | 选择 | 理由 |
|------|------|------|
| 构建工具 | Vite 5 | 极快的冷启动和 HMR，React 官方推荐替代 CRA |
| UI 框架 | React 18 + TypeScript | 生态成熟，TS 保证类型安全 |
| 样式方案 | Tailwind CSS 3 | 原子化 CSS，灵活度极高，可像素级还原设计 |
| 路由 | React Router v6 | 标准 SPA 路由方案 |
| 状态管理 | React 内置 (useState + useContext) | 3 页面的 SPA 无需引入 Redux |
| HTTP 客户端 | fetch API | 零额外依赖，浏览器原生支持 |
| 瀑布流 | CSS columns | 纯 CSS 方案，无需额外 JS 库 |

**不引入的依赖**：
- 不用 UI 组件库（Ant Design 等）——风格偏企业级，覆盖成本高
- 不用状态管理库（Redux/Zustand）——页面状态简单
- 不用 axios —— fetch 足够

---

## 3. 路由设计

```
/                    → FeedPage        (首页瀑布流)
/note/:noteId        → NoteDetailPage  (笔记详情 + 评论)
/publish             → PublishPage     (发布笔记)
```

底部 TabBar：🏠 发现 | ➕ 发布 | 💬 消息(预留)

使用 React Router v6 的 `<BrowserRouter>` + `<Routes>`。

---

## 4. 页面设计

### 4.1 首页 Feed（FeedPage）

**双列瀑布流布局**：
- 使用 CSS `column-count: 2` 实现
- 每列等宽，高度自适应
- 卡片包含：封面图（渐变色占位/实际图片）、标题、用户头像+昵称、点赞数
- 下拉刷新：重新请求列表
- 滚动加载更多：触底触发分页

**数据来源**：`GET /api/note/list?page=0&size=20`（需要后端新增）

### 4.2 笔记详情页（NoteDetailPage）

**布局**（从上到下）：
- 封面大图（全宽，16:9 或自适应）
- 用户信息行（头像 + 昵称）
- 标题（粗体，15px）
- 正文内容
- 互动数据行（点赞数、评论数）
- 评论列表（头像 + 昵称 + 内容 + 时间）
- 底部固定评论输入框

**数据来源**：`GET /api/note/detail?noteId=ID`

**并发优化**：笔记数据和评论数据在同一个接口返回（后端 NoteService 已用 CompletableFuture 并发查询）

### 4.3 发布笔记页（PublishPage）

**表单字段**：
- 封面图上传区（点击触发文件选择）
- 标题输入框
- 正文输入框（多行 textarea）
- 两个按钮：存草稿 / 发布

**发布流程**（4 步走）：
1. `POST /api/note/draft` → 获取 noteId
2. `GET /api/upload/presigned?fileName=&contentType=` → 获取上传 URL
3. `PUT {uploadUrl}` → 直传 MinIO（浏览器 → MinIO，不经过后端）
4. `POST /api/note/publish` → 关联图片 + 发布

**草稿模式**：仅执行步骤 1，跳过步骤 2-4，状态为 "DRAFT"

---

## 5. 组件树

```
App
├── TabBar                     # 底部固定导航栏
├── FeedPage                   # / 路由
│   ├── WaterfallLayout        # CSS columns 容器
│   │   └── NoteCard[]         # 笔记卡片（图片 + 标题 + 用户 + 赞）
│   └── LoadingSkeleton        # 骨架屏加载态
├── NoteDetailPage             # /note/:noteId 路由
│   ├── ImageHero              # 封面大图
│   ├── NoteContent            # 标题 + 正文
│   ├── CommentList            # 评论列表容器
│   │   └── CommentItem[]      # 单条评论
│   └── CommentInput           # 底部固定评论输入框
└── PublishPage                # /publish 路由
    ├── ImageUploader           # 图片上传组件（含预览 + 预签名上传）
    ├── TitleInput              # 标题输入
    └── ContentEditor           # 正文编辑
```

---

## 6. API 层设计

### 6.1 API 封装（src/api/index.ts）

```typescript
const BASE_URL = 'http://localhost:8080';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const json = await res.json();
  if (json.code !== 200) throw new Error(json.msg);
  return json.data;
}
```

### 6.2 前端调用接口汇总

| 方法 | 路径 | 用途 | 调用方 |
|------|------|------|--------|
| GET | /api/note/list?page=&size= | **新增** 分页获取已发布笔记 | FeedPage |
| GET | /api/note/detail?noteId= | 笔记详情 + 评论 | NoteDetailPage |
| POST | /api/note/draft | 创建草稿 | PublishPage |
| POST | /api/note/publish | 发布笔记 | PublishPage |
| POST | /api/note/comment | 发表评论 | NoteDetailPage |
| GET | /api/upload/presigned | 获取预签名上传 URL | PublishPage |

### 6.3 需要后端新增的接口

**`GET /api/note/list?page=0&size=20`**

响应格式：
```json
{
  "code": 200,
  "data": [
    {
      "noteId": 1234567890,
      "userId": 1001,
      "title": "笔记标题",
      "summary": "前200字摘要...",
      "coverUrl": "http://minio:9000/notes/2026/06/20/xxx_cover.jpg?sign=...",
      "status": "PUBLISHED",
      "createdAt": 1781920000000,
      "updatedAt": 1781920000000
    }
  ]
}
```

**关键字段 `coverUrl`**：后端生成 GET 预签名 URL（有效期 24 小时），前端无需关心签名细节。后端在 NoteService 中使用 `MinioClient.getPresignedObjectUrl(Method.GET, ...)` 生成。

---

## 7. 后端改动清单

### 7.1 note-service 新增

**NoteService.java** 新增方法：
```java
public List<NoteDetailResponse> listPublishedNotes(int page, int size)
```

逻辑：查询 Cassandra 中 `status='PUBLISHED'` 的笔记，按 `created_at` 倒序，分页返回。每条记录附带 `coverUrl`（GET 预签名 URL）。

**NoteRpcService.java** 新增接口方法：
```java
List<NoteDetailResponse> listPublishedNotes(int page, int size);
```

**NoteRpcServiceImpl.java** 委托实现。

**gateway NoteController.java** 新增路由：
```java
@GetMapping("/list")
public Result<List<NoteDetailResponse>> listNotes(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size)
```

### 7.2 Cassandra 查询

CommentRepository 需新增分页查询已发布笔记的方法：
```java
@Query("SELECT * FROM note WHERE status = 'PUBLISHED' LIMIT ?1")
List<NoteEntity> findPublished(int limit);
```

**限制**：Cassandra 不支持 `OFFSET`，分页需要基于 `created_at` 或 `id` 做游标分页（cursor-based pagination）。v1 版本可简化为 `LIMIT` 查询，后续优化。

---

## 8. 图片上传链路

```
[PublishPage]
  │
  ├─ Step 1: POST /api/note/draft → noteId (Cassandra: INSERT, status=DRAFT)
  │
  ├─ Step 2: 用户选择图片文件 (input type=file)
  │
  ├─ Step 3: GET /api/upload/presigned?fileName=&contentType=
  │           → uploadUrl (5分钟有效, MinIO PUT 签名)
  │
  ├─ Step 4: fetch(uploadUrl, {method:'PUT', body:file})
  │           → 浏览器直传 MinIO (流不经过后端)
  │
  └─ Step 5: POST /api/note/publish {noteId, fileName, contentType}
              → Cassandra UPDATE status=PUBLISHED, objectKey=...
              → 返回笔记详情 (含 coverUrl: GET 预签名 URL)
```

**错误处理**：
- Step 3 失败 → 提示"获取上传凭证失败"，可重试
- Step 4 失败 → 提示"上传失败"，可重新选择文件
- Step 5 失败 → 提示"发布失败"，草稿已保存（Step 1 成功），可继续

---

## 9. 视觉风格

| 属性 | 值 |
|------|-----|
| 品牌色 | `#ff2442`（小红书红） |
| 主文字 | `#1a1a1a` |
| 次要文字 | `#999999` |
| 背景灰 | `#f5f5f5` |
| 卡片白 | `#ffffff` |
| 卡片圆角 | `rounded-xl` (12px) |
| 按钮圆角 | `rounded-full` |
| 页面内边距 | `p-4` (16px) |
| 卡间距 | `gap-2` (8px) |
| 标题字号 | 15px font-bold |
| 正文字号 | 13px |
| 辅助文字 | 11px text-gray-400 |

---

## 10. 目录结构

```
front/
├── index.html
├── package.json
├── vite.config.ts
├── tailwind.config.js
├── tsconfig.json
├── postcss.config.js
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── config.ts
│   ├── index.css                  # Tailwind directives
│   ├── api/
│   │   └── index.ts               # fetch 封装 + API 方法
│   ├── pages/
│   │   ├── FeedPage.tsx
│   │   ├── NoteDetailPage.tsx
│   │   └── PublishPage.tsx
│   ├── components/
│   │   ├── TabBar.tsx
│   │   ├── NoteCard.tsx
│   │   ├── WaterfallLayout.tsx
│   │   ├── CommentItem.tsx
│   │   ├── CommentInput.tsx
│   │   ├── ImageUploader.tsx
│   │   └── LoadingSkeleton.tsx
│   └── types/
│       └── index.ts
```

---

## 11. 非功能需求

### 11.1 加载态

- Feed 首次加载：显示 4 个骨架屏卡片（`LoadingSkeleton`）
- 详情页加载：全页 loading spinner
- 发布中：按钮显示 spinner + 禁用
- 图片上传中：上传区显示进度条

### 11.2 错误态

- 网络错误：Toast 提示 + 重试按钮
- 列表为空：空状态插画 + "还没有笔记，去发布第一篇吧"
- 笔记不存在：404 提示
- 上传失败：错误提示 + 重新选择

### 11.3 限流处理

- HTTP 429 响应 → 显示"请求太频繁，请稍后再试"
- 自动 3 秒后可重试

---

## 12. 自审清单

- [x] 无 TBD / TODO 占位符
- [x] 架构设计与页面设计一致
- [x] 范围明确：3 页面，不包含 v2 功能
- [x] 后端改动已列出具体文件和接口签名
- [x] 图片上传链路完整（4 步流程）
- [x] 图片展示方案明确（GET 预签名 URL）
- [x] 视觉风格定稿（配色/圆角/字号）
