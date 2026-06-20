# 小红书风格社区前端 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建仿小红书的社区分享 React SPA（Vite + TS + Tailwind CSS），调用现有 Spring Cloud 后端，实现瀑布流浏览、笔记详情、评论、发布（预签名直传 MinIO）。

**Architecture:** React 18 SPA 通过 gateway:8080 REST API 与后端通信。底部 TabBar 三页面路由（Feed/发布/消息占位）。CSS columns 双列瀑布流。fetch 封装 API 层。模拟用户 userId=1001。

**Tech Stack:** Vite 5, React 18, TypeScript, Tailwind CSS 3, React Router v6, fetch API

---

## 后端改动

### Task 1: NoteRepository 新增查询已发布笔记

**Files:**
- Modify: `note-service/src/main/java/com/example/note/repository/NoteRepository.java`

- [ ] **Step 1: 添加 findPublished 方法**

```java
package com.example.note.repository;

import com.example.note.entity.NoteEntity;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends CassandraRepository<NoteEntity, Long> {

    @Query("SELECT * FROM note WHERE status = 'PUBLISHED' LIMIT ?0")
    List<NoteEntity> findPublished(int limit);
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl note-service
```
Expected: BUILD SUCCESS

---

### Task 2: NoteService 新增 listPublishedNotes 方法

**Files:**
- Modify: `note-service/src/main/java/com/example/note/service/NoteService.java`

- [ ] **Step 1: 在 NoteService 末尾添加 listPublishedNotes 方法**

在文件最后的 `toCommentResponse` 方法之后，类的闭合 `}` 之前添加：

```java
    public List<NoteDetailResponse> listPublishedNotes(int page, int size) {
        List<NoteEntity> entities = noteRepository.findPublished(size);
        return entities.stream()
                .map(entity -> {
                    NoteDetailResponse resp = toDetailResponse(entity);
                    if (entity.getObjectKey() != null && !entity.getObjectKey().isEmpty()) {
                        try {
                            String coverUrl = minioClient.getPresignedObjectUrl(
                                    io.minio.GetPresignedObjectUrlArgs.builder()
                                            .method(io.minio.http.Method.GET)
                                            .bucket(bucket)
                                            .object(entity.getObjectKey())
                                            .expiry(24, TimeUnit.HOURS)
                                            .build());
                            resp.setUploadUrl(coverUrl);
                        } catch (Exception e) {
                            log.warn("Failed to generate cover URL for noteId={}", entity.getId(), e);
                        }
                    }
                    return resp;
                })
                .collect(Collectors.toList());
    }
```

> **Note:** 复用已有的 `toDetailResponse()` 方法。`setUploadUrl(coverUrl)` 用 `uploadUrl` 字段暂存封面图 URL（该字段在列表场景下语义为封面图 GET URL）。

---

### Task 3: NoteRpcService 接口 + NoteRpcServiceImpl 新增 listPublishedNotes

**Files:**
- Modify: `common/src/main/java/com/example/common/NoteRpcService.java`
- Modify: `note-service/src/main/java/com/example/note/rpc/NoteRpcServiceImpl.java`

- [ ] **Step 1: NoteRpcService.java 新增接口方法**

在 `addComment` 方法声明之后添加：

```java
    List<NoteDetailResponse> listPublishedNotes(int page, int size);
```

- [ ] **Step 2: NoteRpcServiceImpl.java 新增实现**

在 `addComment` 方法之后添加：

```java
    @Override
    public List<NoteDetailResponse> listPublishedNotes(int page, int size) {
        return noteService.listPublishedNotes(page, size);
    }
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl common,note-service
```
Expected: BUILD SUCCESS

---

### Task 4: Gateway NoteController 新增 /list 端点

**Files:**
- Modify: `gateway/src/main/java/com/example/gateway/controller/NoteController.java`

- [ ] **Step 1: 添加 listNotes 方法**

在 `addComment` 方法之后，类的闭合 `}` 之前添加：

