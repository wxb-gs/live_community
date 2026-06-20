# Auth Service 鉴权服务设计文档

> **日期：** 2026-06-20
> **目标：** 基于 Spring Security + JWT 实现网关层统一鉴权，双 Token 机制，支持微信 OAuth 2.0 登录（客户端角色）和普通注册登录
> **范围：** 新建 auth-service 微服务 + gateway 改造 + common 扩展 + 前端鉴权适配

---

## 架构总览

```
                       ┌──────────────────────────────────┐
                       │          Gateway (:8080)          │
                       │  Spring Security FilterChain     │
                       │  - SecurityFilterChain 管理        │
                       │  - JwtAuthFilter 挂入 Security 链   │
                       │  - /api/auth/** → permitAll()      │
                       │  - 其他 /api/** → authenticated()   │
                       │  - 无状态 Session, 不依赖 Redis      │
                       └────────┬─────────────────────────┘
                                │  Dubbo Triple / HTTP
        ┌───────────────────────┼───────────────────────┐
        ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  auth-service   │    │  note-service   │    │  upload-service │
│  (:8084/20884)  │    │  (:8083/20882)  │    │  (:8081/20880)  │
│                 │    │  (Cassandra)    │    │  (MinIO)        │
│  Spring Security│    │  (不变)          │    │  (不变)          │
│  - AuthManager  │    └─────────────────┘    └─────────────────┘
│  - Provider     │
│  - 注册/登录     │
│  - Token 签发   │
│  - Token 刷新   │
│  - 微信 OAuth   │
│  - 用户 CRUD    │
└───────┬─────────┘
        │
┌───────┴──────────┐
│  MySQL (复用现有)  │  导出 3306 端口
│  - user_info      │
│  - user_wechat    │
└──────────────────┘
│
┌───────┴──────────┐
│  Redis (新增)     │  导出 6379 端口
│  - refresh:*      │
│  - user_sessions:*│
└──────────────────┘
```

**核心原则：**
- Gateway 只做无状态 JWT 验签（HMAC 密钥），不调 Redis，不调任何 Dubbo 服务
- auth-service 独享 MySQL 和 Redis，承担所有有状态操作
- 前端不可信 —— userId 由 Gateway 从 JWT 提取后注入到下游请求

---

## Token 设计

### Access Token

| 属性 | 值 |
|------|-----|
| 格式 | JWT（HMAC-SHA256） |
| 有效期 | 15 分钟 |
| 存储 | 无状态，不存 Redis |
| Payload | `{ sub: userId, username, nickname, avatar, iat, exp }` |

### Refresh Token

| 属性 | 值 |
|------|-----|
| 格式 | 不透明 UUID 字符串 |
| 有效期 | 7 天 |
| 存储 | Redis：`refresh:{tokenId}` → userId，TTL 604800s |
| 特点 | 不含用户信息，泄露不暴露数据 |

### 轮转机制（Rotation）

每次 `POST /api/auth/refresh` 调用：
1. 旧 RefreshToken 从 Redis **立即删除**
2. 签发新 AccessToken + 新 RefreshToken
3. 旧 token 被窃取 → 合法用户下次刷新时旧 token 失效 → 攻击者被踢出

### 吊销策略

| 场景 | 机制 |
|------|------|
| 用户主动登出 | 删除 `refresh:{tokenId}` + 从 `user_sessions:{userId}` 中移除 |
| 管理员禁用用户 | 删除 `user_sessions:{userId}` 下所有 tokenId |
| 检测到 token 重放 | 同一 tokenId 被用两次 → 删除该用户所有 session |
| 正常过期 | AccessToken 15 min 自然过期，RefreshToken 7 天 Redis TTL 自动清除 |

---

## API 设计

### 端点列表

```
POST  /api/auth/register       — 用户名+密码注册
POST  /api/auth/login           — 用户名+密码登录
GET   /api/auth/wechat/url      — 获取微信 OAuth 授权 URL
POST  /api/auth/wechat/login    — 微信 code 换 JWT（自动注册）
POST  /api/auth/refresh         — RefreshToken 换新 Token 对
POST  /api/auth/logout          — 吊销 RefreshToken
GET   /api/auth/me              — 从 JWT 获取当前用户信息
```

### 请求/响应格式

