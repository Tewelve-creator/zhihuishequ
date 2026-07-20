# 后端接口对接文档（网关统一入口）

**Base URL:** `http://localhost:8080`  
**鉴权头:** `Authorization: Bearer {token}` 或 `satoken: {token}`  
**租户头:** `X-Tenant-Id: {tenantId}`（登录返回的 tenantId，平台运营可省略）

统一响应：`{ "code": 0, "msg": "success", "data": ... }`，`code!=0` 为失败。

## 演示账号（密码均为 `123456`）

| 角色 | 手机号 | tenantId |
|------|--------|----------|
| 平台运营 | 13900000000 | 无 |
| 阳光物业 | 13800000001 | 1 |
| 阳光居民 | 13800000021 | 1 |
| 星河物业 | 13800000002 | 2 |

---

## Auth `community-auth :8081`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | body: `{phone,password,tenantId?}` |
| POST | `/auth/register` | body: `{phone,password,nickname,tenantId,roomNo}` |
| GET | `/auth/profile` | 当前用户 |
| GET | `/auth/sms?phone=` | 演示验证码固定 123456 |
| GET | `/auth/users?role=` | 本社区用户列表（物业） |
| POST | `/auth/users` | 创建工作人员/网格员 `{phone,role,nickname,password?}` |
| PUT | `/auth/users/{id}/status?status=` | 启用/禁用 |
| GET | `/auth/messages` | 当前用户消息（含续费提醒） |
| PUT | `/auth/messages/{id}/read` | 标记已读 |

## SaaS `community-saas :8082`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/saas/packages/public` | 公开套餐列表（白名单） |
| GET/POST | `/saas/packages` | 套餐查询/保存（运营） |
| GET/POST/PUT | `/saas/tenants` | 租户 CRUD（运营） |
| DELETE | `/saas/tenants/{id}` | 删除租户 |
| GET | `/saas/tenant/current` | 当前租户套餐与剩余天数 |
| POST | `/saas/tenant/expire-scan` | 手动触发到期扫描+续费提醒 |
| GET | `/saas/tenants/expiring` | 30天内到期租户列表（运营） |
| GET/POST | `/saas/sensitive-words` | 敏感词列表/新增 |

## 报修/公告/访客/活动 `community-repair :8083`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST/GET | `/repair/orders` | 创建/列表 |
| GET | `/repair/orders/{id}` | 详情 |
| PUT | `/repair/orders/{id}/handle` | 处理工单 |
| POST/GET | `/notice` | 公告发布（强制敏感词审核）/列表 |
| DELETE | `/notice/{id}` | 删除公告 |
| POST/GET | `/visit` | 访客预约创建/列表 |
| PUT | `/visit/{id}/audit` | 审核 `{status,remark}` |
| PUT | `/visit/{id}/checkin` | 访客核销 |
| POST/GET | `/activity` | 活动发布/列表 |
| POST | `/activity/{id}/signup` | 报名 |
| GET | `/activity/{id}/signups` | 报名名单 |
| PUT | `/activity/signup/{signupId}/check` | 活动核销 |
| POST/GET | `/appeal` | 情绪诉求创建/列表 |
| PUT | `/appeal/{id}/handle` | 网格员处理 |
| POST | `/file/upload` | 图片上传 form-data:file，返回 url |

## AI `community-ai :8084`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/ai/chat` | `{question}` 智能问答 |
| POST | `/ai/image` | `{imageUrl}` 识图报修建议 |
| POST | `/ai/emotion` | `{text}` 情绪分析（负向自动建诉求） |
| POST | `/ai/audit` | `{content}` 内容审核+摘要 |
| POST | `/ai/lost` | 发布失物 `{title,description,contact,imageUrl}` |
| POST | `/ai/lost/match` | 失物匹配 `{description,imageUrl}` |
| GET/POST | `/ai/kb` | 知识库列表/保存 |
| DELETE | `/ai/kb/{id}` | 删除知识库 |

AI 真模型：环境变量 `AI_PROVIDER=http` + `AI_API_KEY=sk-xxx`（通义兼容模式默认）。

## 订单 `community-order :8085`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/order/create` | `{packageId,cycle,tenantId?}` |
| POST | `/order/{id}/pay` | 模拟支付并生效套餐 |
| GET | `/order/list` | 订单列表 |

## 统计 `community-stats :8086`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/stats/tenant/dashboard` | 本社区大屏 |
| GET | `/stats/saas/dashboard` | SaaS 营收总大屏 |
| GET | `/stats/export/tenant` | 导出本社区报修（需 data_export） |

## 套餐到期行为（网关 P0）

- `degraded/expired`：拦截高级 AI、大屏、导出等，保留基础报修/公告
- `frozen`：仅允许 GET 查看历史 + 续费相关接口
- 定时任务：每天 01:00 扫描到期（也可 `POST /saas/tenant/expire-scan`），到期前 30/7/1 天给物业管理员写 `sys_message` 续费提醒
- 增量 SQL：`sql/patch_v2_message.sql`、`sql/patch_v3_visit_activity.sql`
