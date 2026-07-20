# community-auth：SaaS 租户 / 套餐租赁 / 认证权限

网关前缀：`http://localhost:8080`（直连 auth：`http://localhost:8081`）  
鉴权头：`Authorization: Bearer <token>`

---

## 1. 建表与初始化

执行顺序：

1. `sql/phase1_foundation.sql`（若未执行）
2. `sql/auth_saas_module.sql`（套餐 / 功能关联 / 订单 / 登录日志 / 消息 / 租户增强列 / 菜单权限）

演示账号（密码均为 `123456`）：

| 手机号 | 角色 |
|--------|------|
| 13900000000 | 平台运营 |
| 13800000001 | 物业管理员（租户1） |
| 13800000021 | 居民（租户1） |

---

## 2. 认证与账号

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 密码或验证码登录 |
| POST | `/auth/logout` | 注销 |
| POST | `/auth/register` | 居民注册 |
| POST | `/auth/sms/send` | 发送验证码（演示返回 code） |
| GET | `/auth/sms?phone=` | 兼容旧接口 |
| GET | `/auth/profile` | 当前用户（含 features/tenantStatus） |
| GET | `/auth/users` | 用户分页 |
| POST | `/auth/users` | 创建物业/网格账号 |
| PUT | `/auth/users/{id}/status` | 启停用户 |
| GET | `/auth/roles` | 角色列表 |
| GET | `/auth/menus/tree` | 菜单树 |
| GET | `/auth/permissions` | 权限码 |
| GET | `/auth/messages` | 消息（含续费提醒） |
| PUT | `/auth/messages/{id}/read` | 标记已读 |
| GET | `/auth/login-logs` | 登录日志 |
| GET/DELETE | `/auth/sessions` | 会话管理 |

### 登录示例

密码登录：

```json
{
  "phone": "13800000001",
  "password": "123456",
  "tenantId": 1,
  "clientType": "ADMIN",
  "loginType": "password"
}
```

验证码登录：

```json
{
  "phone": "13800000001",
  "smsCode": "123456",
  "tenantId": 1,
  "clientType": "ADMIN",
  "loginType": "sms"
}
```

---

## 3. 租户（SaaS 运营 / 物业）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/auth/tenants/public` | 登录页社区列表（免登） |
| GET | `/auth/tenants` | 租户分页（运营脱敏联系电话；物业仅本租户） |
| GET | `/auth/tenants/stats` | 运营汇总统计（无隐私明细） |
| GET | `/auth/tenants/expiring?withinDays=30` | 即将到期列表 |
| GET | `/auth/tenants/lease/mine` | 物业：自身套餐与到期预警 |
| GET | `/auth/tenants/{id}/lease` | 指定租户租赁视图 |
| POST | `/auth/tenants` | 新增租户（绑定套餐，默认试用 7 天） |
| PUT | `/auth/tenants` | 修改租户 |
| PUT | `/auth/tenants/{id}/status?status=` | `active/disabled/degraded/frozen` |
| DELETE | `/auth/tenants/{id}` | 删除 |
| POST | `/auth/tenants/expire-scan` | 手动触发到期扫描 |

---

## 4. 四级套餐

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/auth/packages/public` | 公开套餐+功能码（免登） |
| GET | `/auth/packages` | 运营：全部套餐 |
| GET | `/auth/packages/features` | 功能码字典 |
| GET | `/auth/packages/{id}` | 详情 |
| POST/PUT | `/auth/packages` | 创建/更新（含 featureCodes） |
| DELETE | `/auth/packages/{id}` | 删除 |

套餐编码：`free` / `basic` / `pro` / `flagship`

保存示例：

```json
{
  "name": "专业版",
  "code": "pro",
  "priceMonth": 299,
  "priceQuarter": 799,
  "priceYear": 2999,
  "aiQuota": 500,
  "sortNo": 3,
  "featureCodes": ["basic_repair","basic_notice","ai_chat","ai_image_repair","ai_emotion","ai_lost_match","data_dashboard","data_export","vendor_manage"]
}
```

---

## 5. 租赁订单（新购 / 续费 / 升级）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/auth/orders` | 订单分页（物业仅本租户） |
| POST | `/auth/orders` | 创建订单 |
| POST | `/auth/orders/{id}/pay` | 演示支付并生效 |
| POST | `/auth/orders/{id}/cancel` | 取消待支付订单 |

创建示例：

```json
{
  "tenantId": 1,
  "packageId": 3,
  "cycle": "year",
  "orderType": "renew"
}
```

- `cycle`：`month` / `quarter` / `year`
- `orderType`：`new` / `renew` / `upgrade`（升级要求目标 sortNo 更高）
- 支付成功：写入 `expire_time`、同步 `features`/`ai_quota`，状态置 `active`

---

## 6. 定时任务与降级规则

- Cron：`auth.expire.cron`（默认每天 01:00）
- 到期前 **30 / 7 / 1** 天：向物业管理员推送 `sys_message`（同标题同天去重）
- 已过期：`status=degraded`，功能切到 `free` 套餐功能码
- 过期超过 30 天：`status=frozen`
- 网关 `PackageDowngradeFilter`：按 `LoginUser.features` 拦截 AI/大屏/导出等路径

---

## 7. 多租户隔离

- MyBatis-Plus 行级 `tenant_id`（全局表见 `CommunityTenantLineHandler`）
- 网关强制 Token 内租户，伪造 `X-Tenant-Id` → 403
- 运营可跨租户汇总；列表联系电话脱敏，不落隐私明细接口

---

## 8. yml 要点（community-auth）

```yaml
server.port: 8081
auth.sms.use-fixed: true
auth.sms.fixed-code: "123456"
auth.expire.cron: "0 0 1 * * ?"
sa-token + redis 与网关共享
```
