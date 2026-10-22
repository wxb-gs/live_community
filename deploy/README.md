# 部署资源

- elasticsearch/Dockerfile：安装 IK 分词插件的 Elasticsearch 镜像。
- canal/：独立 Canal 演示配置；通过根目录 Compose 的 canal-demo profile 启用。
- 各业务应用自己的 Dockerfile 与源码、构建产物保存在所属服务目录，构建上下文就是该服务目录。
- 根目录 docker-compose.yml 是基础设施统一入口；数据库初始化绑定、镜像构建和演示配置均指向当前目录结构。

迁移和启动步骤见 [实施与部署记录](../docs/微服务修复实施与部署.md)。
