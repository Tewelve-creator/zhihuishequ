# AI 智慧社区服务助手

多社区 SaaS · Spring Cloud 微服务 · UniApp + 管理后台

仓库：https://gitee.com/zhihuo_1/zhihuishequ  
推荐分支：`test`（完整可运行）

## 别人拉取后怎么跑

```bash
git clone -b test https://gitee.com/zhihuo_1/zhihuishequ.git
cd zhihuishequ
```

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- Docker Desktop（MySQL + Redis）
- 或本机已安装 MySQL 8 / Redis 7

### 2. 启动基础依赖

```bash
docker compose up -d
```

会启动：

| 服务 | 端口 | 账号 |
|------|------|------|
| MySQL | 3306 | root / root，库名 `smart_community` |
| Redis | 6379 | 无密码 |

首次启动会自动执行 `sql/schema.sql` + `sql/patch_staff_mobile.sql`。

若不用 Docker，请手动：

```bash
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS smart_community DEFAULT CHARSET utf8mb4;"
mysql -uroot -proot smart_community < sql/schema.sql
mysql -uroot -proot smart_community < sql/patch_staff_mobile.sql
```

（已有旧库可再执行 `sql/patch_*.sql` 增量补丁。）

### 3. 启动后端（端口）

在仓库根目录设置环境变量后，按模块启动（IDEA 或 jar 均可）：

```bash
# Windows PowerShell
$env:MYSQL_PASSWORD='root'
$env:NACOS_ENABLED='false'
```

| 模块 | 端口 | 说明 |
|------|------|------|
| community-gateway | 8080 | 统一入口，最后启动 |
| community-auth | 8081 | 登录 / 账号 / 消息 |
| community-saas | 8082 | 可选 |
| community-repair | 8083 | 报修 / 公告 / 文件 |
| community-ai | 8084 | AI（默认 Mock） |
| community-order | 8085 | 可选 |
| community-stats | 8086 | 可选 |

推荐启动顺序：`auth → repair → ai → gateway`（演示最少集）。

```bash
cd backend
mvn -pl community-gateway,community-auth,community-repair,community-ai -am -DskipTests package
# 再分别 java -jar 各模块 target/*.jar（勿与 IDEA 同时占同一端口）
```

网关地址：http://localhost:8080

### 4. 启动前端

**管理后台（物业工作端 / 运营端）**

```bash
cd admin-web
npm install
npm run dev
```

打开：http://localhost:5173/

**App（UniApp H5）**

```bash
cd app
npm install
npm run dev:h5
```

打开：http://localhost:5180/

### 5. 演示账号（密码均为 `123456`）

| 角色 | 手机号 | 社区 | 用途 |
|------|--------|------|------|
| 物业管理员 | 13800000001 | 阳光花园 | 管理后台物业端 + App 物业态 |
| 网格员 | 13800000011 | 阳光花园 | App 工作台 / 被指派工单 |
| 居民 | 13800000021 | 阳光花园 | App 报修 / 看公告 |
| 平台运营 | 13900000000 | — | 运营管理端（演示账号） |

管理后台请用**真实手机号登录**（不要用 admin1 演示号），才能看到真实报修/公告数据。

### 6. 可选：真实 AI 识图

默认 `ai.provider=mock`。启用通义视觉：

```bash
$env:AI_PROVIDER='http'
$env:AI_MODEL='qwen3.6-plus'
$env:AI_API_KEY='你的Key'
```

**勿把 API Key 提交到仓库。**

## 目录结构

```
backend/     微服务（gateway/auth/saas/repair/ai/order/stats/common）
admin-web/   Vue3 管理后台（物业工作端 + 运营端）
app/         UniApp 居民/物业双端
sql/         建库与补丁
docs/        接口与架构说明
```

## 协作说明

| 成员 | 主要目录 |
|------|----------|
| 马兴桦 | `backend/`、`sql/` |
| 张子煜 | `admin-web/` |
| 赵泽阳 | `app/` |
| 江泽 | `docs/` |

个人功能分支 → `dev` → `test` → `master`。

## 常见问题

1. **端口占用**：不要同时用 IDEA 和 `java -jar` 启动同一服务。  
2. **图片 404**：设置 `FILE_UPLOAD_DIR` 为固定目录，或使用默认 `~/.smart-community/uploads`。  
3. **公告看不到**：定时公告未到点前，居民端不可见；物业端「公告通知」可管理。  
4. **MySQL 密码**：默认与 `application.yml` 一致为 `root`；可用环境变量 `MYSQL_PASSWORD` 覆盖。