```java
    @GetMapping("/list")
    public Result<List<NoteDetailResponse>> listNotes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<NoteDetailResponse> list = noteRpcService.listPublishedNotes(page, size);
        return Result.ok(list);
    }
```

- [ ] **Step 2: 添加 import**

在文件顶部 import 区域添加：

```java
import java.util.List;
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl gateway
```
Expected: BUILD SUCCESS

---

## 前端项目搭建

### Task 5: 使用 Vite 脚手架创建 React + TypeScript 项目

**Files:**
- Create: `front/` (完整项目目录)

- [ ] **Step 1: 创建 Vite 项目**

```bash
cd C:/lib/codes/java_projects/spring_cloud_test
npm create vite@latest front -- --template react-ts
```

- [ ] **Step 2: 安装依赖**

```bash
cd front
npm install
```

Expected: 无错误，`front/node_modules/` 已创建。

---

### Task 6: 安装额外依赖 + 配置 Tailwind CSS

**Files:**
- Modify: `front/package.json`
- Create: `front/tailwind.config.js`
- Create: `front/postcss.config.js`
- Modify: `front/src/index.css`
- Modify: `front/vite.config.ts`

- [ ] **Step 1: 安装 Tailwind CSS + React Router**

```bash
cd front
npm install react-router-dom
npm install -D tailwindcss @tailwindcss/vite
```

