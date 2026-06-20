# Spring Cloud Upload Framework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a minimal Spring Cloud microservice that generates MinIO presigned URLs for frontend direct upload, with Sentinel rate limiting at the gateway.

**Architecture:** Maven multi-module project with three modules — `common` (shared response types), `gateway` (Spring Cloud Gateway + Sentinel), `upload-service` (MinIO presigned URL API). Gateway routes traffic via Nacos service discovery. Sentinel flow rules are stored in Nacos config for dynamic updates.

**Tech Stack:** Spring Boot 3.2.x, Spring Cloud 2023.0.x, Spring Cloud Alibaba 2023.0.x, Spring Cloud Gateway, Nacos 2.3.x (server), Sentinel 1.8.x (dashboard), MinIO 8.5.x (client), Java 17, Maven

---

## File Structure

```
spring-cloud-test/
├── pom.xml                              # Parent POM — dependency management for all modules
├── common/
│   ├── pom.xml                          # Minimal deps (Spring Web for HTTP status codes)
│   └── src/main/java/com/example/common/
│       └── Result.java                  # Generic response wrapper: code, msg, data
├── gateway/
│   ├── pom.xml                          # spring-cloud-starter-gateway, sentinel, nacos
│   └── src/main/java/com/example/gateway/
│       ├── GatewayApplication.java      # @SpringBootApplication + @EnableDiscoveryClient
│       └── src/main/resources/
│           └── application.yml          # Gateway routes, Nacos discovery, Sentinel config
├── upload-service/
│   ├── pom.xml                          # Spring Web, MinIO client SDK, Nacos discovery
│   └── src/main/java/com/example/upload/
│       ├── UploadServiceApplication.java
│       ├── controller/
│       │   └── UploadController.java    # GET /api/upload/presigned
│       ├── config/
│       │   └── MinioConfig.java         # MinioClient bean, reads minio.* properties
│       └── src/main/resources/
│           └── application.yml          # Nacos discovery, MinIO connection, server port
```

---

### Task 1: Parent POM with Dependency Management

**Files:**
- Create: `pom.xml`

- [ ] **Step 1: Write parent POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>spring-cloud-test</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>common</module>
        <module>gateway</module>
        <module>upload-service</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <spring-boot.version>3.2.6</spring-boot.version>
        <spring-cloud.version>2023.0.3</spring-cloud.version>
        <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
        <minio.version>8.5.10</minio.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring-cloud-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>io.minio</groupId>
                <artifactId>minio</artifactId>
                <version>${minio.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 2: Verify POM is valid**

Run: `mvn validate`

---

### Task 2: Common Module — Result<T> Response Wrapper

**Files:**
- Create: `common/pom.xml`
- Create: `common/src/main/java/com/example/common/Result.java`

- [ ] **Step 1: Write common module POM**

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

    <artifactId>common</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Write Result.java**

```java
package com.example.common;

public class Result<T> {

    private int code;
    private String msg;
    private T data;

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
```

- [ ] **Step 3: Build common module**

Run: `mvn -pl common compile`

---

### Task 3: Upload Service — Project Setup and MinIO Config

**Files:**
- Create: `upload-service/pom.xml`
- Create: `upload-service/src/main/java/com/example/upload/UploadServiceApplication.java`
- Create: `upload-service/src/main/java/com/example/upload/config/MinioConfig.java`
- Create: `upload-service/src/main/resources/application.yml`

- [ ] **Step 1: Write upload-service POM**

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

    <artifactId>upload-service</artifactId>

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
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>io.minio</groupId>
            <artifactId>minio</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Write UploadServiceApplication.java**

```java
package com.example.upload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class UploadServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UploadServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Write MinioConfig.java**

```java
package com.example.upload.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    private String endpoint;
    private String accessKey;
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
}
```

- [ ] **Step 4: Write application.yml for upload-service**

```yaml
server:
  port: 8081

spring:
  application:
    name: upload-service
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848

minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
```

- [ ] **Step 5: Compile the module**

Run: `mvn -pl upload-service compile`

---

### Task 4: Upload Service — Presigned URL Controller

**Files:**
- Create: `upload-service/src/main/java/com/example/upload/controller/UploadController.java`
- Create: `upload-service/src/test/java/com/example/upload/controller/UploadControllerTest.java`

- [ ] **Step 1: Write UploadController.java**

```java
package com.example.upload.controller;

