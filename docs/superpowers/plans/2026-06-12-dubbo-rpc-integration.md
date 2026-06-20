# Dubbo RPC Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Gateway→upload-service HTTP routing with Dubbo RPC (Triple protocol), keeping Sentinel rate limiting on the gateway's REST layer.

**Architecture:** Gateway becomes a Spring Boot Web app acting as Dubbo consumer, calling upload-service via `@DubboReference`. upload-service exposes its MinIO presigned-URL logic via `@DubboService`. Nacos (localhost:8848) serves as Dubbo registry. Common module holds the RPC contract (interface + DTOs).

**Tech Stack:** Java 17, Spring Boot 3.2.6, Dubbo 3.3.0, Nacos 2.x, MinIO 8.5.10, Sentinel (via spring-cloud-starter-alibaba-sentinel)

---

### Task 1: Add Dubbo version to parent POM

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add dubbo.version property and dependency management**

In `pom.xml`, add to `<properties>`:
```xml
<dubbo.version>3.3.0</dubbo.version>
```

In `pom.xml`, add to `<dependencyManagement>` / `<dependencies>`:
```xml
<dependency>
    <groupId>org.apache.dubbo</groupId>
    <artifactId>dubbo-spring-boot-starter</artifactId>
    <version>${dubbo.version}</version>
</dependency>
<dependency>
    <groupId>org.apache.dubbo</groupId>
    <artifactId>dubbo-registry-nacos</artifactId>
    <version>${dubbo.version}</version>
</dependency>
```

- [ ] **Step 2: Verify parent POM parses**

```bash
mvn validate -q
```

---

### Task 2: common — Add RPC contract (interface + DTOs)

**Files:**
- Create: `common/src/main/java/com/example/common/UploadRpcService.java`
- Create: `common/src/main/java/com/example/common/PresignedUrlRequest.java`
- Create: `common/src/main/java/com/example/common/PresignedUrlResponse.java`

- [ ] **Step 1: Create PresignedUrlRequest**

```java
package com.example.common;

import java.io.Serializable;

public class PresignedUrlRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String fileName;
    private String contentType;

    public PresignedUrlRequest() {}

    public PresignedUrlRequest(String fileName, String contentType) {
        this.fileName = fileName;
        this.contentType = contentType;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
}
```

- [ ] **Step 2: Create PresignedUrlResponse**

```java
package com.example.common;

import java.io.Serializable;

public class PresignedUrlResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String uploadUrl;
    private String objectKey;
    private long expiresAt;

    public PresignedUrlResponse() {}

    public PresignedUrlResponse(String uploadUrl, String objectKey, long expiresAt) {
        this.uploadUrl = uploadUrl;
        this.objectKey = objectKey;
        this.expiresAt = expiresAt;
    }

    public String getUploadUrl() { return uploadUrl; }
    public void setUploadUrl(String uploadUrl) { this.uploadUrl = uploadUrl; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
}
```

- [ ] **Step 3: Create UploadRpcService interface**

```java
package com.example.common;

public interface UploadRpcService {

    PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request);
}
```

- [ ] **Step 4: Compile common module**

```bash
mvn compile -pl common
```

---

### Task 3: upload-service — Add Dubbo dependencies

**Files:**
- Modify: `upload-service/pom.xml`

- [ ] **Step 1: Add Dubbo dependencies to upload-service**

Add inside `<dependencies>`:
```xml
<dependency>
    <groupId>org.apache.dubbo</groupId>
    <artifactId>dubbo-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.dubbo</groupId>
    <artifactId>dubbo-registry-nacos</artifactId>
</dependency>
```

- [ ] **Step 2: Verify dependency resolution**

```bash
mvn dependency:resolve -pl upload-service -q
```

---

### Task 4: upload-service — Create Dubbo service implementation

**Files:**
- Create: `upload-service/src/main/java/com/example/upload/rpc/UploadRpcServiceImpl.java`

- [ ] **Step 1: Create UploadRpcServiceImpl**