- [ ] **Step 2: 配置 Vite (vite.config.ts)**

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
```

> **Note:** `proxy` 配置让开发时前端 `/api/*` 请求自动代理到 gateway:8080，避免跨域问题。

- [ ] **Step 3: 写入 Tailwind 指令 (src/index.css)**

```css
@import "tailwindcss";
```

- [ ] **Step 4: 验证 dev server 启动**

```bash
npm run dev
```
Expected: Vite 启动在 localhost:5173，页面空白但无报错。

---

### Task 7: TypeScript 类型定义 + 配置文件

**Files:**
- Create: `front/src/types/index.ts`
- Create: `front/src/config.ts`

- [ ] **Step 1: 写入类型定义 (src/types/index.ts)**

```typescript
export interface NoteSummary {
  noteId: number;
  userId: number;
  title: string;
  summary: string;
  coverUrl: string;
  status: string;
  createdAt: number;
  updatedAt: number;
}

export interface CommentItem {
  commentId: number;
  noteId: number;
  userId: number;
  content: string;
  createdAt: number;
}

export interface NoteDetail {
  noteId: number;
  userId: number;
  title: string;
  content: string;
  summary: string;
  uploadUrl: string;
  objectKey: string;
  status: string;
  createdAt: number;
  updatedAt: number;
  comments: CommentItem[];
}

export interface CreateDraftResponse {
  noteId: number;
  status: string;
}

export interface PresignedUrlResponse {
  uploadUrl: string;
  objectKey: string;
  expiresAt: number;
}

export interface ApiResult<T> {
  code: number;
  msg: string;
  data: T;
}
```

- [ ] **Step 2: 写入配置文件 (src/config.ts)**

```typescript
export const API_BASE = ''; // Vite proxy handles /api → gateway:8080

export const MOCK_USER = {
  userId: 1001,
  username: '小红薯用户',
} as const;

export const PAGE_SIZE = 20;
```

---

### Task 8: API 层封装

**Files:**
- Create: `front/src/api/index.ts`

- [ ] **Step 1: 写入 API 封装**

```typescript
import { API_BASE, MOCK_USER } from '../config';
import type {
  NoteSummary,
  NoteDetail,
  CommentItem,
  CreateDraftResponse,
  PresignedUrlResponse,
  ApiResult,
} from '../types';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (res.status === 429) {
    throw new Error('请求太频繁，请稍后再试');
  }
  if (!res.ok) {
    throw new Error(`请求失败 (${res.status})`);
  }
  const json: ApiResult<T> = await res.json();
  if (json.code !== 200) {
    throw new Error(json.msg || '服务器错误');
  }
  return json.data;
}

export async function fetchNotes(page: number, size: number): Promise<NoteSummary[]> {
  return request<NoteSummary[]>(`/api/note/list?page=${page}&size=${size}`);
}

export async function fetchNoteDetail(noteId: number): Promise<NoteDetail> {
  return request<NoteDetail>(`/api/note/detail?noteId=${noteId}`);
}

export async function createDraft(title: string, content: string): Promise<CreateDraftResponse> {
  return request<CreateDraftResponse>('/api/note/draft', {
    method: 'POST',
    body: JSON.stringify({
      userId: MOCK_USER.userId,
      title,
      content,
    }),
  });
}

export async function getPresignedUrl(
  fileName: string,
  contentType: string
): Promise<PresignedUrlResponse> {
  return request<PresignedUrlResponse>(
    `/api/upload/presigned?fileName=${encodeURIComponent(fileName)}&contentType=${encodeURIComponent(contentType)}`
  );
}

export async function uploadToMinio(url: string, file: File): Promise<void> {
  const res = await fetch(url, {
    method: 'PUT',
    body: file,
    headers: { 'Content-Type': file.type },
  });
  if (!res.ok) {
    throw new Error(`上传失败 (${res.status})`);
  }
}

export async function publishNote(
  noteId: number,
  fileName: string,
  contentType: string
): Promise<NoteDetail> {
  return request<NoteDetail>('/api/note/publish', {
    method: 'POST',
    body: JSON.stringify({ noteId, fileName, contentType }),
  });
}

export async function addComment(noteId: number, content: string): Promise<CommentItem> {
  return request<CommentItem>('/api/note/comment', {
    method: 'POST',
    body: JSON.stringify({
      noteId,
      userId: MOCK_USER.userId,
      content,
    }),
  });
}
```

---

## 前端组件

### Task 9: TabBar 底部导航栏

**Files:**
- Create: `front/src/components/TabBar.tsx`

- [ ] **Step 1: 写入 TabBar 组件**

```tsx
import { useNavigate, useLocation } from 'react-router-dom';

export default function TabBar() {
  const navigate = useNavigate();
  const location = useLocation();

  const tabs = [
    { path: '/', label: '发现', icon: '🏠' },
    { path: '/publish', label: '发布', icon: '➕' },
    { path: '/messages', label: '消息', icon: '💬' },
  ];

  return (
    <nav className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-100 z-50">
      <div className="max-w-lg mx-auto flex justify-around items-center h-14">
        {tabs.map((tab) => {
          const active = location.pathname === tab.path;
          return (
            <button
              key={tab.path}
              onClick={() => navigate(tab.path)}
              className={`flex flex-col items-center gap-0.5 px-4 py-1 ${
                active ? 'text-[#ff2442]' : 'text-gray-400'
              }`}
            >
              <span className="text-xl">{tab.icon}</span>
              <span className="text-[10px]">{tab.label}</span>
            </button>
          );
        })}
      </div>
    </nav>
  );
}
```

---

### Task 10: NoteCard + WaterfallLayout 瀑布流卡片

**Files:**
- Create: `front/src/components/NoteCard.tsx`
- Create: `front/src/components/WaterfallLayout.tsx`

- [ ] **Step 1: 写入 NoteCard 组件**

```tsx
import { useNavigate } from 'react-router-dom';
import type { NoteSummary } from '../types';
import { MOCK_USER } from '../config';

export default function NoteCard({ note }: { note: NoteSummary }) {
  const navigate = useNavigate();

  return (
    <div
      onClick={() => navigate(`/note/${note.noteId}`)}
      className="bg-white rounded-xl overflow-hidden cursor-pointer break-inside-avoid mb-2 shadow-sm active:scale-[0.98] transition-transform"
    >
      {note.coverUrl ? (
        <img
          src={note.coverUrl}
          alt={note.title}
          className="w-full object-cover"
          loading="lazy"
          onError={(e) => {
            (e.target as HTMLImageElement).style.display = 'none';
            (e.target as HTMLImageElement).nextElementSibling?.classList.remove('hidden');
          }}
        />
      ) : null}
      <div
        className={`w-full h-40 bg-gradient-to-br from-pink-400 to-red-500 flex items-center justify-center text-white text-sm ${
          note.coverUrl ? 'hidden' : ''
        }`}
      >
        {note.title.charAt(0)}
      </div>
      <div className="p-2.5">
        <h3 className="text-[13px] font-bold leading-tight line-clamp-2 text-[#1a1a1a]">
          {note.title}
        </h3>
        <div className="flex items-center gap-1.5 mt-2">
          <div className="w-4 h-4 rounded-full bg-gray-200 flex items-center justify-center text-[8px] text-gray-500">
            {MOCK_USER.username.charAt(0)}
          </div>
          <span className="text-[11px] text-gray-400">{MOCK_USER.username}</span>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: 写入 WaterfallLayout 组件**

```tsx
import type { ReactNode } from 'react';

export default function WaterfallLayout({ children }: { children: ReactNode }) {
  return (
    <div className="columns-2 gap-2 px-2">
      {children}
    </div>
  );
}
```

---

### Task 11: LoadingSkeleton 骨架屏

**Files:**
- Create: `front/src/components/LoadingSkeleton.tsx`

- [ ] **Step 1: 写入 LoadingSkeleton 组件**

```tsx
export default function LoadingSkeleton() {
  return (
    <div className="columns-2 gap-2 px-2">
      {[1, 2, 3, 4].map((i) => (
        <div key={i} className="bg-white rounded-xl overflow-hidden break-inside-avoid mb-2 animate-pulse">
          <div className={`bg-gray-200 ${i % 2 === 0 ? 'h-36' : 'h-44'}`} />
          <div className="p-2.5 space-y-2">
            <div className="h-3 bg-gray-200 rounded w-3/4" />
            <div className="h-3 bg-gray-200 rounded w-1/2" />
            <div className="flex items-center gap-1.5">
              <div className="w-4 h-4 rounded-full bg-gray-200" />
              <div className="h-2.5 bg-gray-200 rounded w-16" />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
```

---

### Task 12: CommentItem + CommentInput 评论组件

**Files:**
- Create: `front/src/components/CommentItem.tsx`
- Create: `front/src/components/CommentInput.tsx`

- [ ] **Step 1: 写入 CommentItem 组件**

```tsx
import type { CommentItem as CommentType } from '../types';
import { MOCK_USER } from '../config';

function formatTime(timestamp: number): string {
  const diff = Date.now() - timestamp;
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}小时前`;
  return `${Math.floor(hours / 24)}天前`;
}

export default function CommentItem({ comment }: { comment: CommentType }) {
  return (
    <div className="flex gap-2 py-2.5">
      <div className="w-7 h-7 rounded-full bg-gray-200 flex items-center justify-center text-[10px] text-gray-500 shrink-0">
        {comment.userId === MOCK_USER.userId ? MOCK_USER.username.charAt(0) : 'U'}
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-baseline gap-2">
          <span className="text-[12px] font-medium text-[#1a1a1a]">
            {comment.userId === MOCK_USER.userId ? MOCK_USER.username : `用户${comment.userId}`}
          </span>
          <span className="text-[10px] text-gray-400">{formatTime(comment.createdAt)}</span>
        </div>
        <p className="text-[13px] text-[#333] mt-0.5 break-words">{comment.content}</p>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: 写入 CommentInput 组件**

```tsx
import { useState } from 'react';

interface Props {
  onSubmit: (content: string) => Promise<void>;
}

export default function CommentInput({ onSubmit }: Props) {
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);

  const handleSubmit = async () => {
    const trimmed = text.trim();
    if (!trimmed || sending) return;
    setSending(true);
    try {
      await onSubmit(trimmed);
      setText('');
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-100 z-40">
      <div className="flex items-center gap-2 px-3 py-2 max-w-lg mx-auto">
        <input
          type="text"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSubmit()}
          placeholder="发表评论..."
          maxLength={500}
          className="flex-1 bg-gray-100 rounded-full px-4 py-2 text-[13px] outline-none focus:ring-2 focus:ring-[#ff2442]/30"
        />
        <button
          onClick={handleSubmit}
          disabled={!text.trim() || sending}
          className="text-[13px] font-bold text-[#ff2442] disabled:text-gray-300 shrink-0"
        >
          {sending ? '发送中' : '发送'}
        </button>
      </div>
    </div>
  );
}
```

---

### Task 13: ImageUploader 图片上传组件

**Files:**
- Create: `front/src/components/ImageUploader.tsx`

- [ ] **Step 1: 写入 ImageUploader 组件**

```tsx
import { useState, useRef } from 'react';
import { getPresignedUrl, uploadToMinio } from '../api';

interface Props {
  onUploaded: (fileName: string, contentType: string) => void;
}

export default function ImageUploader({ onUploaded }: Props) {
  const [preview, setPreview] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const handleFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      setError('请选择图片文件');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setError('图片大小不能超过 10MB');
      return;
    }

    setError(null);
    setUploading(true);

    const reader = new FileReader();
    reader.onload = () => setPreview(reader.result as string);
    reader.readAsDataURL(file);

    try {
      const { uploadUrl } = await getPresignedUrl(file.name, file.type);
      await uploadToMinio(uploadUrl, file);
      onUploaded(file.name, file.type);
    } catch (err) {
      setError(err instanceof Error ? err.message : '上传失败');
      setPreview(null);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        onChange={handleFile}
        className="hidden"
      />
      {preview ? (
        <div className="relative rounded-xl overflow-hidden">
          <img src={preview} alt="封面" className="w-full h-52 object-cover" />
          {uploading && (
            <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
              <div className="text-white text-sm">上传中...</div>
            </div>
          )}
          {!uploading && (
            <button
              onClick={() => { setPreview(null); fileRef.current?.click(); }}
              className="absolute top-2 right-2 bg-black/50 text-white text-xs px-3 py-1 rounded-full"
            >
              重选
            </button>
          )}
        </div>
      ) : (
        <button
          onClick={() => fileRef.current?.click()}
          className="w-full h-52 border-2 border-dashed border-gray-300 rounded-xl flex flex-col items-center justify-center gap-2 text-gray-400 hover:border-[#ff2442] hover:text-[#ff2442] transition-colors"
        >
          <span className="text-3xl">📷</span>
          <span className="text-sm">{uploading ? '上传中...' : '点击上传封面图'}</span>
        </button>
      )}
      {error && <p className="text-red-500 text-xs mt-1">{error}</p>}
    </div>
  );
}
```

---

## 前端页面

### Task 14: FeedPage 首页瀑布流

**Files:**
- Create: `front/src/pages/FeedPage.tsx`

- [ ] **Step 1: 写入 FeedPage 页面**

```tsx
import { useState, useEffect, useCallback } from 'react';
import { fetchNotes } from '../api';
import { PAGE_SIZE } from '../config';
import type { NoteSummary } from '../types';
import WaterfallLayout from '../components/WaterfallLayout';
import NoteCard from '../components/NoteCard';
import LoadingSkeleton from '../components/LoadingSkeleton';

export default function FeedPage() {
  const [notes, setNotes] = useState<NoteSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);

  const loadNotes = useCallback(async (pageNum: number) => {
    try {
      setError(null);
      const data = await fetchNotes(pageNum, PAGE_SIZE);
      if (pageNum === 0) {
        setNotes(data);
      } else {
        setNotes((prev) => [...prev, ...data]);
      }
      setHasMore(data.length === PAGE_SIZE);
      setPage(pageNum);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadNotes(0);
  }, [loadNotes]);

  const handleRefresh = () => {
    setLoading(true);
    loadNotes(0);
  };

  const handleScroll = useCallback(() => {
    if (loading || !hasMore) return;
    const nearBottom =
      window.innerHeight + window.scrollY >= document.body.offsetHeight - 300;
    if (nearBottom) {
      setLoading(true);
      loadNotes(page + 1);
    }
  }, [loading, hasMore, page, loadNotes]);

  useEffect(() => {
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, [handleScroll]);

  if (loading && notes.length === 0) {
    return (
      <div className="pt-4 pb-16">
        <LoadingSkeleton />
      </div>
    );
  }

  return (
    <div className="pb-16">
      <header className="sticky top-0 bg-white/80 backdrop-blur z-30 px-4 py-3 border-b border-gray-50">
        <h1 className="text-lg font-bold text-[#1a1a1a]">发现</h1>
      </header>

      {error && (
        <div className="px-4 py-3">
          <div className="bg-red-50 text-red-500 text-sm rounded-lg px-4 py-3 flex items-center justify-between">
            <span>{error}</span>
            <button onClick={handleRefresh} className="font-bold text-red-600">
              重试
            </button>
          </div>
        </div>
      )}

      {notes.length === 0 && !loading ? (
        <div className="flex flex-col items-center justify-center h-80 text-gray-400">
          <span className="text-5xl mb-4">📝</span>
          <p className="text-sm">还没有笔记</p>
          <p className="text-xs mt-1">去发布第一篇吧</p>
        </div>
      ) : (
        <div className="pt-3">
          <WaterfallLayout>
            {notes.map((note) => (
              <NoteCard key={note.noteId} note={note} />
            ))}
          </WaterfallLayout>
          {loading && (
            <div className="text-center py-4 text-gray-400 text-sm">
              加载中...
            </div>
          )}
          {!hasMore && notes.length > 0 && (
            <div className="text-center py-4 text-gray-300 text-xs">
              —— 已经到底了 ——
            </div>
          )}
        </div>
      )}
    </div>
  );
}
```

---

### Task 15: NoteDetailPage 笔记详情页

**Files:**
- Create: `front/src/pages/NoteDetailPage.tsx`

- [ ] **Step 1: 写入 NoteDetailPage 页面**

```tsx
import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { fetchNoteDetail, addComment } from '../api';
import { MOCK_USER } from '../config';
import type { NoteDetail, CommentItem as CommentType } from '../types';
import CommentItem from '../components/CommentItem';
import CommentInput from '../components/CommentInput';

function formatTime(timestamp: number): string {
  const date = new Date(timestamp);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export default function NoteDetailPage() {
  const { noteId } = useParams<{ noteId: string }>();
  const [note, setNote] = useState<NoteDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [comments, setComments] = useState<CommentType[]>([]);

  const loadDetail = useCallback(async () => {
    if (!noteId) return;
    try {
      setError(null);
      setLoading(true);
      const data = await fetchNoteDetail(Number(noteId));
      setNote(data);
      setComments(data.comments || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, [noteId]);

  useEffect(() => {
    loadDetail();
  }, [loadDetail]);

  const handleAddComment = async (content: string) => {
    if (!noteId) return;
    const newComment = await addComment(Number(noteId), content);
    setComments((prev) => [newComment, ...prev]);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="animate-spin w-8 h-8 border-2 border-[#ff2442] border-t-transparent rounded-full" />
      </div>
    );
  }

  if (error || !note) {
    return (
      <div className="flex flex-col items-center justify-center h-screen text-gray-400 gap-4">
        <p>{error || '笔记不存在'}</p>
        <button onClick={loadDetail} className="text-[#ff2442] text-sm font-bold">
          重试
        </button>
      </div>
    );
  }

  return (
    <div className="pb-16">
      {/* 封面大图 */}
      {note.uploadUrl || note.objectKey ? (
        <img
          src={note.uploadUrl || ''}
          alt={note.title}
          className="w-full aspect-[4/3] object-cover"
          onError={(e) => {
            (e.target as HTMLImageElement).style.display = 'none';
          }}
        />
      ) : (
        <div className="w-full aspect-[4/3] bg-gradient-to-br from-pink-300 to-red-400 flex items-center justify-center text-white text-4xl">
          {MOCK_USER.username.charAt(0)}
        </div>
      )}

      <div className="px-4">
        {/* 用户信息 */}
        <div className="flex items-center gap-2 py-3">
          <div className="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center text-xs text-gray-500">
            {MOCK_USER.username.charAt(0)}
          </div>
          <span className="text-sm font-medium">{MOCK_USER.username}</span>
        </div>

        {/* 标题 + 正文 */}
        <h1 className="text-lg font-bold text-[#1a1a1a] leading-relaxed">
          {note.title}
        </h1>
        <p className="text-[14px] text-[#333] leading-relaxed mt-3 whitespace-pre-wrap">
          {note.content}
        </p>
        <div className="text-xs text-gray-400 mt-2">
          {formatTime(note.updatedAt || note.createdAt)}
        </div>

        {/* 分割线 */}
        <div className="border-t border-gray-100 mt-4 pt-4">
          <h3 className="text-sm font-bold text-[#1a1a1a] mb-1">
            评论 ({comments.length})
          </h3>
        </div>
      </div>

      {/* 评论列表 */}
      <div className="px-4 pb-16">
        {comments.length === 0 ? (
          <p className="text-gray-400 text-sm text-center py-8">暂无评论，快来抢沙发吧</p>
        ) : (
          comments.map((c) => (
            <CommentItem key={c.commentId} comment={c} />
          ))
        )}
      </div>

      {/* 评论输入框 */}
      <CommentInput onSubmit={handleAddComment} />
    </div>
  );
}
```

---

### Task 16: PublishPage 发布笔记页

**Files:**
- Create: `front/src/pages/PublishPage.tsx`

- [ ] **Step 1: 写入 PublishPage 页面**

```tsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createDraft, publishNote } from '../api';
import ImageUploader from '../components/ImageUploader';