import com.example.common.Result;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.presigned-expiry:300}")
    private int presignedExpiry;

    public UploadController(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @GetMapping("/presigned")
    public Result<Map<String, Object>> presignedUrl(
            @RequestParam String fileName,
            @RequestParam String contentType) {

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectKey = datePath + "/" + UUID.randomUUID() + "_" + fileName;

        try {
            String url = minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(presignedExpiry, TimeUnit.SECONDS)
                            .build());

            Map<String, Object> data = new HashMap<>();
            data.put("uploadUrl", url);
            data.put("objectKey", objectKey);
            data.put("expiresAt", System.currentTimeMillis() / 1000 + presignedExpiry);

            return Result.ok(data);
        } catch (Exception e) {
            return Result.error(500, "Failed to generate presigned URL: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Write UploadControllerTest.java**

```java
package com.example.upload.controller;

import com.example.upload.UploadServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = UploadServiceApplication.class,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "minio.endpoint=http://localhost:9000",
        "minio.access-key=minioadmin",
        "minio.secret-key=minioadmin",
        "minio.bucket=uploads"
    })
@AutoConfigureMockMvc
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void presignedUrl_shouldReturn400_whenMissingParams() throws Exception {
        mockMvc.perform(get("/api/upload/presigned"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void presignedUrl_shouldReturn200_whenValidParams() throws Exception {
        mockMvc.perform(get("/api/upload/presigned")
                        .param("fileName", "test.png")
                        .param("contentType", "image/png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.objectKey").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
    }
}
```

- [ ] **Step 3: Run tests to verify they fail (no MinIO server)**

Run: `mvn -pl upload-service test`
Expected: Tests may fail because MinIO is not running locally — this is expected at this stage. The controller wiring is verified when MinIO is available.

---

### Task 5: Gateway Module — Project Setup and Route Configuration

**Files:**
- Create: `gateway/pom.xml`
- Create: `gateway/src/main/java/com/example/gateway/GatewayApplication.java`
- Create: `gateway/src/main/resources/application.yml`

- [ ] **Step 1: Write gateway POM**

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

    <artifactId>gateway</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.csp</groupId>
            <artifactId>sentinel-datasource-nacos</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Write GatewayApplication.java**

```java
package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

- [ ] **Step 3: Write application.yml for gateway**

```yaml
server:
  port: 8080

spring:
  application:
    name: gateway
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
    gateway:
      routes:
        - id: upload-service
          uri: lb://upload-service
          predicates:
            - Path=/api/upload/**
    sentinel:
      datasource:
        ds1:
          nacos:
            server-addr: localhost:8848
            data-id: gateway-flow-rules
            group-id: DEFAULT_GROUP
            data-type: json
            rule-type: gw-flow
      scg:
        fallback:
          response-status: 429
          response-body: '{"code":429,"msg":"Too many requests, please try again later"}'
```

- [ ] **Step 4: Compile the gateway module**

Run: `mvn -pl gateway compile`

---

### Task 6: Build All Modules Together

- [ ] **Step 1: Full project compile**

Run: `mvn compile`

- [ ] **Step 2: Full project package**

Run: `mvn -DskipTests package`

---

### Task 7: Verify Against Spec

- [ ] **Step 1: Check that common module has Result<T> with static factory methods**

The `Result.java` in `common/` has `Result.ok(data)` and `Result.error(code, msg)`.

- [ ] **Step 2: Check that gateway routes /api/upload/** to upload-service via Nacos lb**

The gateway's `application.yml` has `uri: lb://upload-service` with `Path=/api/upload/**`.

- [ ] **Step 3: Check that Sentinel is configured with Nacos datasource for gw-flow rules**

The gateway's `application.yml` has `sentinel.datasource.ds1.nacos` with `data-id: gateway-flow-rules` and `rule-type: gw-flow`.

- [ ] **Step 4: Check that upload-service exposes GET /api/upload/presigned with fileName and contentType params**

`UploadController.presignedUrl()` maps to `@GetMapping("/presigned")` with `@RequestParam String fileName` and `@RequestParam String contentType`.

- [ ] **Step 5: Check that presigned URL response includes uploadUrl, objectKey, and expiresAt**

The controller returns a Map with keys `uploadUrl`, `objectKey`, `expiresAt`.

- [ ] **Step 6: Check that MinIO presigned URL uses PUT method with configurable expiry**

`MinioClient.getPresignedObjectUrl()` uses `Method.PUT` and reads `minio.presigned-expiry` (default 300s).

---
