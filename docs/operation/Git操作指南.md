# Git 操作指南

开发过程中常见的 Git 操作记录，包含场景、命令和注意事项。

---

## 1. 移除已被 .gitignore 忽略但已提交到本地库的文件

**场景**：文件已提交到 Git，后来添加到 `.gitignore`，但 Git 仍然在追踪它们。

### 为什么 .gitignore 不生效？

`.gitignore` **只对未追踪（untracked）文件生效**。一旦文件被 `git add` 加入索引（index），Git 就会持续追踪它，后续修改 `.gitignore` 对它无效。

可以用一句话理解：**`.gitignore` 是门卫，只拦没进门的人，已经进来的不管。**

### 为什么 git rm -r --cached . 可以解决问题？

Git 追踪文件分为两个区域：

| 区域 | 说明 |
|------|------|
| 工作区（working tree） | 磁盘上真实的文件 |
| 暂存区/索引（index / staging） | Git 追踪列表，决定哪些文件被版本控制 |

核心思路：**把索引清空，再用 `git add .` 重建，重建时 `.gitignore` 就会生效。**

```
执行前：索引包含所有文件（含 target/*.class 等）
  ↓ git rm -r --cached .    清空索引，工作区文件不受影响
  ↓ git add .               重新扫描工作区，.gitignore 过滤掉不应追踪的文件
执行后：索引只包含 .gitignore 允许的文件
```

### 命令拆解：git rm -r --cached .

| 参数 | 含义 |
|------|------|
| `rm` | 从 Git 中移除文件 |
| `-r` | 递归，处理目录 |
| `--cached` | **只操作索引，不动工作区** — 这是关键，不加会删除本地文件！ |
| `.` | 当前目录下所有文件 |

> `--cached` 是安全阀：没有它，`git rm` 会把工作区的文件一并删掉。

### git rm -r --cached . 会删除所有文件吗？

**不会删本地文件，但会清空整个 Git 索引。** 这听起来危险，实际是安全的：

1. 工作区文件原封不动
2. `git add .` 立刻重建索引，且只添加 `.gitignore` 允许的文件
3. 整个操作可逆：只要没 commit，`git reset` 即可恢复

**唯一要注意的**：commit 之后，其他人 pull 会看到这些文件被删除（因为 Git 认为你"不再需要追踪它们"）。这是正确的行为，但需要提前和团队沟通。

### 全局清理

```bash
# 步骤 1：清空索引（本地文件不动）
git rm -r --cached .

# 步骤 2：重建索引，.gitignore 规则生效
git add .

# 步骤 3：提交
git commit -m "chore: stop tracking files now in .gitignore"
```

### 指定目录清理

```bash
git rm -r --cached <目录名>/
git add <目录名>/
git commit -m "chore: stop tracking ignored files in <目录名>"
```

### 跳过个别文件（保留本地但忽略远端变更）

当团队共享一个配置文件模板，但每个人需要本地修改时，不要用 `.gitignore`（那会让文件彻底不被追踪），用 `skip-worktree`：

```bash
git update-index --skip-worktree <file>    # 忽略后续修改
git update-index --no-skip-worktree <file> # 恢复追踪
```

---

## 2. 撤销操作

### 撤销工作区修改（未 add）

```bash
git checkout -- <file>       # 单个文件
git checkout -- .             # 所有文件
```

### 撤销暂存区（已 add 未 commit）

```bash
git reset HEAD <file>        # 单个文件
git reset HEAD .              # 所有文件
```

### 撤销最近一次 commit（保留修改）

```bash
git reset --soft HEAD~1       # 修改回到暂存区
git reset --mixed HEAD~1      # 修改回到工作区（默认）
```

### 撤销最近一次 commit（丢弃修改）

```bash
git reset --hard HEAD~1       # 彻底丢弃
```

---

## 3. 修改最后一次提交

### 修改 commit message

```bash
git commit --amend -m "新的提交信息"
```

### 补充漏掉的文件

```bash
git add <漏掉的文件>
git commit --amend --no-edit    # 不修改 message，只补充文件
```

> 如果 commit 已经 push，需要 `git push --force`，谨慎使用。

---

## 4. 分支操作

### 创建并切换到新分支

```bash
git checkout -b feature/xxx
```

### 删除分支

```bash
git branch -d feature/xxx          # 已合并的分支
git branch -D feature/xxx          # 强制删除
```

### 删除远程分支

```bash
git push origin --delete feature/xxx
```

### 重命名分支

```bash
git branch -m old-name new-name
```

### 拉取远程分支

```bash
git fetch origin
git checkout -b feature/xxx origin/feature/xxx
```

---

## 5. 合并与 Rebase

### 合并分支

```bash
git checkout main
git merge feature/xxx
```

### Rebase（保持线性历史）

```bash
git checkout feature/xxx
git rebase main
```

### 冲突处理

```bash
# 冲突发生后：
git add <解决冲突的文件>
git rebase --continue     # rebase 时
git merge --continue      # merge 时

# 放弃本次操作：
git rebase --abort
git merge --abort
```

---

## 6. 暂存（Stash）

### 暂存当前修改

```bash
git stash                    # 暂存所有修改
git stash save "描述信息"    # 带说明的暂存
```

### 查看暂存列表

```bash
git stash list
```

### 恢复暂存

```bash
git stash pop                # 恢复最近一次并删除记录
git stash apply              # 恢复最近一次但保留记录
git stash pop stash@{1}      # 恢复指定 stash
```

---

## 7. 查看历史

### 简洁日志

```bash
git log --oneline -10                    # 最近 10 条，单行
git log --oneline --graph --all          # 所有分支的图形化日志
```

### 查看文件变更

```bash
git log --follow -p <file>              # 追踪文件历史（含重命名）
git blame <file>                         # 查看每行代码的作者
```

### 查看某次提交的内容

```bash
git show <commit-hash>                  # 完整内容
git show <commit-hash> --stat           # 仅统计
git show --name-only <commit-hash>      # 仅文件名
```

---

## 8. Tag 标签

### 创建标签

```bash
git tag v1.0.0                          # 轻量标签
git tag -a v1.0.0 -m "版本说明"         # 附注标签
```

### 推送标签

```bash
git push origin v1.0.0                  # 推送单个
git push origin --tags                  # 推送所有
```

### 删除标签

```bash
git tag -d v1.0.0                       # 本地删除
git push origin --delete v1.0.0         # 远程删除
```

---

## 9. 远程仓库

### 修改远程地址

```bash
git remote set-url origin <新地址>
```

### 查看远程信息

```bash
git remote -v
git remote show origin
```

### 同步上游分支

```bash
git fetch origin
# 或设置跟踪后直接 pull
git branch --set-upstream-to=origin/main main
git pull --rebase
```

---

## 10. Cherry-pick

### 挑取单个提交

```bash
git cherry-pick <commit-hash>
```

### 挑取多个提交

```bash
git cherry-pick <hash1> <hash2> <hash3>
git cherry-pick <hash1>..<hash4>        # 不包含 hash1
git cherry-pick <hash1>^..<hash4>       # 包含 hash1
```