```java
package com.example.upload.rpc;

import com.example.common.PresignedUrlRequest;
import com.example.common.PresignedUrlResponse;
import com.example.common.UploadRpcService;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@DubboService
public class UploadRpcServiceImpl implements UploadRpcService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.presigned-expiry:300}")
    private int presignedExpiry;

    public UploadRpcServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectKey = datePath + "/" + UUID.randomUUID() + "_" + request.getFileName();

        try {
            String url = minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(presignedExpiry, TimeUnit.SECONDS)
                            .build());

            return new PresignedUrlResponse(url, objectKey,
                    System.currentTimeMillis() / 1000 + presignedExpiry);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 2: Add @EnableDubbo to UploadServiceApplication**

Modify `upload-service/src/main/java/com/example/upload/UploadServiceApplication.java` — add `@EnableDubbo`:
```java
package com.example.upload;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableDubbo
public class UploadServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UploadServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Add Dubbo config to upload-service application.yml**

Append to `upload-service/src/main/resources/application.yml`:
```yaml
dubbo:
  application:
    name: upload-service
  registry:
    address: nacos://localhost:8848
  protocol:
    name: tri
    port: 20880
```

- [ ] **Step 4: Compile upload-service**

```bash
mvn compile -pl upload-service
```

---

### Task 5: gateway — Replace dependencies

**Files:**
- Modify: `gateway/pom.xml`

- [ ] **Step 1: Rewrite gateway pom.xml dependencies**

Replace the entire `<dependencies>` block:
```xml
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
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
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
```

- [ ] **Step 2: Verify dependency resolution**

```bash
mvn dependency:resolve -pl gateway -q
```

---

### Task 6: gateway — Rewrite application.yml

**Files:**
- Modify: `gateway/src/main/resources/application.yml`

- [ ] **Step 1: Replace application.yml content**

Replace the entire file content:
```yaml
server:
  port: 8080

spring:
  application:
    name: gateway

dubbo:
  application:
    name: gateway
  registry:
    address: nacos://localhost:8848
```

---

### Task 7: gateway — Rewrite Application class + add UploadController

**Files:**
- Modify: `gateway/src/main/java/com/example/gateway/GatewayApplication.java`
- Create: `gateway/src/main/java/com/example/gateway/controller/UploadController.java`

- [ ] **Step 1: Rewrite GatewayApplication**

Replace the file content:
```java
package com.example.gateway;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

- [ ] **Step 2: Create UploadController**

```java
package com.example.gateway.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.example.common.PresignedUrlRequest;
import com.example.common.PresignedUrlResponse;
import com.example.common.Result;
import com.example.common.UploadRpcService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    @DubboReference(check = false)
    private UploadRpcService uploadRpcService;

    @GetMapping("/presigned")
    @SentinelResource(value = "upload-presigned", blockHandler = "rateLimitFallback")
    public Result<PresignedUrlResponse> presignedUrl(
            @RequestParam String fileName,
            @RequestParam String contentType) {
        PresignedUrlRequest req = new PresignedUrlRequest(fileName, contentType);
        PresignedUrlResponse resp = uploadRpcService.generatePresignedUrl(req);
        return Result.ok(resp);
    }

    public Result<?> rateLimitFallback(String fileName, String contentType, BlockException ex) {
        return Result.error(429, "Too many requests, please try again later");
    }
}
```

---

### Task 8: Build all modules and verify

- [ ] **Step 1: Full compile**

```bash
mvn clean compile
```

Expected: BUILD SUCCESS, no errors.

- [ ] **Step 2: Run existing tests (upload-service HTTP controller tests should still pass)**

```bash
mvn test -pl upload-service
```

Expected: Tests pass (requires MinIO running on localhost:9000).

---

### Task 9: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Update architecture section**

Replace the architecture description in CLAUDE.md to reflect Dubbo RPC:

- Module table: gateway now "Spring Boot Web (Dubbo Consumer)", no port for Dubbo
- Service dependency flow: `Gateway (8080) → [Dubbo RPC] → upload-service (20880) → MinIO (9000)`
- Add Dubbo 3.3.0 to key tech stack
- Note Triple protocol on port 20880
