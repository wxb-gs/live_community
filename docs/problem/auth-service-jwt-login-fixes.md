# Auth Service JWT 校验与登录错误修复

## 问题 1：JWT Token 校验失败（401 "Token 无效或已过期"）

**现象：** 注册/登录返回的合法 Token，在访问受保护接口时被 JwtAuthFilter 拒绝，返回 401。

**根因：** 类型不匹配。`TokenService.issueAccessToken()` 将 `userId` 以 String 写入 JWT 的 `sub` 字段（`.subject(user.getUserId().toString())`），但 `JwtAuthFilter` 试图以 `Long.class` 读取：

```java
// TokenService.java — 写入为 String
.subject(user.getUserId().toString())

// JwtAuthFilter.java — 读取为 Long（修复前）
Long userId = claims.get("sub", Long.class);
```

jjwt 0.12.5 不会自动做 String → Long 转换，抛出 `RequiredTypeException`。异常在 `log.debug` 级别被捕获，线上无法看到错误信息。

**修复：** `JwtAuthFilter.java`（auth-service 和 gateway 两处），先读为 String 再解析：

```java
Long userId = Long.parseLong(claims.get("sub", String.class));
```

同时将日志级别从 `log.debug` 改为 `log.warn`，确保认证失败可追溯。

---

## 问题 2：登录失败返回 500 而非 401

**现象：** 用不存在的用户名或错误密码登录时，gateway 返回 500 Internal Server Error，而非提示"用户名或密码错误"。

**根因：** 三层问题叠加。

### 第一层：AuthController 不捕获 AuthenticationException

```java
// 修复前
@PostMapping("/login")
public Result<LoginResponse> login(@RequestBody LoginRequest req) {
    Authentication auth = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
    // ...
}
```

`authManager.authenticate()` 在凭证无效时抛出 `BadCredentialsException`（继承自 `AuthenticationException`），Controller 未捕获。Spring Security 的 `ExceptionTranslationFilter` 将其转换为 403 Forbidden 响应。

### 第二层：HttpURLConnection 自动重试导致 HttpRetryException

Spring Security 的 403/401 响应包含 `WWW-Authenticate` 头。Java 的 `HttpURLConnection`（gateway 的 `RestTemplate` 默认 HTTP 客户端）看到此头后，触发 HTTP 认证自动协商，试图重试请求。由于 POST 请求体已发送（streaming mode），无法重试，抛出 `HttpRetryException: cannot retry due to server authentication, in streaming mode`。gateway 的 `RestTemplate` 将此包装为 500。

尝试过 `BufferingClientHttpRequestFactory` 和 `sun.net.http.retryPost=false`，均无法修复 JDK 17 下的 `HttpURLConnection` 行为。

### 第三层：RestTemplate 默认对 4xx/5xx 抛异常

`RestTemplate` 的默认 `DefaultResponseErrorHandler` 对非 2xx 响应抛出 `HttpClientErrorException`，导致即便 auth-service 正确返回 401，gateway 也会将其包装成 500。

**修复：**

AuthController 捕获认证异常并返回 401：

```java
@PostMapping("/login")
public Result<LoginResponse> login(@RequestBody LoginRequest req) {
    Authentication auth;
    try {
        auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
    } catch (org.springframework.security.core.AuthenticationException e) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }
    // ...
}
```

Gateway 替换 HTTP 客户端为 Apache HttpClient，禁用自动重试，并透传所有状态码：

```java
// 使用 Apache HttpClient 替代 HttpURLConnection
CloseableHttpClient httpClient = HttpClientBuilder.create()
        .disableAuthCaching()
        .disableAutomaticRetries()
        .build();
this.restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));

// 透传 4xx/5xx 响应，不抛异常
this.restTemplate.setErrorHandler(new ResponseErrorHandler() {
    @Override
    public boolean hasError(ClientHttpResponse response) { return false; }
    @Override
    public void handleError(ClientHttpResponse response) {}
});
```

---

## 问题 3：Vite 代理 POST 请求返回 502

**现象：** 前端 `fetch()` POST 到 Vite 代理（`localhost:5173/api/auth/login`），返回 502 Bad Gateway，`res.json()` 报 "Unexpected end of JSON input"。

**根因：** Vite 内置的 `http-proxy` 无法正确解析 chunked transfer encoding 响应。gateway 通过 Spring MVC `ResponseEntity<String>` 返回响应时，Tomcat 使用 chunked 编码，导致 Vite 代理报 `Parse Error: Invalid character in chunk size`。

GET 请求不受影响（响应体通常较短，不走 chunked）。

**修复：** Gateway 的 `AuthProxyController` 改为直接写入 `HttpServletResponse`，显式设置 `Content-Length` 和 `Content-Type`，避免 chunked 编码：

```java
@RequestMapping("/**")
public void proxy(HttpServletRequest request, HttpServletResponse response,
                  @RequestBody(required = false) String body) {
    // ... 请求转发 ...
    String respBody = resp.getBody();
    if (respBody != null) {
        response.setContentType("application/json;charset=UTF-8");
        byte[] bytes = respBody.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);  // 显式设置长度，避免 chunked
        response.getOutputStream().write(bytes);
    }
}
```

---

## 关键文件变更

| 文件 | 变更 |
|------|------|
| `auth-service/.../security/JwtAuthFilter.java` | `sub` 读取改为 `String` → `Long.parseLong()`；日志 `debug` → `warn` |
| `auth-service/.../controller/AuthController.java` | `login()` 捕获 `AuthenticationException` 返回 401 |
| `gateway/.../security/JwtAuthFilter.java` | 同 auth-service 的 `sub` 修复 |
| `gateway/pom.xml` | 新增 `httpclient5` 依赖 |
| `gateway/.../controller/AuthProxyController.java` | 换用 Apache HttpClient；no-op error handler；直接写 `HttpServletResponse` 避免 chunked |
