# Auth Service 鉴权服务实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于 Spring Security + JWT 双 Token 机制实现网关统一鉴权，支持用户名密码注册登录和微信 OAuth 2.0 自动注册，新建 auth-service 微服务模块。

**Architecture:** 新建 auth-service（Spring Boot + Dubbo Provider + MySQL + Redis）负责认证和 Token 签发。Gateway 集成 Spring Security FilterChain，JWT 验签后注入 SecurityContext，Controller 用 @AuthenticationPrincipal 获取 userId。前端新增登录页面，Token 管理和请求拦截器。

**Tech Stack:** Spring Boot 3.2.6, Spring Security, jjwt 0.12.5, Spring Data Redis, JdbcTemplate, MySQL 8.0, Dubbo 3.3.0, Nacos, React 18 + TypeScript, Tailwind CSS

---

### Task 1: Infrastructure — MySQL 建表 SQL

**Files:**
- Create: `auth-service/src/main/resources/sql/init_auth.sql`

- [ ] **Step 1: 编写建表 SQL**

```sql
-- auth-service/src/main/resources/sql/init_auth.sql
CREATE TABLE IF NOT EXISTS user_info (
    user_id   BIGINT      PRIMARY KEY,
    username  VARCHAR(64) UNIQUE,
    password  VARCHAR(256),
    nickname  VARCHAR(128),
    avatar    VARCHAR(512),
    status    VARCHAR(16) DEFAULT 'ACTIVE',
    created_at BIGINT     NOT NULL,
    updated_at BIGINT     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_wechat (
    wechat_id  BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    openid     VARCHAR(128) NOT NULL,
    unionid    VARCHAR(128),
    nickname   VARCHAR(128),
    avatar     VARCHAR(512),
    created_at BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user_info(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE UNIQUE INDEX idx_user_wechat_openid ON user_wechat(openid);
CREATE INDEX idx_user_wechat_userid ON user_wechat(user_id);
```

- [ ] **Step 2: 验证 SQL 语法**

Run: `docker cp auth-service/src/main/resources/sql/init_auth.sql mysql:/tmp/ && docker exec mysql mysql -u root -proot live_community -e "source /tmp/init_auth.sql; SHOW TABLES;"`

Expected: 输出中 `user_info` 和 `user_wechat` 两张表。

- [ ] **Step 3: Commit**

```bash
git add auth-service/src/main/resources/sql/init_auth.sql
git commit -m "feat: add auth MySQL tables (user_info, user_wechat)"
```

---

### Task 2: Infrastructure — Docker Compose 新增 Redis + auth-service

**Files:**
- Modify: `docker-compose.yml`
- Modify: `pom.xml` (root — 添加 auth-service module)

- [ ] **Step 1: 修改 docker-compose.yml 添加 Redis 和 auth-service**

在 `cassandra:` 之后，`# ==================== Application Services ====================` 之前插入 Redis：

```yaml
  redis:
    image: redis:7-alpine
    container_name: redis
    ports:
      - "16379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 30
    networks:
      - live-community
```

在 `gateway:` 之后添加 auth-service：

```yaml
  auth-service:
    build: ./auth-service
    container_name: auth-service
    ports:
      - "8084:8084"
      - "20884:20884"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/live_community?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
      SPRING_REDIS_HOST: redis
      SPRING_CLOUD_NACOS_DISCOVERY_SERVER-ADDR: nacos:8848
      DUBBO_REGISTRY_ADDRESS: nacos://nacos:8848
      JWT_SECRET: ${JWT_SECRET:-super-secret-key-change-in-production}
      WECHAT_APP_ID: ${WECHAT_APP_ID:-}
      WECHAT_APP_SECRET: ${WECHAT_APP_SECRET:-}
    depends_on:
      nacos:
        condition: service_healthy
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
    networks:
      - live-community
```

将 MySQL 建表初始化改为支持多个 SQL 文件：

```yaml
      - ./leaf-service/src/main/resources/sql/init_leaf.sql:/docker-entrypoint-initdb.d/01-init-leaf.sql
      - ./auth-service/src/main/resources/sql/init_auth.sql:/docker-entrypoint-initdb.d/02-init-auth.sql
```

- [ ] **Step 2: 修改根 pom.xml 添加 auth-service 模块**

```xml
    <modules>
        <module>common</module>
        <module>gateway</module>
        <module>upload-service</module>
        <module>leaf-service</module>
        <module>note-service</module>
        <module>auth-service</module>
    </modules>
```

- [ ] **Step 3: 验证 Docker Compose 语法**

Run: `docker compose config --services 2>&1`
Expected: 输出包含 `redis` 和 `auth-service`。

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml pom.xml
git commit -m "feat: add redis container and auth-service to docker-compose"
```

---

### Task 3: auth-service — pom.xml + application.yml + Main Class

**Files:**
- Create: `auth-service/pom.xml`
- Create: `auth-service/src/main/resources/application.yml`
- Create: `auth-service/src/main/java/com/example/auth/AuthServiceApplication.java`

- [ ] **Step 1: 创建 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example</groupId>
        <artifactId>spring-cloud-test</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <name>spring-cloud-test :: auth-service</name>
    <artifactId>auth-service</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
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
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.dubbo</groupId>
            <artifactId>dubbo-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.dubbo</groupId>
            <artifactId>dubbo-registry-nacos</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 application.yml**

```yaml
server:
  port: 8084

spring:
  application:
    name: auth-service
  datasource:
    url: jdbc:mysql://localhost:3306/live_community?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: root
  redis:
    host: localhost
    port: 6379

dubbo:
  application:
    name: auth-service
  registry:
    address: nacos://localhost:8848
  protocol:
    name: tri
    port: 20884