**注册**：`POST /api/auth/register`
```json
// Request
{ "username": "foo", "password": "123456", "nickname": "小Foo" }

// Response (201)
{ "code": 200, "data": {
    "accessToken": "eyJhbG...",
    "refreshToken": "a1b2c3d4-e5f6-...",
    "expiresIn": 900,
    "user": { "userId": 2001, "username": "foo", "nickname": "小Foo", "avatar": null }
}}
```

**登录**：`POST /api/auth/login`
```json
// Request
{ "username": "foo", "password": "123456" }

// Response (200)
{ "code": 200, "data": { "accessToken": "...", "refreshToken": "...", "expiresIn": 900, "user": {...} }}
```

**微信登录**：`POST /api/auth/wechat/login`
```json
// Request
{ "code": "081abc..." }

// Response（已注册用户 / 自动注册新用户，格式同上）
{ "code": 200, "data": { "accessToken": "...", "refreshToken": "...", "user": {...} }}
```

**刷新 Token**：`POST /api/auth/refresh`
```json
// Request
{ "refreshToken": "a1b2c3d4-e5f6-..." }

// Response（新 Token 对，旧 RefreshToken 立即失效）
{ "code": 200, "data": { "accessToken": "...", "refreshToken": "...", "expiresIn": 900 }}
```

**登出**：`POST /api/auth/logout`
```json
// Request
{ "refreshToken": "a1b2c3d4-e5f6-..." }

// Response
{ "code": 200, "msg": "ok" }
```

---

## 微信 OAuth 流程

```
1. 前端 GET /api/auth/wechat/url?redirectUri=https://example.com/callback
2. auth-service 返回微信授权 URL
3. 前端 redirect 到微信授权页（用户扫码确认）
4. 微信 redirect → redirectUri?code=xxx（回到前端回调页面）
5. 前端拿到 code → POST /api/auth/wechat/login { code }
6. auth-service 处理：
   ├─ code → 微信 /sns/oauth2/access_token → openid + access_token
   ├─ access_token → 微信 /sns/userinfo → 昵称、头像、unionid
   ├─ openid 查 user_wechat 表：
   │   ├─ 找到 → UserRepository.findById(userId) → 走登录
   │   └─ 没找到 → LeafRpcService.generateSegmentId("user")
   │             → UserRepository.insert(userId, username=null, password=null, ...)
   │             → UserWechatRepository.insert(wechatId, userId, openid, ...)
   ├─ TokenService.issueTokens(userId)
   └─ 返回 LoginResponse
```

---

## 数据模型

### MySQL 表（数据库 `live_community`）

**user_info 表：**

```sql
CREATE TABLE user_info (
    user_id   BIGINT      PRIMARY KEY,
    username  VARCHAR(64) UNIQUE,
    password  VARCHAR(256),               -- BCrypt hash, 微信用户可为 NULL
    nickname  VARCHAR(128),
    avatar    VARCHAR(512),
    status    VARCHAR(16) DEFAULT 'ACTIVE', -- ACTIVE / DISABLED
    created_at BIGINT     NOT NULL,
    updated_at BIGINT     NOT NULL
);
```

**user_wechat 表：**

```sql
CREATE TABLE user_wechat (
    wechat_id       BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    openid          VARCHAR(128) NOT NULL,
    unionid         VARCHAR(128),
    nickname        VARCHAR(128),
    avatar          VARCHAR(512),
    created_at      BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user_info(user_id)
);
CREATE UNIQUE INDEX idx_user_wechat_openid ON user_wechat(openid);
CREATE INDEX idx_user_wechat_userid ON user_wechat(user_id);
```

### Redis 键设计

| Key | 类型 | Value | TTL | 说明 |
|-----|------|-------|-----|------|
| `refresh:{tokenId}` | String | userId | 604800s (7d) | 有效的 RefreshToken |
| `user_sessions:{userId}` | Set | tokenId 集合 | 604800s (7d) | 按用户聚合，用于强制踢出 |

---

## auth-service 模块结构

