# 第四阶段：SaaS 租赁全套（P0 到期降级 + 套餐拦截）

模块：`community-auth` + `community-gateway`（前端菜单隐藏：admin-web / app）

## 1. 建表 SQL

执行：`sql/phase4_saas.sql`（推荐）或历史脚本 `sql/auth_saas_module.sql`

核心表：

| 表 | 说明 |
|----|------|
| `sys_package` | 免费/基础/专业/旗舰四级套餐 |
| `sys_package_feature` | 套餐 ↔ 功能码 |
| `saas_order` | 续费/新购/升级订单 |
| `sys_tenant.features/status/expire_time` | 租户套餐快照 |
| `sys_message` | 续费提醒消息 |

**降级约束**：`free` 套餐仅含 `basic_repair,basic_notice`；到期未续费自动写入这两项。

## 2. 租户状态

| status | 含义 |
|--------|------|
| `active` | 正常使用，按套餐功能码放行 |
| `degraded` | 到期降级，仅公告+报修 |
| `frozen` | 超期 30 天冻结，高级功能全拦 |
| `disabled` | 运营禁用 |

运营改状态：`PUT /auth/tenants/{id}/status?status=degraded|frozen|active`

## 3. 网关套餐拦截（P0）

类：`PackageDowngradeFilter`（order=-90，鉴权之后）

拦截能力：

- 高级 AI：`/ai/**`（chat/image/emotion/lost/kb…）
- 数据大屏：`/stats/dashboard|/cockpit|/screen`
- Excel 导出：`/stats/export`、路径含 `/export`
- 服务商：`/repair/vendor`

配置（`community-gateway/application.yml`）：

```yaml
gateway:
  package:
    extra-rules: ${GATEWAY_PACKAGE_EXTRA_RULES:}   # /path=featureCode,...
```

实时生效：支付/到期任务写入 Redis `saas:lease:{tenantId}`，网关 `LeaseSnapshotReader` 叠加到 Session 快照，无需重新登录。

## 4. 订单 Mock 支付

| 接口 | 说明 |
|------|------|
| `POST /auth/orders` | 下单 new/renew/upgrade |
| `POST /auth/orders/{id}/pay` | Mock 支付：延长 expire_time、写入套餐 features、status=active、推送消息、刷新 Redis |

## 5. 定时任务

`TenantExpireJob`，cron：`auth.expire.cron` 默认 `0 0 1 * * ?`

1. 到期前 **30/7/1** 天推送续费提醒（`sys_message`，msg_type=`renew`）
2. 已过期 → `degraded` + 基础功能码
3. 过期超 30 天 → `frozen`
4. 手动触发：`POST /auth/tenants/expire-scan`

## 6. 套餐 CRUD

| 接口 | 说明 |
|------|------|
| `GET /auth/packages/public` | 公开价目 |
| `GET/POST/PUT/DELETE /auth/packages` | 运营 CRUD + featureCodes |
| `GET /auth/packages/features` | 功能码字典 |

## 7. 前端菜单隐藏

- **App**：`userStore.can(feature)`，首页入口 `locked` 隐藏高级能力
- **物业后台**：`menus.ts` + `filterMenusByFeature`；演示降级账号 `degraded1/123456`
- **Excel**：`exportExcel.ts` 校验 `data_export`

## 8. auth yml 要点

```yaml
auth:
  expire:
    cron: "0 0 1 * * ?"
    freeze-after-days: 30
    remind-days: 30,7,1
```