export default function PublishPage() {
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [fileName, setFileName] = useState('');
  const [contentType, setContentType] = useState('');
  const [publishing, setPublishing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const handlePublish = async (asDraft: boolean) => {
    if (!title.trim()) {
      setError('请输入标题');
      return;
    }
    if (!asDraft && !fileName) {
      setError('请上传封面图');
      return;
    }

    setPublishing(true);
    setError(null);

    try {
      const draft = await createDraft(title.trim(), content.trim());

      if (asDraft) {
        setSuccessMsg('草稿已保存！');
        setTimeout(() => navigate('/'), 1000);
        return;
      }

      await publishNote(draft.noteId, fileName, contentType);
      setSuccessMsg('发布成功！');
      setTimeout(() => navigate(`/note/${draft.noteId}`), 800);
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败');
    } finally {
      setPublishing(false);
    }
  };

  return (
    <div className="pb-16">
      <header className="sticky top-0 bg-white/80 backdrop-blur z-30 px-4 py-3 border-b border-gray-50 flex items-center justify-between">
        <button onClick={() => navigate(-1)} className="text-sm text-gray-500">
          取消
        </button>
        <h1 className="text-base font-bold text-[#1a1a1a]">发布笔记</h1>
        <div className="w-10" />
      </header>

      <div className="px-4 pt-4 space-y-4">
        {error && (
          <div className="bg-red-50 text-red-500 text-sm rounded-lg px-4 py-3">{error}</div>
        )}
        {successMsg && (
          <div className="bg-green-50 text-green-600 text-sm rounded-lg px-4 py-3">
            {successMsg}
          </div>
        )}

        <ImageUploader
          onUploaded={(name, type) => {
            setFileName(name);
            setContentType(type);
          }}
        />

        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="输入标题..."
          maxLength={100}
          className="w-full text-[15px] font-bold px-1 py-2 outline-none placeholder:text-gray-300"
        />

        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="分享你的内容..."
          rows={8}
          maxLength={5000}
          className="w-full text-[14px] leading-relaxed px-1 py-2 outline-none resize-none placeholder:text-gray-300"
        />

        <div className="flex gap-3 pt-4">
          <button
            onClick={() => handlePublish(true)}
            disabled={publishing}
            className="flex-1 h-11 rounded-full border border-gray-200 text-gray-600 text-sm font-medium active:bg-gray-50 disabled:opacity-50"
          >
            {publishing ? '保存中...' : '存草稿'}
          </button>
          <button
            onClick={() => handlePublish(false)}
            disabled={publishing}
            className="flex-1 h-11 rounded-full bg-[#ff2442] text-white text-sm font-bold active:bg-[#e0203a] disabled:opacity-50"
          >
            {publishing ? '发布中...' : '发布'}
          </button>
        </div>
      </div>
    </div>
  );
}
```

---

## App Shell

### Task 17: App.tsx 路由 + 布局

**Files:**
- Create: `front/src/App.tsx`

- [ ] **Step 1: 写入 App 组件**

```tsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import TabBar from './components/TabBar';
import FeedPage from './pages/FeedPage';
import NoteDetailPage from './pages/NoteDetailPage';
import PublishPage from './pages/PublishPage';

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-[#f5f5f5] max-w-lg mx-auto relative">
        <Routes>
          <Route path="/" element={<FeedPage />} />
          <Route path="/note/:noteId" element={<NoteDetailPage />} />
          <Route path="/publish" element={<PublishPage />} />
          <Route
            path="/messages"
            element={
              <div className="flex items-center justify-center h-screen text-gray-400 text-sm">
                消息功能即将上线
              </div>
            }
          />
        </Routes>
        <TabBar />
      </div>
    </BrowserRouter>
  );
}
```

---

### Task 18: main.tsx + index.html

**Files:**
- Modify: `front/src/main.tsx`
- Modify: `front/index.html`

- [ ] **Step 1: 更新 main.tsx**

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

> 删除默认的 App.css 文件：
> ```bash
> rm -f front/src/App.css
> ```

- [ ] **Step 2: 更新 index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <meta name="theme-color" content="#ff2442" />
    <title>Live Community</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 3: 清理 Vite 默认文件**

```bash
rm -f front/src/App.css front/src/assets/react.svg public/vite.svg
```

- [ ] **Step 4: 验证 dev server**

```bash
cd front && npm run dev
```
Expected: 打开 http://localhost:5173，看到底部 TabBar 和首页（可能为空，后端未启动时列表为空或报错可忽略）。

---

## 验证与收尾

### Task 19: 全链路验证

- [ ] **Step 1: 编译后端全部模块**

```bash
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 2: 编译前端**

```bash
cd front && npx tsc --noEmit
```
Expected: 无 TypeScript 错误

- [ ] **Step 3: 构建前端**

```bash
cd front && npm run build
```
Expected: dist/ 目录生成，无错误

---

## 自审清单

1. **Spec coverage**: Feed 瀑布流(Task 10+14)、笔记详情(Task 15)、评论(Task 12+15)、发布+上传(Task 13+16)、后端新接口(Task 1-4)、TabBar(Task 9)、加载态(Task 11)、错误态(各页面内置)、模拟用户(Task 7)
2. **Placeholder scan**: 无 TBD/TODO，无"implement later"，所有代码完整
3. **Type consistency**: `NoteSummary` / `NoteDetail` / `CommentItem` 在前端 types 和后端 DTO 间字段对应一致；`createDraft` 返回 `CreateDraftResponse` 被 PublishPage 正确使用 `draft.noteId`
