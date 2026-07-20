# Uniapp App 前端（赵泽阳）

居民端 + 社区工作人员端，对接网关 `http://localhost:8080`，接口见 `docs/backend-api.md`。

## 技术栈

- uni-app + Vue3
- 请求封装：`utils/request.js`（自动带 `Authorization`、`X-Tenant-Id`）
- 会话：`store/user.js`（token / 角色 / 套餐 features / 租户状态）

## 运行方式

### 方式一：HBuilderX（推荐）

1. 用 HBuilderX 打开本 `app` 目录
2. 运行 → 运行到浏览器 / 手机 / 模拟器
3. 确保后端网关已启动（`localhost:8080`）

### 方式二：CLI

```bash
cd app
npm install
npm run dev:h5
```

H5 开发代理：`/api` → `http://localhost:8080`

## 功能对照（赵泽阳职责）

| 规划分支 | 页面 / 能力 |
|----------|-------------|
| `feature/app-login-tenant` | 登录、注册、租赁社区选择 |
| `feature/app-home` | 居民首页、快捷入口、公告/消息摘要 |
| `feature/app-ai-chat` | AI 社区小助手对话 |
| `feature/app-repair-upload` | 拍照上传、AI 识图报修、工单列表/详情 |
| `feature/app-notice-activity` | 公告、活动报名与核销 |
| `feature/app-visitor-payment` | 访客预约、物业缴费演示、套餐续费支付 |
| `feature/app-staff-workbench` | 工作人员工作台、知识库、诉求处理 |
| `feature/app-package-display` | 套餐公示、功能权限显隐、到期状态 |

当前开发分支：`feature/app-core`（上述模块一次落地，便于联调）。

## 演示账号（密码均为 `123456`）

| 角色 | 手机号 | 社区 |
|------|--------|------|
| 阳光居民 | 13800000021 | 阳光花园（tenantId=1） |
| 工作人员 | 13800000031 | 同上 |
| 网格员 | 13800000011 | 同上 |
| 物业管理员 | 13800000001 | 同上 |
| 星河居民 | 13800000022 | 星河湾（tenantId=2） |

## 套餐权限显隐

登录返回的 `features`（逗号分隔）驱动入口显示：

- 无 `ai_chat` → AI 助手锁定
- 无 `ai_image_repair` → 报修仍可用，但无识图
- 无 `ai_emotion` / `ai_lost_match` → 对应模块提示升级
- `tenantStatus=degraded/frozen` → 首页条幅提醒，冻结禁止新建工单

## 目录结构

```
app/
├── api/           # 按模块封装接口
├── pages/         # 业务页面
├── store/         # 用户会话
├── utils/         # request / 常量
├── static/        # tabBar 图标
├── styles/        # 全局样式
├── pages.json
├── manifest.json
└── App.vue
```