```
auth-service/src/main/java/com/example/auth/
├── AuthServiceApplication.java          # @SpringBootApplication + @EnableDubbo
├── config/
│   ├── AuthSecurityConfig.java          # SecurityFilterChain + AuthManager Bean
│   ├── GatewaySecurityConfig.java       # (同模板) JwtAuthFilter 复用
│   ├── SecurityBeansConfig.java        # BCryptPasswordEncoder + SecretKey Bean
│   ├── RedisConfig.java                # RedisTemplate 序列化配置
│   └── WechatConfig.java               # 微信 appId/secret @ConfigurationProperties
├── security/
│   ├── JwtAuthFilter.java              # OncePerRequestFilter（gateway 同款）
│   ├── JwtAuthenticationToken.java      # 自定义 Authentication
│   ├── WechatCodeAuthenticationToken.java
│   ├── DaoAuthenticationProvider.java
│   └── WechatAuthenticationProvider.java
├── entity/
│   ├── UserEntity.java                 # user_info 表映射
│   └── UserWechatEntity.java           # user_wechat 表映射
├── repository/
│   ├── UserRepository.java             # JdbcTemplate 实现 CRUD
│   └── UserWechatRepository.java       # JdbcTemplate 实现 CRUD
├── rpc/
│   └── AuthRpcServiceImpl.java         # @DubboService implements AuthRpcService
├── service/
│   ├── AuthService.java                # 注册/登录/刷新/登出 核心业务
│   ├── WechatAuthService.java          # 微信 OAuth 对接 + 自动注册
│   └── TokenService.java               # JWT 签发/验签 + RefreshToken 管理
├── controller/
│   └── AuthController.java             # REST 端点（薄层，委托 AuthManager）
└── dto/
    ├── RegisterRequest.java
    ├── LoginRequest.java
    ├── WechatLoginRequest.java
    ├── RefreshRequest.java
    └── WechatUserInfo.java             # 微信返回的用户信息 DTO
```

---

## common 模块扩展

Gateway 透明透传 `/api/auth/**` 到 auth-service，不需要解析认证响应，因此 **common 模块不需要新增 DTO**。

Gateway 和 auth-service **共享 JWT HMAC 密钥**（通过各自的 `jwt.secret` 配置，值相同）。这是唯一的共享知识。

如需跨服务获取用户信息（如 note-service 需要验证用户状态），可通过 Dubbo RPC 调用 auth-service 的 `AuthRpcService.getUserById(userId)`。此接口为预留设计。

---

## Spring Security 集成

### 两层 Security 职责划分

| 层 | 职责 | 配置 |
|----|------|------|
| Gateway | JWT 验签 + 权限匹配，不做认证 | `SecurityFilterChain` + `JwtAuthFilter` |
| auth-service | 身份认证 + Token 签发，自身也验签 | `SecurityFilterChain` + `AuthenticationManager` + `Provider` |

---

## Gateway Security 配置

### SecurityFilterChain

```java
@Configuration
@EnableWebSecurity
public class GatewaySecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/upload/presigned").authenticated()
                .requestMatchers("/api/note/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### JwtAuthFilter

```java
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String secret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            sendUnauthorized(response, "缺少 Token");
            return;
        }

        try {
            String token = header.substring(7);
            Claims claims = Jwts.parser()
                    .verifyWith(HMAC_KEY)           // SecretKey 单例
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = claims.get("sub", Long.class);
            String username = claims.get("username", String.class);

            // 构造 Authentication 放入 SecurityContext
            JwtAuthenticationToken auth =
                    new JwtAuthenticationToken(userId, username, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            chain.doFilter(request, response);
        } catch (JwtException e) {
            sendUnauthorized(response, "Token 无效或已过期");
        }
    }
}
```

### 自定义 Authentication Token

```java
public class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private final Long userId;
    private final String username;

    public JwtAuthenticationToken(Long userId, String username,
                                   Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.userId = userId;
        this.username = username;
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal() { return userId; }

    public String getUsername() { return username; }
}
```

### Controller 使用 @AuthenticationPrincipal

```java
@RestController
@RequestMapping("/api/note")
public class NoteController {

    @GetMapping("/list")
    public Result<?> listNotes(@AuthenticationPrincipal Long userId,
                                int page, int size) {
        // Spring Security 自动从 SecurityContext 注入 userId
        return Result.ok(noteRpcService.listPublishedNotes(page, size));
    }

