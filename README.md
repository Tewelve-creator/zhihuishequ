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
| MinIO | 9000（API）/ 9001（控制台） | minioadmin / minioadmin |

首次启动会自动执行 `sql/schema.sql` + `sql/patch_staff_mobile.sql`。

若不用 Docker，请手动准备 MySQL / Redis，并单独启动 MinIO：

```bash
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS smart_community DEFAULT CHARSET utf8mb4;"
mysql -uroot -proot smart_community < sql/schema.sql
mysql -uroot -proot smart_community < sql/patch_staff_mobile.sql
```

**MinIO（图片存储，报修服务默认 `file.type=minio`）**

1. 下载 [minio.exe](https://dl.min.io/server/minio/release/windows-amd64/minio.exe) 放到 `tools/minio.exe`
2. 运行 `scripts\start-minio.bat`（API `http://127.0.0.1:9000`，控制台 `http://127.0.0.1:9001`）
3. 报修服务启动时会自动创建桶 `smart-community`
4. 可选：把历史本地图片迁到 MinIO（需已登录 token，或直连 8083）：
   `POST http://127.0.0.1:8083/file/migrate-local-to-minio`

切回本地盘：`$env:FILE_STORAGE_TYPE='local'`

（已有旧库可再执行 `sql/patch_*.sql` 增量补丁。）

### 3. 启动后端（端口）

各服务 `application.yml` 默认已指向远程开发机 `82.156.66.83`（MySQL / Redis / Nacos / MinIO）。本地覆盖示例：

```bash
# Windows PowerShell（改回本机中间件时）
$env:MYSQL_HOST='127.0.0.1'
$env:MYSQL_PASSWORD='root'
$env:REDIS_HOST='127.0.0.1'
$env:NACOS_ADDR='127.0.0.1:8848'
$env:MINIO_ENDPOINT='http://127.0.0.1:9000'
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

五人功能分工（报修 / 缴费 / 商户 / 邻里 / AI）见 [`docs/team-five-modules.md`](docs/team-five-modules.md)。

个人功能分支 → `dev` → `test` → `master`。

## 常见问题

1. **端口占用**：不要同时用 IDEA 和 `java -jar` 启动同一服务。  
2. **图片 404 / MinIO**：确认 MinIO 已启动（9000），`file.type=minio`；历史本地图会自动回退读盘，也可调用 `/file/migrate-local-to-minio` 迁移。  
3. **公告看不到**：定时公告未到点前，居民端不可见；物业端「公告通知」可管理。  
4. **MySQL**：默认连接 `82.156.66.83`，账号 `root` / 密码 `zxc123456`；可用 `MYSQL_HOST`、`MYSQL_PASSWORD` 覆盖。
