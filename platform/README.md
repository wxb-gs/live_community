# 技术平台

| 目录 | 模块 | 用途 |
| --- | --- | --- |
| common-core | common-core | 通用响应类型，不承载业务事件 |
| security | security-core、service-security、rpc-security | 纯 JWT 校验、Servlet 服务认证、Dubbo 内部签名；分开依赖以兼容 Reactive 网关 |
| messaging | event-outbox | 复用事务 Outbox 存储与投递逻辑 |
| id | leaf-api、leaf-client、leaf-service | ID 契约、批量客户端与唯一的发号进程 |

除 leaf-service 外，这里的模块均为库。平台模块不能依赖业务服务实现。详情见 [项目结构与服务边界](../docs/项目结构与服务边界.md)。
