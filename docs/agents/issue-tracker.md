# Issue tracker: GitHub

仓库：wxb-gs/live_community
使用 gh CLI 操作 GitHub Issues，命令显式指定该仓库。

## 发布约定

- to-spec：创建规格 Issue。
- to-tickets：拆分方案经用户确认后，按依赖顺序创建任务。
- 多行正文使用 --body-file，保留真实换行。
- 发布前检查是否已有对应 Issue，避免重复。
- to-spec/to-tickets 使用其要求的 ready-for-agent 标签；
  发布时若缺少该标签，先创建这个标签。
- 当前未配置 triage 技能的五态分流体系。

## 任务关系

- 优先使用 GitHub 原生子任务与阻塞关系。
- 原生关系不可用时，在正文写明 Parent 和 Blocked by。
- 创建依赖关系时区分 Issue 编号与数据库 ID。
- 不自动关闭或改写父 Issue。

## Pull requests as a triage surface

PRs as a request surface: no.
