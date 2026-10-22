# 业务服务

按业务所有权组织代码，每个目录包含本域契约与运行应用：

| 业务域 | 契约 | 可部署应用 | 拥有的数据 |
| --- | --- | --- | --- |
| account | 暂无跨服务用户契约 | auth-service | 账号、第三方绑定、会话、账号 Outbox |
| community | community-api | note-service | 笔记、评论、互动、浏览、内容 Outbox |
| media | upload-api | upload-service | 文件元数据、上传确认与对象引用 |
| search | search-api | search-service、search-sync-service | 搜索索引及投影规则 |

API 是库，不单独启动。领域之间依赖契约，不能依赖对方可执行服务 JAR。详情见 [项目结构与服务边界](../docs/项目结构与服务边界.md)。