    @PostMapping("/comment")
    public Result<?> addComment(@AuthenticationPrincipal Long userId,
                                 @RequestBody CommentRequest req) {
        req.setUserId(userId);  // 覆盖前端传入的 userId，保证不可篡改
        return Result.ok(noteRpcService.addComment(req));
    }
}
```

**关键安全保证**：Controller 从 `@AuthenticationPrincipal` 获取的 `userId` 是 Filter 从 JWT 签名中解析的，前端不可伪造。Controller 强制用这个值覆盖请求中的 `userId` 字段。

---

## auth-service Security 配置

### SecurityFilterChain

```java
@Configuration
@EnableWebSecurity
public class AuthSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register",
                                 "/api/auth/login",
                                 "/api/auth/wechat/**",
                                 "/api/auth/refresh").permitAll()
                .requestMatchers("/api/auth/logout",
                                 "/api/auth/me").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### AuthenticationManager 多 Provider 组合

```java
@Bean
AuthenticationManager authManager(
        DaoAuthenticationProvider daoProvider,
        WechatAuthenticationProvider wechatProvider) {
    return new ProviderManager(daoProvider, wechatProvider);
}
```

### DaoAuthenticationProvider（用户名密码）

```java
@Component
public class DaoAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication auth) {
        String username = auth.getName();
        String password = auth.getCredentials().toString();

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new DisabledException("账号已被禁用");
        }

        return new UsernamePasswordAuthenticationToken(
                user.getUserId(), null, Collections.emptyList());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class
                .isAssignableFrom(authentication);
    }
}
```

### WechatAuthenticationProvider（微信登录）

```java
@Component
public class WechatAuthenticationProvider implements AuthenticationProvider {

    private final WechatAuthService wechatAuthService;

    @Override
    public Authentication authenticate(Authentication auth) {
        String code = auth.getCredentials().toString();
        UserEntity user = wechatAuthService.authenticateAndRegister(code);
        return new UsernamePasswordAuthenticationToken(
                user.getUserId(), null, Collections.emptyList());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return WechatCodeAuthenticationToken.class
                .isAssignableFrom(authentication);
    }
}
```

### 自定义微信认证 Token 类型

```java
public class WechatCodeAuthenticationToken extends AbstractAuthenticationToken {
    private final String code;

    public WechatCodeAuthenticationToken(String code) {
        super(Collections.emptyList());
        this.code = code;
    }

    @Override public Object getCredentials() { return code; }
    @Override public Object getPrincipal() { return null; }
}
```

### AuthController 简化

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final TokenService tokenService;
    private final RedisTemplate<String, String> redis;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req) {
        // ProviderManager 自动路由到 DaoAuthenticationProvider
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        Long userId = (Long) auth.getPrincipal();
        return Result.ok(tokenService.issueTokens(userId));
    }

    @PostMapping("/wechat/login")
    public Result<LoginResponse> wechatLogin(@RequestBody WechatLoginRequest req) {
        // ProviderManager 自动路由到 WechatAuthenticationProvider
        Authentication auth = authManager.authenticate(
                new WechatCodeAuthenticationToken(req.getCode()));
        Long userId = (Long) auth.getPrincipal();
        return Result.ok(tokenService.issueTokens(userId));
    }
}
```

**优势**：Controller 不再包含认证逻辑，只负责调用 `AuthenticationManager`。新增认证方式只需加一个 `AuthenticationProvider`。

---

## Gateway 依赖新增

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
```

---

## 前端改造

### 移除内容

- `front/src/config.ts` 中 `MOCK_USER` 移除

### 新增内容

| 文件 | 说明 |
|------|------|
| `src/pages/LoginPage.tsx` | 登录/注册页面（Tabs 切换） |
| `src/pages/WechatCallbackPage.tsx` | 微信授权回调页（提取 code 调 API） |
| `src/api/index.ts` 改造 | `request()` 自动加 `Authorization: Bearer {token}` 头 |
| `src/App.tsx` | 添加 `/login` 路由，未登录时重定向 |
| `src/utils/TokenStore.ts` | localStorage 封装：`{ getAccessToken, getRefreshToken, saveTokens, clearTokens }` |

### Token 存储

```typescript
// TokenStore.ts
const TOKEN_KEY = 'auth_tokens';

interface TokenData {
  accessToken: string;
  refreshToken: string;
  expiresAt: number; // Date.now() + expiresIn * 1000
}

export function saveTokens(data: TokenData): void { ... }
export function getAccessToken(): string | null { ... }
export function getRefreshToken(): string | null { ... }
export function clearTokens(): void { ... }
```

