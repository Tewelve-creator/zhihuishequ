# 第一阶段地基 API 说明

## 导入数据库

```bash
mysql -uroot -p < sql/phase1_foundation.sql
```

演示密码均为 `123456`。

| 角色 | 手机号 | tenantId | clientType |
|------|--------|----------|------------|
| 运营 | 13900000000 | 不传 | ADMIN |
| 阳光物业 | 13800000001 | 1 | ADMIN |
| 阳光网格 | 13800000011 | 1 | ADMIN |
| 阳光居民 | 13800000021 | 1 | APP |
| 星河物业 | 13800000002 | 2 | ADMIN |
| 星河居民 | 13800000022 | 2 | APP |

## 启动

1. Redis、MySQL  
2. `community-auth`（8081）  
3. `community-gateway`（8080）  

## 主要接口（经网关 `http://localhost:8080`）

### 认证
- `POST /auth/login` `{phone,password,tenantId?,clientType:APP|ADMIN}`
- `POST /auth/logout` Header: `Authorization: Bearer {token}`
- `POST /auth/register` 居民注册
- `GET /auth/profile`
- `GET /auth/users?page=&size=&role=`
- `POST /auth/users` 创建物业/网格
- `PUT /auth/users/{id}/status?status=`
- `GET /auth/roles`
- `GET /auth/menus/tree?clientType=`
- `GET /auth/permissions`
- `GET /auth/sessions` / `DELETE /auth/sessions/{token}`

### 租户
- `GET /auth/tenants/public` 登录页社区列表（白名单）
- `GET /auth/tenants` 分页（运营全量 / 租户仅本社区）
- `GET /auth/tenants/{id}`
- `POST/PUT/DELETE /auth/tenants` 仅运营

## 验收

1. 租户1居民登录后 `GET /auth/users`：仅租户1数据（或无权限则 403）  
2. 伪造 `X-Tenant-Id: 2` 访问：网关 403 禁止跨社区  
3. 运营登录：`GET /auth/tenants` 可见租户1+2  
