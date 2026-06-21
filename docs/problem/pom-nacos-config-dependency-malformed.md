# POM 文件 nacos-config 依赖嵌套导致 Maven 编译失败

## 现象

`mvn compile` 失败，5 个模块报 `Malformed POM` 错误：

```
[ERROR] Malformed POM .../upload-service/pom.xml: Unrecognised tag: 'dependency'
[ERROR] Malformed POM .../leaf-service/pom.xml: Unrecognised tag: 'dependency'
[ERROR] Malformed POM .../note-service/pom.xml: Unrecognised tag: 'dependency'
[ERROR] Malformed POM .../auth-service/pom.xml: Unrecognised tag: 'dependency'
[ERROR] Malformed POM .../search-service/pom.xml: Unrecognised tag: 'dependency'
```

## 根因

`spring-cloud-starter-alibaba-nacos-config` 的 `<dependency>` 块被错误地嵌套在 `nacos-discovery` 的 `<dependency>` 块内部，导致 XML 结构不合法：

```xml
<!-- 错误：nacos-config 嵌套在 nacos-discovery 内部 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
<dependency>                                          <!-- 缺少 / 闭合 -->
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
</dependency>                                          <!-- 多余的闭合标签 -->
```

## 修复

将两个依赖恢复为独立的平级 `<dependency>` 块：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

同时对 `gateway/pom.xml` 补充了缺失的 `nacos-discovery` 和 `nacos-config` 依赖。

## 关键文件变更

| 文件 | 变更 |
|------|------|
| `upload-service/pom.xml` | 修复 nacos-config 嵌套 |
| `leaf-service/pom.xml` | 修复 nacos-config 嵌套 |
| `note-service/pom.xml` | 修复 nacos-config 嵌套 |
| `auth-service/pom.xml` | 修复 nacos-config 嵌套 |
| `search-service/pom.xml` | 修复 nacos-config 嵌套 |
| `gateway/pom.xml` | 新增 nacos-discovery + nacos-config 依赖 |
