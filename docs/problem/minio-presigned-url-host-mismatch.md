# MinIO 预签名 URL 签名不匹配导致 403 Forbidden

## 日期

2026-06-20

## 现象

前端调用 MinIO 预签名 PUT URL 上传文件时返回 **403 Forbidden**。

## 根因

MinIO Java SDK 生成预签名 URL 时，会使用配置的 endpoint 主机名（Docker 内部地址 `minio:9000`）构建签名。签名计算时 `X-Amz-SignedHeaders=host` 表示 **HTTP Host 请求头被纳入了哈希**。

前端 `toExternalUrl()` 将 `http://minio:9000` 替换为 `http://localhost:19000` 后：

- 浏览器发出请求时 `Host: localhost:19000`
- 签名预期的 `Host: minio:9000`
- 两者不一致 → MinIO 签名校验失败 → 403

## 调用链

```
MinioClient.getPresignedObjectUrl()
  → endpoint = "http://minio:9000"（Docker 内部地址）
  → 签名覆盖: Host 头 = "minio:9000"

浏览器 fetch("http://localhost:19000/uploads/...")
  → Host 头 = "localhost:19000"
  → 签名校验失败 ❌
```

## 解决方案

### 前端（Vite 代理）

不直接改写主机名，而是将 MinIO URL 转换为同源相对路径，通过 Vite 代理转发到 MinIO，并在代理层强制正确的 Host 头。

**vite.config.ts：**

```typescript
proxy: {
  '/minio': {
    target: 'http://localhost:19000',
    changeOrigin: true,
    rewrite: (path) => path.replace(/^\/minio/, ''),
    configure: (proxy) => {
      proxy.on('proxyReq', (proxyReq) => {
        proxyReq.setHeader('Host', 'minio:9000');
      });
    },
  },
}
```

**toExternalUrl：**

```typescript
// 替换：将 minio 内部地址改为代理路径
url.replace('http://minio:9000', '/minio')
```

### 请求链路（修复后）

```
浏览器 fetch("/minio/uploads/...")
  → Vite 代理接收，路径重写去掉 /minio 前缀
  → 转发到 localhost:19000，Host 头设置为 minio:9000
  → MinIO 校验签名：Host 头匹配 ✅
```

## 涉及文件

- `front/vite.config.ts` — 添加 `/minio` 代理规则
- `front/src/api/index.ts` — `toExternalUrl()` 改为代理路径

## 适用范围

所有 MinIO 预签名 URL 的场景（PUT 上传、GET 图片加载），只要签名中包含 `host` header 均受影响。

## 生产环境注意

生产部署时，前端不通过 Vite 代理。应在反向代理层（Nginx）配置等效的代理规则，或让后端 MinIO Client 使用外部可达的 endpoint（如 `MINIO_SERVER_URL` 环境变量）生成预签名 URL。