### 请求拦截

```typescript
// api/index.ts request() 函数改造
async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getAccessToken();
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  const res = await fetch(`${API_BASE}${path}`, { headers, ...options });
  if (res.status === 401) {
    clearTokens();
    window.location.href = '/login';
    throw new Error('登录已过期');
  }
  // ... 其余不变
}
```

---

## Docker Compose 新增

```yaml
redis:
  image: redis:7-alpine
  ports:
    - "6379:6379"
  networks:
    - live-community

auth-service:
  build: ./auth-service
  ports:
    - "8084:8084"
    - "20884:20884"
  depends_on:
    - nacos
    - mysql
    - redis
  environment:
    - DUBBO_REGISTRY_ADDRESS=nacos://nacos:8848
    - DUBBO_PROTOCOL_PORT=20884
    - SPRING_REDIS_HOST=redis
    - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/live_community
    - WECHAT_APP_ID=${WECHAT_APP_ID}
    - WECHAT_APP_SECRET=${WECHAT_APP_SECRET}
  networks:
    - live-community
```

---

## 调用流总结

### 用户登录后，后续请求的完整链路

```
浏览器: fetch("/api/note/list", { headers: { Authorization: "Bearer eyJ..." } })
  → Gateway SecurityFilterChain: 匹配 /api/note/** → authenticated()
  → Gateway JwtAuthFilter (挂入 Security 链):
      HMAC 验签 ✓, 提取 sub=2001, username="foo"
      → SecurityContextHolder.setAuthentication(JwtAuthenticationToken)
  → Gateway NoteController:
      @AuthenticationPrincipal Long userId → 2001 (Security 自动注入)
      noteRpcService.listPublishedNotes(page, size)
  → note-service: 处理业务，返回数据
  → Gateway 返回 Result<NoteDetailResponse>
```

### 认证流程（用户名密码）

```
POST /api/auth/login { username, password }
  → Gateway: /api/auth/** → permitAll(), 透传
  → auth-service AuthSecurityConfig: /api/auth/login → permitAll()
  → AuthController.login():
      authManager.authenticate(UsernamePasswordAuthenticationToken)
        → DaoAuthenticationProvider.authenticate():
            UserRepository.findByUsername() → UserEntity
            BCrypt.matches(password, hash) ✓
            → 返回 UsernamePasswordAuthenticationToken(userId)
      tokenService.issueTokens(userId)
        → JWT: { sub: userId, username, nickname, avatar, exp: +15min }
        → UUID: refreshTokenId
        → Redis: SET refresh:{id} = userId EX 604800
        → Redis: SADD user_sessions:{userId} id
      → LoginResponse { accessToken, refreshToken, expiresIn, user }
```

### 认证流程（微信登录）

```
POST /api/auth/wechat/login { code }
  → Gateway: /api/auth/** → permitAll(), 透传
  → auth-service AuthSecurityConfig: /api/auth/wechat/** → permitAll()
  → AuthController.wechatLogin():
      authManager.authenticate(WechatCodeAuthenticationToken)
        → WechatAuthenticationProvider.authenticate():
            wechatAuthService.authenticateAndRegister(code)
              → 微信 API: code → openid + access_token
              → 微信 API: access_token → 用户信息 (昵称, 头像, unionid)
              → UserWechatRepository.findByOpenid(openid)
                ├─ 找到 → UserRepository.findById(userId)
                └─ 没找到 → LeafRpcService.genId("user")
                          → UserRepository.insert(userId, null, null, nickname, avatar)
                          → UserWechatRepository.insert(...)
            → 返回 UsernamePasswordAuthenticationToken(userId)
      tokenService.issueTokens(userId)
      → LoginResponse
```

### 前端的 Token 管理

```
1. 登录成功 → saveTokens({ accessToken, refreshToken, expiresIn })
2. 每次 API 调用 → request() 自动从 TokenStore 取 accessToken 加 Authorization 头
3. 收到 401 → 尝试用 refreshToken 调 POST /api/auth/refresh
   ├─ 成功 → saveTokens(新数据), 重试原请求
   └─ 失败 → clearTokens(), redirect /login
4. 定时检查 → accessToken 快过期前 2 分钟自动 refresh
```
