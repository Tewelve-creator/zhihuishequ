# 后端微服务（马兴桦）

Spring Cloud Alibaba · Gateway · 多租户 · AI · SaaS 租赁

## 模块

| 模块 | 端口 | 说明 |
|------|------|------|
| community-gateway | 8080 | 鉴权、租户拦截、套餐降级 |
| community-auth | 8081 | 登录注册 |
| community-saas | 8082 | 租户/套餐 |
| community-repair | 8083 | 报修+公告 |
| community-ai | 8084 | AI 能力 |
| community-order | 8085 | 租赁订单 |
| community-stats | 8086 | 双大屏 API |

## 本地启动

```bash
# 根目录
docker compose up -d
# 导入 sql/schema.sql（compose 首次会自动导入）

cd backend
mvn -DskipTests package

# 分终端启动各服务 Main 类，或：
java -jar community-gateway/target/community-gateway-1.0.0-SNAPSHOT.jar
# ... 同理启动其余 jar
```

Nacos 默认关闭（`enabled: false`），网关用固定端口路由，方便竞赛演示。需要注册中心时再打开并改 `lb://`。
