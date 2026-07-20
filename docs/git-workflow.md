# Git 三分支协作规范（防冲突）

## 主干分支

- `dev`：日常开发汇总
- `test`：全量测试（由 dev 合并）
- `master`：交付稳定版（由 test 合并，禁止直接提交）

## 目录所有权（最重要）

| 成员 | 只改这些目录 | 分支前缀 |
|------|--------------|----------|
| 马兴桦 | `backend/`、`sql/`、根 `docker-compose.yml` / `.gitignore` / `README.md` | `feature/backend-*` |
| 赵泽阳 | `app/` | `feature/app-*` |
| 张子煜 | `admin/` | `feature/admin-*` |
| 姜泽 | `docs/`（可改测试/演示文档；`backend-api.md` 联调变更先沟通） | `docs/test-*` |

**禁止：** 改别人目录；在同一文件两边同时改。

## 推荐流程

```bash
git checkout dev
git pull
git checkout -b feature/backend-xxx   # 或 feature/app-xxx / feature/admin-xxx
# 开发 + 自测
git add <仅自己目录的文件>
git commit -m "feat(backend): xxx"
git checkout dev
git merge feature/backend-xxx
# 由马兴桦审核合并远程；迭代结束后：dev -> test -> master
```

## 马兴桦后端分支对照

- `feature/backend-gateway-tenant`
- `feature/backend-user-auth`
- `feature/backend-saas-tenant`
- `feature/backend-repair-notice`
- `feature/backend-ai-service`
- `feature/backend-order-package`
- `feature/backend-stats-dashboard`

当前首版已在 `backend/` 一次落地完整可编译工程，后续按功能在对应分支迭代即可。

## 冲突预防检查清单

1. 提交前 `git status` 确认没有别人的目录
2. 不要提交 `node_modules/`、`target/`、`.env`
3. 合并前先 `git pull origin dev`
4. 接口变更只改 `docs/backend-api.md`，并通知前后端
