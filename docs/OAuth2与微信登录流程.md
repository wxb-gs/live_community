# OAuth2 概念与微信登录流程

## 目录

- [1. OAuth2 概述](#1-oauth2-概述)
- [2. OAuth2 四大角色](#2-oauth2-四大角色)
- [3. OAuth2 四种授权模式](#3-oauth2-四种授权模式)
- [4. 授权码模式详解](#4-授权码模式详解)
- [5. 微信 OAuth2 登录流程](#5-微信-oauth2-登录流程)
- [6. 本项目实现](#6-本项目实现)
- [7. 配置指南](#7-配置指南)

---

## 1. OAuth2 概述

**OAuth2**（Open Authorization 2.0）是一个开放授权协议，允许用户让第三方应用**在不知道密码的情况下**访问其在某个服务提供商上的受保护资源。

核心思想：用户不把密码交给第三方应用，而是通过"授权令牌"让第三方应用以用户名义访问资源。

```
传统方式:  用户 → 密码 → 第三方应用 → 资源服务器  (不安全，密码泄露风险)

OAuth2:   用户 → 授权服务器 → 授权码/令牌 → 第三方应用 → 资源服务器  (安全，密码不离开授权服务器)
```

## 2. OAuth2 四大角色

| 角色 | 说明 | 示例 |
|------|------|------|
| **Resource Owner**（资源所有者） | 用户本人，拥有数据的人 | 用户 |
| **Client**（客户端） | 请求访问资源的第三方应用 | 我们的 Live Community |
| **Authorization Server**（授权服务器） | 验证用户身份并签发令牌 | 微信开放平台 / Google Auth |
| **Resource Server**（资源服务器） | 存储用户数据的服务器 | 微信用户信息接口 |

```
┌──────────┐       ┌──────────────┐       ┌──────────────┐
│ Resource │       │ Authorization│       │   Resource   │
│  Owner   │       │    Server    │       │    Server    │
│  (用户)   │       │ (微信开放平台) │       │  (微信API)    │
└────┬─────┘       └──────┬───────┘       └──────┬───────┘
     │                    │                      │
     │  ① 发起登录         │                      │
     │────────────────────▶│                      │
     │                    │                      │
     │  ② 返回授权页面      │                      │
     │◀────────────────────│                      │
     │                    │                      │
     │  ③ 用户同意授权      │                      │
     │────────────────────▶│                      │
     │                    │                      │
     │  ④ 颁发授权码(code)  │                      │
     │◀────────────────────│                      │
     │                    │                      │
     │    ┌─────────────────────────────┐         │
     │    │  Client (Live Community)     │         │
     │    └─────────────────────────────┘         │
     │                    │                      │
     │  ⑤ 用授权码换AccessToken│                  │
     │                    │──────────────────────▶│
     │                    │       ⑥ 返回令牌       │
     │                    │◀──────────────────────│
     │                    │                      │
     │                    │  ⑦ 用令牌访问受保护资源   │
     │                    │──────────────────────────────▶│
     │                    │       ⑧ 返回资源                  │
     │                    │◀──────────────────────────────│
```

## 3. OAuth2 四种授权模式

### 3.1 授权码模式（Authorization Code）

**最安全、最常用**。适用于有后端服务器的应用。

```
流程: 用户 → 授权码(code) → 后端用code+secret换令牌 → 访问资源
```

- 优点：令牌不经过浏览器，安全性最高
- 适用：Web 应用、移动应用

### 3.2 隐式授权模式（Implicit）

已废弃。令牌直接返回给浏览器。

- 优点：简单，无需后端
- 缺点：令牌暴露在浏览器 URL 中，不安全
- 适用：纯前端应用（不推荐，已被 PKCE 替代）

### 3.3 密码模式（Resource Owner Password）

用户直接把用户名密码交给客户端。

- 一般不用：违背 OAuth2 初衷

### 3.4 客户端凭证模式（Client Credentials）

应用以自己的名义访问资源，不涉及用户。

- 适用：机器对机器通信、微服务间认证

### 模式对比

| 模式 | 安全性 | 是否需要后端 | 典型场景 |
|------|--------|------------|----------|
| 授权码模式 | 最高 | 是 | Web 应用、移动 App |
| PKCE 增强 | 最高 | 否 | SPA、原生 App |
| 密码模式 | 低 | — | 遗留系统 |
| 客户端凭证 | — | 是 | 服务间通信 |

## 4. 授权码模式详解

这是本项目微信登录使用的模式。

```
浏览器/APP                              后端服务                           授权服务器(微信)
    │                                     │                                    │
    │  ① GET /api/auth/wechat/url        │                                    │
    │────────────────────────────────────▶│                                    │
    │                                     │                                    │
    │  ② 返回微信授权URL                   │                                    │
    │  https://open.weixin.qq.com/       │                                    │
    │  connect/qrconnect?                │                                    │
    │  appid=xxx&redirect_uri=xxx       │                                    │
    │◀────────────────────────────────────│                                    │
    │                                     │                                    │
    │  ③ 浏览器跳转到微信授权页面            │                                    │
    │─────────────────────────────────────────────────────────────────────────▶│
    │                                     │                                    │
    │  ④ 用户扫码/确认授权                  │        授权页面展示给用户              │
    │  (用户在微信授权页操作)               │        "Live Community 请求获取你的    │
    │                                     │         公开信息"                    │
    │                                     │        [取消] [确认授权]              │
    │─────────────────────────────────────────────────────────────────────────▶│
    │                                     │                                    │
    │  ⑤ 微信回调 redirect_uri            │                                    │
    │  /wechat/callback?code=AUTH_CODE   │                                    │
    │◀─────────────────────────────────────────────────────────────────────────│
    │                                     │                                    │
    │  ⑥ POST /api/auth/wechat/login     │                                    │
    │     { code: "AUTH_CODE" }          │                                    │
    │────────────────────────────────────▶│                                    │
    │                                     │                                    │
    │                                     │  ⑦ 用 code 换取 access_token       │
    │                                     │  POST api.weixin.qq.com/sns/      │
    │                                     │  oauth2/access_token              │
    │                                     │  {appid, secret, code, grant_type}│
    │                                     │───────────────────────────────────▶│
    │                                     │                                    │
    │                                     │  ⑧ 返回 access_token + openid     │
    │                                     │◀───────────────────────────────────│
    │                                     │                                    │
    │                                     │  ⑨ 用 access_token 获取用户信息     │
    │                                     │  GET api.weixin.qq.com/sns/       │
    │                                     │  userinfo?access_token=&openid=   │
    │                                     │───────────────────────────────────▶│
    │                                     │                                    │
    │                                     │  ⑩ 返回用户信息                     │
    │                                     │  {openid, nickname, headimgurl}   │
    │                                     │◀───────────────────────────────────│
    │                                     │                                    │
    │                                     │  ⑪ 创建/查找用户，签发JWT           │
    │                                     │  (openid → user_id → JWT)         │
    │                                     │                                    │
    │  ⑫ 返回 JWT tokens                  │                                    │
    │  {accessToken, refreshToken, user} │                                    │
    │◀────────────────────────────────────│                                    │
    │                                     │                                    │
    │  ⑬ 存储token，跳转到首页              │                                    │
    │                                     │                                    │
```

**关键设计点：**

1. **code 是一次性的**：用过即失效，防止重放攻击
2. **code 通过前端传，token 不经过前端**：后端用 app_secret 换 token，密钥不暴露
3. **state 参数**（可选）：防 CSRF 攻击，验证回调请求来自我们的应用

## 5. 微信 OAuth2 登录流程

微信提供两套 OAuth2 体系：

| 体系 | 授权URL域名 | 用途 | appid 来源 |
|------|-----------|------|-----------|
| 微信开放平台 (Open Platform) | `open.weixin.qq.com` | 网站扫码登录、移动应用 | 开放平台 AppID |
| 微信公众号 (Official Account) | `open.weixin.qq.com` | 公众号内网页授权 | 公众号 AppID |

**本项目使用微信开放平台（Open Platform）**，通过 `snsapi_login` scope 实现网站扫码登录。

### 5.1 微信开放平台申请流程

```
1. 注册账号      → https://open.weixin.qq.com
2. 开发者认证     → 提交企业/个人资料，缴纳认证费
3. 创建网站应用   → 填写应用名、图标、回调域名
4. 审核通过      → 获得 AppID 和 AppSecret
5. 配置回调域名   → 在应用详情中设置授权回调域
```

### 5.2 微信授权接口

**① 获取授权URL（前端跳转）**

```
GET https://open.weixin.qq.com/connect/qrconnect
  ?appid=APPID
  &redirect_uri=REDIRECT_URI      ← 授权后回调地址（需URL编码）
  &response_type=code              ← 固定值
  &scope=snsapi_login              ← 网站登录固定值
  &state=STATE                     ← 防CSRF的随机串（可选）
  #wechat_redirect                 ← 固定锚点（微信JS会读取）
```

**② 用 code 换 access_token（后端调用）**

```
GET https://api.weixin.qq.com/sns/oauth2/access_token
  ?appid=APPID
  &secret=APPSECRET
  &code=CODE                       ← 上一步获得的授权码
  &grant_type=authorization_code   ← 固定值

返回:
{
  "access_token": "ACCESS_TOKEN",
  "expires_in": 7200,
  "refresh_token": "REFRESH_TOKEN",
  "openid": "OPENID",
  "unionid": "UNIONID"             ← 跨应用统一用户标识（需绑定开放平台）
}
```

**③ 获取用户信息（后端调用）**

```
GET https://api.weixin.qq.com/sns/userinfo
  ?access_token=ACCESS_TOKEN
  &openid=OPENID

返回:
{
  "openid": "OPENID",
  "nickname": "用户昵称",
  "sex": 1,
  "headimgurl": "https://thirdwx.qlogo.cn/...",
  "unionid": "UNIONID"
}
```

### 5.3 核心概念

| 概念 | 说明 |
|------|------|
| **openid** | 用户在同一应用下的唯一标识。不同应用的 openid 不同 |
| **unionid** | 用户在同一开放平台账号下的唯一标识。跨应用统一 |
| **access_token** | 微信 API 调用凭证，有效期 2 小时 |
| **refresh_token** | 用于刷新 access_token，有效期 30 天 |
| **scope** | 授权范围。`snsapi_login` = 网站登录，可获取用户信息 |

### 5.4 与标准 OAuth2 的对应关系

| 标准 OAuth2 | 微信实现 |
|------------|---------|
| Authorization Server | `open.weixin.qq.com` |
| Resource Server | `api.weixin.qq.com` |
| Authorization Code | URL 参数 `code` |
| Access Token | 微信的 `access_token` |
| Resource Owner | 微信用户 |
| Client | 我们的应用（Live Community） |

## 6. 本项目实现

### 6.1 架构概览

```
┌───────────────────────────────────────────────────────────────┐
│                         浏览器                                  │
│  ┌─────────┐    ┌───────────────┐    ┌───────────────────────┐ │
│  │ LoginPage│    │WechatCallback │    │      其他页面           │ │
│  │ 点击微信  │───▶│ 接收code      │───▶│  (携带JWT Bearer Token)│ │
│  │ 登录按钮  │    │ 换取JWT       │    │                       │ │
│  └─────────┘    └───────────────┘    └───────────────────────┘ │
└──────┬──────────────────┬──────────────────────┬───────────────┘
       │                  │                      │
       ▼                  ▼                      ▼
┌──────────────────────────────────────────────────────────────┐
│                     Gateway (:8080)                           │
│  ┌─────────────────┐  ┌──────────────────────────────────┐   │
│  │ AuthProxyController│  │ SpaResourceConfig              │   │
│  │ /api/auth/** →    │  │ 前端SPA路由fallback到index.html  │   │
│  │   auth-service    │  │                                │   │
│  └─────────────────┘  └──────────────────────────────────┘   │
└──────────────┬───────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────────────┐
│                  Auth Service (:8084)                         │
│  ┌───────────────┐  ┌────────────────┐  ┌─────────────────┐  │
│  │ AuthController │  │WechatAuthService│  │  TokenService   │  │
│  │               │  │                │  │                 │  │
│  │ /register     │  │ buildAuthUrl() │  │ issueTokens()   │  │
│  │ /login        │  │ fetchUserInfo()│  │ validateToken() │  │
│  │ /wechat/login │  │ registerUser() │  │ revokeToken()   │  │
│  │ /wechat/url   │  │                │  │                 │  │
│  │ /refresh      │  │                │  │                 │  │
│  │ /logout       │  │                │  │                 │  │
│  │ /me           │  │                │  │                 │  │
│  └───────────────┘  └────────────────┘  └─────────────────┘  │
│                                                              │
│  数据存储:                                                    │
│  ┌────────────────┐  ┌──────────────────┐  ┌──────────────┐ │
│  │ user_info (MySQL)│  │ user_wechat (MySQL)│  │  Redis       │ │
│  │ - user_id       │  │ - wechat_id       │  │  refresh_token│ │
│  │ - username      │  │ - user_id         │  │  sessions     │ │
│  │ - password      │  │ - openid          │  │              │ │
│  │ - nickname      │  │ - unionid         │  │              │ │
│  │ - avatar        │  │ - nickname        │  │              │ │
│  │ - status        │  │ - avatar          │  │              │ │
│  └────────────────┘  └──────────────────┘  └──────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 关键代码路径

| 文件 | 作用 |
|------|------|
| `front/src/pages/LoginPage.tsx` | 微信登录按钮，点击后获取授权URL并跳转 |
| `front/src/pages/WechatCallbackPage.tsx` | 接收微信回调，提取code，调API换取JWT |
| `front/src/api/index.ts` | `getWechatAuthUrl()`, `wechatLogin()` API封装 |
| `gateway/.../AuthProxyController.java` | 代理 `/api/auth/**` 到 auth-service |
| `auth-service/.../AuthController.java` | `/api/auth/wechat/url`、`/api/auth/wechat/login` |
| `auth-service/.../WechatAuthService.java` | 构建授权URL、换取token、获取用户信息、自动注册 |
| `auth-service/.../WechatAuthenticationProvider.java` | Spring Security provider，验证微信code |
| `auth-service/.../WechatCodeAuthenticationToken.java` | 封装微信code的认证令牌 |
| `auth-service/.../TokenService.java` | JWT签发、刷新、吊销 |

### 6.3 前端路由设计

| 路由 | 页面 | 是否需要登录 | 说明 |
|------|------|------------|------|
| `/login` | LoginPage | 否 | 用户名密码登录 + 微信登录入口 |
| `/wechat/callback` | WechatCallbackPage | 否 | 微信OAuth2回调页面，提取code |
| `/` | FeedPage | 是 | 首页笔记流 |
| `/publish` | PublishPage | 是 | 发布笔记 |
| `/note/:noteId` | NoteDetailPage | 是 | 笔记详情 |
| `/profile` | ProfilePage | 是 | 个人中心 |
| `/messages` | 占位 | 是 | 消息中心（规划中） |

### 6.4 数据库表结构

```sql
-- 用户主表
CREATE TABLE user_info (
    user_id   BIGINT      PRIMARY KEY,
    username  VARCHAR(64) UNIQUE,         -- 微信用户为NULL
    password  VARCHAR(256),               -- 微信用户为NULL
    nickname  VARCHAR(128),
    avatar    VARCHAR(512),
    status    VARCHAR(16) DEFAULT 'ACTIVE',
    created_at BIGINT, updated_at BIGINT
);

-- 微信关联表
CREATE TABLE user_wechat (
    wechat_id  BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    openid     VARCHAR(128) NOT NULL,      -- 微信用户唯一标识
    unionid    VARCHAR(128),               -- 跨应用统一标识
    nickname   VARCHAR(128),               -- 微信昵称快照
    avatar     VARCHAR(512),               -- 微信头像快照
    created_at BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user_info(user_id)
);
```

### 6.5 安全性考虑

| 措施 | 实现 |
|------|------|
| **code 一次性** | 微信保证 code 使用一次后失效 |
| **后端换 token** | `app_secret` 仅在后端使用，不暴露给前端 |
| **JWT 短有效期** | access_token 15分钟，refresh_token 7天 |
| **refresh_token 吊销** | 存储在 Redis，可在服务端主动吊销 |
| **防 CSRF** | 可为 state 参数添加随机值并验证 |
| **HTTPS** | 生产环境必须使用 HTTPS，防止中间人攻击 |
| **PKCE 可升级** | 当前 SPA 方案可用 PKCE 进一步增强 |

## 7. 配置指南

### 7.1 微信开放平台配置

1. 访问 [微信开放平台](https://open.weixin.qq.com)
2. 注册并完成开发者认证
3. 创建「网站应用」
4. 在应用详情中设置 **授权回调域**：

```
例如: localhost  (开发环境)
     your-domain.com  (生产环境)
```

**注意**：回调域名不需要加 `http://` 或路径，只需填写域名部分。微信会验证回调URL的域名是否在授权回调域中。

### 7.2 应用配置

**application.yml（auth-service）：**

```yaml
wechat:
  app-id: "wx1234567890abcdef"      # 微信开放平台 AppID
  app-secret: "abc123def456..."     # 微信开放平台 AppSecret

jwt:
  secret: "your-secure-jwt-secret"  # JWT 签名密钥
```

**Docker Compose 环境变量：**

```bash
WECHAT_APP_ID=wx1234567890abcdef \
WECHAT_APP_SECRET=abc123def456... \
docker compose up -d
```

### 7.3 开发环境调试

开发时前端运行在 `localhost:5173`（Vite dev server），回调 URL 自动使用 `window.location.origin + '/wechat/callback'`：

```
回调URL: http://localhost:5173/wechat/callback
```

生产环境如果通过 Gateway 提供前端静态资源，则是：

```
回调URL: https://your-domain.com/wechat/callback
```

### 7.4 测试流程

```bash
# 1. 确保所有服务启动
docker compose up -d

# 2. 确认 auth-service 配置了有效的 wechat.app-id
docker compose logs auth-service | grep -i wechat

# 3. 打开浏览器访问 http://localhost:5173/login

# 4. 点击"微信登录"按钮

# 5. 在微信授权页扫码确认

# 6. 自动回调到 /wechat/callback，完成登录，跳转首页
```

---

## 参考资料

- [OAuth 2.0 规范 (RFC 6749)](https://datatracker.ietf.org/doc/html/rfc6749)
- [微信开放平台 - 网站应用开发指南](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/Wechat_Login.html)
- [OAuth 2.0 for Browser-Based Apps (Best Current Practice)](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps)
- [PKCE (RFC 7636)](https://datatracker.ietf.org/doc/html/rfc7636) — 推荐用于 SPA 的安全增强