jwt:
  secret: super-secret-key-change-in-production

wechat:
  app-id: ""
  app-secret: ""
```

- [ ] **Step 3: 创建 AuthServiceApplication.java**

```java
package com.example.auth;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
```

- [ ] **Step 4: 验证编译**

Run: `mvn compile -pl auth-service -am`
Expected: BUILD SUCCESS。

- [ ] **Step 5: Commit**

```bash
git add auth-service/pom.xml auth-service/src/main/resources/application.yml auth-service/src/main/java/com/example/auth/AuthServiceApplication.java
git commit -m "feat: scaffold auth-service module with Spring Boot + Security + Redis + Dubbo"
```

---

### Task 4: auth-service — Entity + Repository

**Files:**
- Create: `auth-service/src/main/java/com/example/auth/entity/UserEntity.java`
- Create: `auth-service/src/main/java/com/example/auth/entity/UserWechatEntity.java`
- Create: `auth-service/src/main/java/com/example/auth/repository/UserRepository.java`
- Create: `auth-service/src/main/java/com/example/auth/repository/UserWechatRepository.java`

- [ ] **Step 1: 创建 UserEntity**

```java
package com.example.auth.entity;

public class UserEntity {
    private Long userId;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String status;
    private Long createdAt;
    private Long updatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 2: 创建 UserWechatEntity**

```java
package com.example.auth.entity;

public class UserWechatEntity {
    private Long wechatId;
    private Long userId;
    private String openid;
    private String unionid;
    private String nickname;
    private String avatar;
    private Long createdAt;

    public Long getWechatId() { return wechatId; }
    public void setWechatId(Long wechatId) { this.wechatId = wechatId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }
    public String getUnionid() { return unionid; }
    public void setUnionid(String unionid) { this.unionid = unionid; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 3: 创建 UserRepository**

```java
package com.example.auth.repository;

import com.example.auth.entity.UserEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<UserEntity> rowMapper = (rs, rowNum) -> {
        UserEntity u = new UserEntity();
        u.setUserId(rs.getLong("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setNickname(rs.getString("nickname"));
        u.setAvatar(rs.getString("avatar"));
        u.setStatus(rs.getString("status"));
        u.setCreatedAt(rs.getLong("created_at"));
        u.setUpdatedAt(rs.getLong("updated_at"));
        return u;
    };

    public Optional<UserEntity> findById(Long userId) {
        var list = jdbc.query("SELECT * FROM user_info WHERE user_id = ?", rowMapper, userId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<UserEntity> findByUsername(String username) {
        var list = jdbc.query("SELECT * FROM user_info WHERE username = ?", rowMapper, username);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void insert(UserEntity user) {
        jdbc.update(
            "INSERT INTO user_info (user_id, username, password, nickname, avatar, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            user.getUserId(), user.getUsername(), user.getPassword(), user.getNickname(),
            user.getAvatar(), user.getStatus(), user.getCreatedAt(), user.getUpdatedAt()
        );
    }

    public void updateStatus(Long userId, String status) {
        jdbc.update("UPDATE user_info SET status = ? WHERE user_id = ?", status, userId);
    }
}
```

- [ ] **Step 4: 创建 UserWechatRepository**

```java
package com.example.auth.repository;

import com.example.auth.entity.UserWechatEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserWechatRepository {

    private final JdbcTemplate jdbc;

    public UserWechatRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<UserWechatEntity> rowMapper = (rs, rowNum) -> {
        UserWechatEntity w = new UserWechatEntity();
        w.setWechatId(rs.getLong("wechat_id"));
        w.setUserId(rs.getLong("user_id"));
        w.setOpenid(rs.getString("openid"));
        w.setUnionid(rs.getString("unionid"));
        w.setNickname(rs.getString("nickname"));
        w.setAvatar(rs.getString("avatar"));
        w.setCreatedAt(rs.getLong("created_at"));
        return w;
    };

    public Optional<UserWechatEntity> findByOpenid(String openid) {
        var list = jdbc.query("SELECT * FROM user_wechat WHERE openid = ?", rowMapper, openid);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void insert(UserWechatEntity entity) {
        jdbc.update(
            "INSERT INTO user_wechat (wechat_id, user_id, openid, unionid, nickname, avatar, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            entity.getWechatId(), entity.getUserId(), entity.getOpenid(),
            entity.getUnionid(), entity.getNickname(), entity.getAvatar(), entity.getCreatedAt()
        );
    }
}
```

- [ ] **Step 5: 验证编译**

Run: `mvn compile -pl auth-service -am`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git add auth-service/src/main/java/com/example/auth/entity/ auth-service/src/main/java/com/example/auth/repository/
git commit -m "feat: add user and wechat entities with JDBC repositories"
```

---

### Task 5: auth-service — SecurityBeansConfig + JwtAuthFilter

**Files:**
- Create: `auth-service/src/main/java/com/example/auth/security/JwtAuthenticationToken.java`
- Create: `auth-service/src/main/java/com/example/auth/security/JwtAuthFilter.java`
- Create: `auth-service/src/main/java/com/example/auth/config/SecurityBeansConfig.java`

- [ ] **Step 1: 创建 JwtAuthenticationToken**

```java
package com.example.auth.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private final Long userId;
    private final String username;

    public JwtAuthenticationToken(Long userId, String username) {
        super(Collections.emptyList());
        this.userId = userId;
        this.username = username;
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal() { return userId; }
    public String getUsername() { return username; }
}
```

- [ ] **Step 2: 创建 JwtAuthFilter**

```java
package com.example.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final SecretKey secretKey;

    public JwtAuthFilter(@Value("${jwt.secret}") String secret) {
        byte[] keyBytes = Base64.getEncoder().encode(secret.getBytes(StandardCharsets.UTF_8));
        this.secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/register") || path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/wechat/") || path.startsWith("/api/auth/refresh")) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            sendError(response, 401, "缺少认证 Token");
            return;
        }

        try {
            String token = header.substring(7);
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = claims.get("sub", Long.class);
            String username = claims.get("username", String.class);

            JwtAuthenticationToken auth = new JwtAuthenticationToken(userId, username);
            SecurityContextHolder.getContext().setAuthentication(auth);

            chain.doFilter(request, response);
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            sendError(response, 401, "Token 无效或已过期");
        }
    }

    private void sendError(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"msg\":\"" + msg + "\"}");
    }
}
```

- [ ] **Step 3: 创建 SecurityBeansConfig**

```java
package com.example.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class SecurityBeansConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtSecretKey(@org.springframework.beans.factory.annotation.Value("${jwt.secret}") String secret) {
        byte[] keyBytes = Base64.getEncoder().encode(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}
```

- [ ] **Step 4: 验证编译**

Run: `mvn compile -pl auth-service -am`
Expected: BUILD SUCCESS。

- [ ] **Step 5: Commit**

```bash
git add auth-service/src/main/java/com/example/auth/security/ auth-service/src/main/java/com/example/auth/config/
git commit -m "feat: add JwtAuthFilter, JwtAuthenticationToken, and SecurityBeansConfig"
```

---

### Task 6: auth-service — TokenService

**Files:**
- Create: `auth-service/src/main/java/com/example/auth/service/TokenService.java`

- [ ] **Step 1: 创建 TokenService**

```java
package com.example.auth.service;

import com.example.auth.entity.UserEntity;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class TokenService {

    private final SecretKey secretKey;
    private final RedisTemplate<String, String> redis;

    @Value("${jwt.access-token-ttl:900}")
    private long accessTokenTtl;

    @Value("${jwt.refresh-token-ttl:604800}")
    private long refreshTokenTtl;

    public TokenService(SecretKey secretKey, RedisTemplate<String, String> redis) {
        this.secretKey = secretKey;
        this.redis = redis;
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn) {}

    public TokenPair issueTokens(UserEntity user) {
        return new TokenPair(
                issueAccessToken(user),
                issueRefreshToken(user.getUserId()),
                accessTokenTtl
        );
    }

    String issueAccessToken(UserEntity user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(Map.of(
                        "username", user.getUsername() != null ? user.getUsername() : "",
                        "nickname", user.getNickname() != null ? user.getNickname() : "",
                        "avatar", user.getAvatar() != null ? user.getAvatar() : ""
                ))
                .subject(user.getUserId().toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenTtl * 1000))
                .signWith(secretKey)
                .compact();
    }

    String issueRefreshToken(Long userId) {
        String tokenId = UUID.randomUUID().toString();
        String key = "refresh:" + tokenId;
        redis.opsForValue().set(key, userId.toString(), Duration.ofSeconds(refreshTokenTtl));
        redis.opsForSet().add("user_sessions:" + userId, tokenId);
        redis.expire("user_sessions:" + userId, Duration.ofSeconds(refreshTokenTtl));
        return tokenId;
    }

    public Long validateAndGetUserId(String refreshToken) {
        String key = "refresh:" + refreshToken;
        String userIdStr = redis.opsForValue().get(key);
        if (userIdStr == null) return null;
        return Long.parseLong(userIdStr);
    }

    public void revokeRefreshToken(Long userId, String refreshToken) {
        redis.delete("refresh:" + refreshToken);
        redis.opsForSet().remove("user_sessions:" + userId, refreshToken);
    }

    public void revokeAllUserTokens(Long userId) {
        var tokenIds = redis.opsForSet().members("user_sessions:" + userId);
        if (tokenIds != null) {
            for (String tid : tokenIds) {
                redis.delete("refresh:" + tid);
            }
        }
        redis.delete("user_sessions:" + userId);
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl auth-service -am`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add auth-service/src/main/java/com/example/auth/service/TokenService.java
git commit -m "feat: add TokenService with JWT access token + Redis refresh token"
```

---

### Task 7: auth-service — WechatAuthService

**Files:**
- Create: `auth-service/src/main/java/com/example/auth/service/WechatAuthService.java`
- Create: `auth-service/src/main/java/com/example/auth/config/WechatConfig.java`
- Create: `auth-service/src/main/java/com/example/auth/dto/WechatUserInfo.java`

- [ ] **Step 1: 创建 WechatConfig**

```java
package com.example.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "wechat")
public class WechatConfig {
    private String appId = "";
    private String appSecret = "";

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
}
```

- [ ] **Step 2: 创建 WechatUserInfo DTO**

```java
package com.example.auth.dto;

public class WechatUserInfo {
    private String openid;
    private String unionid;
    private String nickname;
    private String headimgurl;

    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }
    public String getUnionid() { return unionid; }
    public void setUnionid(String unionid) { this.unionid = unionid; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getHeadimgurl() { return headimgurl; }
    public void setHeadimgurl(String headimgurl) { this.headimgurl = headimgurl; }
}
```

- [ ] **Step 3: 创建 WechatAuthService**

```java
package com.example.auth.service;

import com.example.auth.config.WechatConfig;
import com.example.auth.dto.WechatUserInfo;
import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserWechatEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.repository.UserWechatRepository;
import com.example.common.IdResponse;
import com.example.common.LeafRpcService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class WechatAuthService {

    private static final Logger log = LoggerFactory.getLogger(WechatAuthService.class);

    private final WechatConfig wechatConfig;
    private final UserRepository userRepository;
    private final UserWechatRepository wechatRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @DubboReference(check = false)
    private LeafRpcService leafRpcService;

    public WechatAuthService(WechatConfig wechatConfig, UserRepository userRepository,
                              UserWechatRepository wechatRepository) {
        this.wechatConfig = wechatConfig;
        this.userRepository = userRepository;
        this.wechatRepository = wechatRepository;
    }

    public String buildAuthUrl(String redirectUri) {
        return String.format(
            "https://open.weixin.qq.com/connect/qrconnect?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_login&state=STATE#wechat_redirect",
            wechatConfig.getAppId(), redirectUri
        );
    }

    public UserEntity authenticateAndRegister(String code) {
        if (wechatConfig.getAppId().isEmpty()) {
            throw new RuntimeException("微信登录未配置 (wechat.app-id)");
        }

        WechatUserInfo wechatUser = fetchWechatUserInfo(code);
        var existing = wechatRepository.findByOpenid(wechatUser.getOpenid());

        if (existing.isPresent()) {
            return userRepository.findById(existing.get().getUserId())
                    .orElseThrow(() -> new RuntimeException("用户数据异常"));
        }

        // 自动注册
        long now = System.currentTimeMillis();
        IdResponse idResp = leafRpcService.generateSegmentId("user");
        long userId = idResp.getId();

        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setUsername(null);  // 微信用户无独立密码
        user.setPassword(null);
        user.setNickname(wechatUser.getNickname());
        user.setAvatar(wechatUser.getHeadimgurl());
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.insert(user);

        IdResponse wechatIdResp = leafRpcService.generateSegmentId("wechat");
        UserWechatEntity wechatEntity = new UserWechatEntity();
        wechatEntity.setWechatId(wechatIdResp.getId());
        wechatEntity.setUserId(userId);
        wechatEntity.setOpenid(wechatUser.getOpenid());
        wechatEntity.setUnionid(wechatUser.getUnionid());
        wechatEntity.setNickname(wechatUser.getNickname());
        wechatEntity.setAvatar(wechatUser.getHeadimgurl());
        wechatEntity.setCreatedAt(now);
        wechatRepository.insert(wechatEntity);

        log.info("Wechat auto-registered: userId={}, openid={}", userId, wechatUser.getOpenid());
        return user;
    }

    private WechatUserInfo fetchWechatUserInfo(String code) {
        try {
            String tokenUrl = String.format(
                "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
                wechatConfig.getAppId(), wechatConfig.getAppSecret(), code
            );
            String tokenResp = restTemplate.getForObject(tokenUrl, String.class);
            JsonNode tokenNode = objectMapper.readTree(tokenResp);

            if (tokenNode.has("errcode")) {
                throw new RuntimeException("微信授权失败: " + tokenNode.get("errmsg").asText());
            }

            String accessToken = tokenNode.get("access_token").asText();
            String openid = tokenNode.get("openid").asText();

            String userUrl = String.format(
                "https://api.weixin.qq.com/sns/userinfo?access_token=%s&openid=%s",
                accessToken, openid
            );
            String userResp = restTemplate.getForObject(userUrl, String.class);
            return objectMapper.readValue(userResp, WechatUserInfo.class);
        } catch (Exception e) {
            log.error("Failed to fetch WeChat user info", e);
            throw new RuntimeException("微信登录失败: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: 验证编译**

Run: `mvn compile -pl auth-service -am`
Expected: BUILD SUCCESS。

- [ ] **Step 5: Commit**

```bash
git add auth-service/src/main/java/com/example/auth/service/WechatAuthService.java auth-service/src/main/java/com/example/auth/config/WechatConfig.java auth-service/src/main/java/com/example/auth/dto/WechatUserInfo.java
git commit -m "feat: add WechatAuthService with OAuth code flow and auto-registration"
```

---

### Task 8: auth-service — Security Tokens + Providers

**Files:**
- Create: `auth-service/src/main/java/com/example/auth/security/WechatCodeAuthenticationToken.java`
- Create: `auth-service/src/main/java/com/example/auth/security/DaoAuthenticationProvider.java`
- Create: `auth-service/src/main/java/com/example/auth/security/WechatAuthenticationProvider.java`

- [ ] **Step 1: 创建 WechatCodeAuthenticationToken**

```java
package com.example.auth.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

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

- [ ] **Step 2: 创建 DaoAuthenticationProvider**

```java
package com.example.auth.security;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class DaoAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DaoAuthenticationProvider(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication auth) throws AuthenticationException {
        String username = auth.getName();
        String password = auth.getCredentials().toString();

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));

        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new DisabledException("账号已被禁用");
        }

        return new UsernamePasswordAuthenticationToken(user.getUserId(), null, Collections.emptyList());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
```

- [ ] **Step 3: 创建 WechatAuthenticationProvider**

```java
package com.example.auth.security;

import com.example.auth.entity.UserEntity;
import com.example.auth.service.WechatAuthService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class WechatAuthenticationProvider implements AuthenticationProvider {

    private final WechatAuthService wechatAuthService;

    public WechatAuthenticationProvider(WechatAuthService wechatAuthService) {
        this.wechatAuthService = wechatAuthService;
    }

    @Override
    public Authentication authenticate(Authentication auth) throws AuthenticationException {
        String code = auth.getCredentials().toString();
        UserEntity user = wechatAuthService.authenticateAndRegister(code);
        return new UsernamePasswordAuthenticationToken(user.getUserId(), null, Collections.emptyList());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return WechatCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
```

- [ ] **Step 4: 验证编译**

Run: `mvn compile -pl auth-service -am`
Expected: BUILD SUCCESS。

- [ ] **Step 5: Commit**

```bash
git add auth-service/src/main/java/com/example/auth/security/
git commit -m "feat: add authentication tokens and providers for password + wechat login"
```

---

### Task 9: auth-service — SecurityConfig

**Files:**
- Create: `auth-service/src/main/java/com/example/auth/config/AuthSecurityConfig.java`

- [ ] **Step 1: 创建 AuthSecurityConfig**

```java
package com.example.auth.config;

import com.example.auth.security.DaoAuthenticationProvider;
import com.example.auth.security.JwtAuthFilter;
import com.example.auth.security.WechatAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class AuthSecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public AuthSecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register",
                                 "/api/auth/login",
                                 "/api/auth/wechat/url",
                                 "/api/auth/wechat/login",
                                 "/api/auth/refresh").permitAll()
                .requestMatchers("/api/auth/logout",
                                 "/api/auth/me").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    AuthenticationManager authManager(
            DaoAuthenticationProvider daoProvider,
            WechatAuthenticationProvider wechatProvider) {
        return new ProviderManager(daoProvider, wechatProvider);
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl auth-service -am`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add auth-service/src/main/java/com/example/auth/config/AuthSecurityConfig.java
git commit -m "feat: add AuthSecurityConfig with multi-provider AuthenticationManager"
```

---

### Task 10: auth-service — AuthController

**Files:**
- Create: `auth-service/src/main/java/com/example/auth/controller/AuthController.java`
- Create: `auth-service/src/main/java/com/example/auth/dto/RegisterRequest.java`
- Create: `auth-service/src/main/java/com/example/auth/dto/LoginRequest.java`
- Create: `auth-service/src/main/java/com/example/auth/dto/WechatLoginRequest.java`
- Create: `auth-service/src/main/java/com/example/auth/dto/RefreshRequest.java`
- Create: `auth-service/src/main/java/com/example/auth/dto/LoginResponse.java`

- [ ] **Step 1: 创建 DTOs**

```java
// RegisterRequest.java
package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank @Size(min = 2, max = 64)
    private String username;
    @NotBlank @Size(min = 6, max = 128)
    private String password;
    @Size(max = 128)
    private String nickname;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
```

```java
// LoginRequest.java
package com.example.auth.dto;

public class LoginRequest {
    private String username;
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
```

```java
// WechatLoginRequest.java
package com.example.auth.dto;

public class WechatLoginRequest {
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
```

```java
// RefreshRequest.java
package com.example.auth.dto;

public class RefreshRequest {
    private String refreshToken;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
```

```java
// LoginResponse.java
package com.example.auth.dto;

public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserInfo user;

    public static class UserInfo {
        private Long userId;
        private String username;
        private String nickname;
        private String avatar;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }
}
```

- [ ] **Step 2: 创建 AuthController**

```java
package com.example.auth.controller;

import com.example.auth.dto.*;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.security.WechatCodeAuthenticationToken;
import com.example.auth.service.TokenService;
import com.example.auth.service.WechatAuthService;
import com.example.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WechatAuthService wechatAuthService;

    public AuthController(AuthenticationManager authManager, TokenService tokenService,
                          UserRepository userRepository, PasswordEncoder passwordEncoder,
                          WechatAuthService wechatAuthService) {
        this.authManager = authManager;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.wechatAuthService = wechatAuthService;
    }

    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody RegisterRequest req) {
        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }

        // 用 Leaf ID + 本地插入（不走 ProviderManager）
        // 简化处理：直接用 JdbcTemplate 插入
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "Register will be integrated via leaf-service. Use POST /login for now.");
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        Long userId = (Long) auth.getPrincipal();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        TokenService.TokenPair tokens = tokenService.issueTokens(user);
        return Result.ok(toLoginResponse(user, tokens));
    }

    @PostMapping("/wechat/login")
    public Result<LoginResponse> wechatLogin(@RequestBody WechatLoginRequest req) {
        Authentication auth = authManager.authenticate(
                new WechatCodeAuthenticationToken(req.getCode()));
        Long userId = (Long) auth.getPrincipal();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        TokenService.TokenPair tokens = tokenService.issueTokens(user);
        return Result.ok(toLoginResponse(user, tokens));
    }

    @GetMapping("/wechat/url")
    public Result<String> wechatUrl(@RequestParam String redirectUri) {
        String url = wechatAuthService.buildAuthUrl(redirectUri);
        return Result.ok(url);
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody RefreshRequest req) {
        Long userId = tokenService.validateAndGetUserId(req.getRefreshToken());
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "RefreshToken 无效或已过期");
        }

        tokenService.revokeRefreshToken(userId, req.getRefreshToken());

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        TokenService.TokenPair tokens = tokenService.issueTokens(user);
        return Result.ok(toLoginResponse(user, tokens));
    }

    @PostMapping("/logout")
    public Result<String> logout(@RequestBody RefreshRequest req,
                                  @AuthenticationPrincipal Long userId) {
        tokenService.revokeRefreshToken(userId, req.getRefreshToken());
        return Result.ok("ok");
    }

    @GetMapping("/me")
    public Result<LoginResponse.UserInfo> me(@AuthenticationPrincipal Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        LoginResponse.UserInfo info = new LoginResponse.UserInfo();
        info.setUserId(user.getUserId());
        info.setUsername(user.getUsername());
        info.setNickname(user.getNickname());
        info.setAvatar(user.getAvatar());
        return Result.ok(info);
    }

    private LoginResponse toLoginResponse(UserEntity user, TokenService.TokenPair tokens) {
        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(tokens.accessToken());
        resp.setRefreshToken(tokens.refreshToken());
        resp.setExpiresIn(tokens.expiresIn());

        LoginResponse.UserInfo info = new LoginResponse.UserInfo();
        info.setUserId(user.getUserId());
        info.setUsername(user.getUsername());
        info.setNickname(user.getNickname());
        info.setAvatar(user.getAvatar());
        resp.setUser(info);
        return resp;
    }
}
```

- [ ] **Step 3: 验证编译**

Run: `mvn compile -pl auth-service -am`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add auth-service/src/main/java/com/example/auth/controller/ auth-service/src/main/java/com/example/auth/dto/
git commit -m "feat: add AuthController with login/wechat/refresh/logout/me endpoints"
```

---

### Task 11: auth-service — Register 完善（集成 Leaf ID）

**Files:**
- Modify: `auth-service/src/main/java/com/example/auth/controller/AuthController.java`

- [ ] **Step 1: 替换 register 方法**

```java
// 在 AuthController 类中注入
private final com.example.common.LeafRpcService leafRpcService;

// 构造函数添加 LeafRpcService 参数

// 替换 register 方法
@PostMapping("/register")
public Result<LoginResponse> register(@RequestBody RegisterRequest req) {
    if (userRepository.findByUsername(req.getUsername()).isPresent()) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
    }

    long now = System.currentTimeMillis();
    var idResp = leafRpcService.generateSegmentId("user");
    long userId = idResp.getId();

    UserEntity user = new UserEntity();
    user.setUserId(userId);
    user.setUsername(req.getUsername());
    user.setPassword(passwordEncoder.encode(req.getPassword()));
    user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
    user.setStatus("ACTIVE");
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    userRepository.insert(user);

    return login(new LoginRequest() {{
        setUsername(req.getUsername());
        setPassword(req.getPassword());
    }});
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl auth-service -am`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add auth-service/src/main/java/com/example/auth/controller/AuthController.java
git commit -m "feat: implement register with Leaf ID generation"
```

---

### Task 12: Gateway — Security 依赖 + JwtAuthFilter

**Files:**
- Modify: `gateway/pom.xml`
- Create: `gateway/src/main/java/com/example/gateway/security/JwtAuthenticationToken.java`
- Create: `gateway/src/main/java/com/example/gateway/security/JwtAuthFilter.java`
- Modify: `gateway/src/main/resources/application.yml`

- [ ] **Step 1: 修改 gateway/pom.xml 添加 Security + jjwt**

在 `<dependencies>` 中添加：

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

- [ ] **Step 2: 修改 gateway application.yml 添加 jwt.secret**

```yaml
jwt:
  secret: super-secret-key-change-in-production
```

- [ ] **Step 3: 创建 JwtAuthenticationToken 和 JwtAuthFilter**

将 `auth-service` 中的 `JwtAuthenticationToken.java` 和 `JwtAuthFilter.java` 复制到 `gateway/src/main/java/com/example/gateway/security/`，修改包名为 `com.example.gateway.security`。

- [ ] **Step 4: 验证编译**

Run: `mvn compile -pl gateway -am`
Expected: BUILD SUCCESS。

- [ ] **Step 5: Commit**

```bash
git add gateway/pom.xml gateway/src/main/resources/application.yml gateway/src/main/java/com/example/gateway/security/
git commit -m "feat: add Spring Security + jjwt dependencies and JwtAuthFilter to gateway"
```

---

### Task 13: Gateway — SecurityConfig

**Files:**
- Create: `gateway/src/main/java/com/example/gateway/config/GatewaySecurityConfig.java`

- [ ] **Step 1: 创建 GatewaySecurityConfig**

```java
package com.example.gateway.config;

import com.example.gateway.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class GatewaySecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public GatewaySecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/upload/presigned").authenticated()
                .requestMatchers("/api/note/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl gateway -am`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add gateway/src/main/java/com/example/gateway/config/GatewaySecurityConfig.java
git commit -m "feat: add GatewaySecurityConfig with stateless JWT filter chain"
```

---

### Task 14: Gateway — 更新 Controllers 使用 @AuthenticationPrincipal

**Files:**
- Modify: `gateway/src/main/java/com/example/gateway/controller/NoteController.java`
- Modify: `gateway/src/main/java/com/example/gateway/controller/UploadController.java`

- [ ] **Step 1: 更新 NoteController**

将 `MOCK_USER.userId` 替换为 `@AuthenticationPrincipal Long userId`，并在需要 userId 的端点（draft/comment/publish）中注入并覆盖请求中的 userId：

```java
package com.example.gateway.controller;

import com.example.common.*;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/note")
public class NoteController {

    @DubboReference(check = false)
    private NoteRpcService noteRpcService;

    @PostMapping("/draft")
    public Result<CreateDraftResponse> createDraft(@AuthenticationPrincipal Long userId,
                                                    @RequestBody CreateDraftRequest request) {
        request.setUserId(userId);
        CreateDraftResponse resp = noteRpcService.createDraft(request);
        return Result.ok(resp);
    }

    @PostMapping("/publish")
    public Result<NoteDetailResponse> publishNote(@RequestBody PublishNoteRequest request) {
        NoteDetailResponse resp = noteRpcService.publishNote(request);
        return Result.ok(resp);
    }

    @GetMapping("/detail")
    public Result<NoteDetailResponse> getNoteDetail(@RequestParam("noteId") Long noteId) {
        NoteDetailResponse resp = noteRpcService.getNoteDetail(noteId);
        return Result.ok(resp);
    }

    @PostMapping("/comment")
    public Result<CommentResponse> addComment(@AuthenticationPrincipal Long userId,
                                               @RequestBody CommentRequest request) {
        request.setUserId(userId);
        CommentResponse resp = noteRpcService.addComment(request);
        return Result.ok(resp);
    }

    @GetMapping("/list")
    public Result<List<NoteDetailResponse>> listNotes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<NoteDetailResponse> list = noteRpcService.listPublishedNotes(page, size);
        return Result.ok(list);
    }
}
```

- [ ] **Step 2: 更新 UploadController**

```java
@GetMapping("/presigned")
public Result<PresignedUrlResponse> presignedUrl(
        @AuthenticationPrincipal Long userId,
        @RequestParam("fileName") String fileName,
        @RequestParam("contentType") String contentType) {
    PresignedUrlResponse resp = uploadRpcService.generatePresignedUrl(
            new PresignedUrlRequest(fileName, contentType));
    return Result.ok(resp);
}
```

- [ ] **Step 3: 验证编译**

Run: `mvn compile -pl gateway -am`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add gateway/src/main/java/com/example/gateway/controller/
git commit -m "feat: replace MOCK_USER with @AuthenticationPrincipal in gateway controllers"
```

---

### Task 15: Frontend — TokenStore + 请求拦截器

**Files:**
- Modify: `front/src/config.ts` — 移除 MOCK_USER
- Create: `front/src/utils/tokenStore.ts`
- Modify: `front/src/api/index.ts` — 请求拦截 + 401 处理

- [ ] **Step 1: 移除 MOCK_USER**

```typescript
// front/src/config.ts
export const API_BASE = '';
export const PAGE_SIZE = 20;
```

- [ ] **Step 2: 创建 TokenStore**

```typescript
// front/src/utils/tokenStore.ts
const TOKENS_KEY = 'auth_tokens';

export interface TokenData {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}

export function saveTokens(data: TokenData): void {
  localStorage.setItem(TOKENS_KEY, JSON.stringify(data));
}

export function loadTokens(): TokenData | null {
  const raw = localStorage.getItem(TOKENS_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as TokenData;
  } catch {
    return null;
  }
}

export function getAccessToken(): string | null {
  const tokens = loadTokens();
  if (!tokens) return null;
  if (Date.now() > tokens.expiresAt) return null;
  return tokens.accessToken;
}

export function getRefreshToken(): string | null {
  return loadTokens()?.refreshToken ?? null;
}

export function clearTokens(): void {
  localStorage.removeItem(TOKENS_KEY);
}

export function isAuthenticated(): boolean {
  return getAccessToken() !== null;
}
```

- [ ] **Step 3: 更新 api/index.ts 请求拦截**

```typescript
// api/index.ts — 更新 request() 函数
import { API_BASE } from '../config';
import { getAccessToken, clearTokens } from '../utils/tokenStore';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getAccessToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { ...headers, ...((options?.headers as Record<string, string>) ?? {}) },
  });
  if (res.status === 401) {
    clearTokens();
    window.location.href = '/login';
    throw new Error('登录已过期');
  }
  if (res.status === 429) {
    throw new Error('请求太频繁，请稍后再试');
  }
  if (!res.ok) {
    throw new Error(`请求失败 (${res.status})`);
  }
  const json = await res.json();
  if (json.code !== 200) {
    throw new Error(json.msg || '服务器错误');
  }
  return json.data;
}
```

- [ ] **Step 4: 验证 TypeScript 编译**

Run: `npx tsc --noEmit`
Expected: No errors。

- [ ] **Step 5: Commit**

```bash
git add front/src/config.ts front/src/utils/tokenStore.ts front/src/api/index.ts
git commit -m "feat: add TokenStore and request interceptor with auto 401 redirect"
```

---

### Task 16: Frontend — LoginPage

**Files:**
- Create: `front/src/pages/LoginPage.tsx`

- [ ] **Step 1: 创建 LoginPage**

```tsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { API_BASE } from '../config';
import { saveTokens } from '../utils/tokenStore';

export default function LoginPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const endpoint = tab === 'login' ? '/api/auth/login' : '/api/auth/register';
      const body = tab === 'login'
        ? { username, password }
        : { username, password, nickname: nickname || username };

      const res = await fetch(`${API_BASE}${endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });

      if (!res.ok) {
        const json = await res.json();
        throw new Error(json.msg || '操作失败');
      }

      const json = await res.json();
      const data = json.data;
      saveTokens({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        expiresAt: Date.now() + data.expiresIn * 1000,
      });

      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-white flex flex-col">
      <div className="flex-1 flex flex-col items-center justify-center px-6">
        <div className="w-full max-w-sm">
          <h1 className="text-2xl font-bold text-center mb-8">Live Community</h1>

          <div className="flex border-b border-border mb-6">
            {(['login', 'register'] as const).map((t) => (
              <button
                key={t}
                onClick={() => { setTab(t); setError(null); }}
                className={`flex-1 pb-2.5 text-sm font-bold border-b-2 transition-colors ${
                  tab === t ? 'border-brand text-brand' : 'border-transparent text-text-muted'
                }`}
              >
                {t === 'login' ? '登录' : '注册'}
              </button>
            ))}
          </div>

          {error && (
            <div className="bg-red-50 text-red-500 text-sm rounded-xl px-4 py-3 mb-4">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="用户名"
              required
              minLength={2}
              maxLength={64}
              className="w-full h-12 px-4 rounded-xl bg-bg-page outline-none ring-1 ring-transparent focus:ring-brand/20 focus:bg-white transition-all text-[15px] placeholder:text-text-muted"
            />
            {tab === 'register' && (
              <input
                type="text"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                placeholder="昵称（选填）"
                maxLength={128}
                className="w-full h-12 px-4 rounded-xl bg-bg-page outline-none ring-1 ring-transparent focus:ring-brand/20 focus:bg-white transition-all text-[15px] placeholder:text-text-muted"
              />
            )}
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="密码"
              required
              minLength={6}
              maxLength={128}
              className="w-full h-12 px-4 rounded-xl bg-bg-page outline-none ring-1 ring-transparent focus:ring-brand/20 focus:bg-white transition-all text-[15px] placeholder:text-text-muted"
            />
            <button
              type="submit"
              disabled={loading}
              className="w-full h-12 rounded-full bg-brand text-white font-bold text-[15px] active:bg-brand-hover disabled:opacity-50 transition-colors shadow-sm shadow-brand/25"
            >
              {loading ? '处理中...' : tab === 'login' ? '登录' : '注册'}
            </button>
          </form>

          <div className="mt-6 text-center">
            <span className="text-text-muted text-sm">微信登录功能开发中</span>
          </div>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: 验证 TypeScript 编译**

Run: `npx tsc --noEmit`
Expected: No errors。

- [ ] **Step 3: Commit**

```bash
git add front/src/pages/LoginPage.tsx
git commit -m "feat: add LoginPage with login/register tabs"
```

---

### Task 17: Frontend — App.tsx 路由 + 鉴权守卫

**Files:**
- Modify: `front/src/App.tsx`
- Modify: 移除所有 `MOCK_USER` 引用（CommentItem.tsx, NoteCard.tsx, NoteDetailPage.tsx, PublishPage.tsx）

- [ ] **Step 1: 更新 App.tsx 添加登录路由和鉴权守卫**

```tsx
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import TabBar from './components/TabBar';
import FeedPage from './pages/FeedPage';
import NoteDetailPage from './pages/NoteDetailPage';
import PublishPage from './pages/PublishPage';
import LoginPage from './pages/LoginPage';
import { isAuthenticated } from './utils/tokenStore';

function AuthGuard({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  if (!isAuthenticated()) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-bg-page max-w-lg mx-auto relative shadow-float">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<AuthGuard><FeedPage /></AuthGuard>} />
          <Route path="/note/:noteId" element={<AuthGuard><NoteDetailPage /></AuthGuard>} />
          <Route path="/publish" element={<AuthGuard><PublishPage /></AuthGuard>} />
          <Route
            path="/messages"
            element={
              <AuthGuard>
                <div className="flex items-center justify-center h-screen text-text-muted text-sm">
                  消息功能即将上线
                </div>
              </AuthGuard>
            }
          />
        </Routes>
        <TabBar />
      </div>
    </BrowserRouter>
  );
}
```

- [ ] **Step 2: 移除组件中的 MOCK_USER 引用**

所有组件中 `MOCK_USER.userId` → `getAccessToken() ? decodeJWT(getAccessToken()!).userId : 0`，或简化为：直接在组件中使用从 API 获取的 userId。

对于 NoteCard, CommentItem 等只用于 UI 展示的组件，保留 `MOCK_USER.username` 的前端头像样式，但用户信息改为从 API 获取或从 JWT payload 解析。

**简化策略**：新增一个 `useCurrentUser()` hook：
```typescript
// front/src/hooks/useCurrentUser.ts
export function useCurrentUser() {
  const token = getAccessToken();
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return { userId: payload.sub, username: payload.username, avatar: payload.avatar };
  } catch {
    return null;
  }
}
```

- [ ] **Step 3: 验证编译**

Run: `npx tsc --noEmit && npx vite build`
Expected: No errors, BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add front/src/ front/src/hooks/
git commit -m "feat: add auth guard, login route, and replace MOCK_USER with JWT hook"
```

---

### Task 18: 集成测试 — 全链路验证

**Files:**
- 无新文件，docker compose 集成测试

- [ ] **Step 1: 构建所有服务**

Run: `mvn clean package -DskipTests`
Expected: BUILD SUCCESS for all modules。

- [ ] **Step 2: 启动全栈**

Run: `docker compose up -d --build`
Expected: 所有 11 个容器健康（nacos, mysql, redis, minio, minio-init, cassandra, leaf-service, upload-service, note-service, auth-service, gateway）。

- [ ] **Step 3: 验证注册接口**

Run: `curl -s -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"username":"testuser","password":"123456","nickname":"测试用户"}' | head -200`
Expected: HTTP 200，返回 JSON `{ code: 200, data: { accessToken: "...", refreshToken: "..." } }`。

- [ ] **Step 4: 验证 JWT 鉴权**

Run: `curl -s http://localhost:8080/api/note/list`
Expected: HTTP 401，`{ "code": 401, "msg": "缺少认证 Token" }`。

Run: `TOKEN=上一步获取的accessToken && curl -s http://localhost:8080/api/note/list -H "Authorization: Bearer $TOKEN"`
Expected: HTTP 200，返回笔记列表。

- [ ] **Step 5: 验证前端**

Run: `cd front && npx vite build && npx vite preview`
Expected: 浏览器访问 `http://localhost:4173`，未登录跳转到 `/login`，登录后可访问首页。

---

### Post-Implementation Verification

完成所有 tasks 后：

```bash
docker compose ps                    # 所有服务 healthy
curl http://localhost:8080/api/auth/login -X POST \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"123456"}'
# → 返回 accessToken + refreshToken

curl http://localhost:8080/api/note/list \
  -H "Authorization: Bearer $TOKEN"
# → 200 OK

# 验证 RefreshToken 刷新
curl http://localhost:8080/api/auth/refresh -X POST \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"上一步的refreshToken"}'
# → 新 Token 对，旧 token 失效

# 验证未认证拒绝
curl http://localhost:8080/api/note/list
# → 401
```
