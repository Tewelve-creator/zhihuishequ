# 第三阶段 community-ai API 说明

默认 `ai.provider=mock`（规则引擎模拟）；切换真实模型：

```yaml
ai:
  provider: http
  api-key: sk-xxx
  api-url: https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
  model: qwen-turbo
```

建表：`sql/phase3_ai.sql`  
服务端口：`8084`，网关路径：`/ai/**`

## 约束

| 能力 | 说明 |
|------|------|
| 租户隔离 | `ai_knowledge` 等表带 `tenant_id`，MyBatis 租户插件自动过滤 |
| 日额度 | Redis `ai:quota:{tenantId}:{date}`，默认 `ai.daily-quota=200` |
| Sentinel | 资源 `ai_chat/ai_audit/ai_image/ai_emotion/ai_lost/ai_summary`，默认 QPS=`ai.sentinel.qps=20` |

## REST 接口（community-ai）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/ai/chat` | 租户知识库优先问答 |
| GET/POST/DELETE | `/ai/kb` | 知识库 CRUD |
| POST | `/ai/audit` | 敏感词 + 摘要 |
| POST | `/ai/notice/summary` | 公告摘要（Feign 别名） |
| POST | `/ai/image` | 报修识图 |
| POST | `/ai/emotion` | 情绪识别，负面自动建调解工单并通知网格员 |
| POST | `/ai/lost/match` | 失物匹配 |
| POST/GET | `/ai/schedule` | 便民日程提醒 |
| POST | `/ai/schedule/dispatch` | 手动触发到期推送 |

### 请求示例

```json
POST /ai/chat
{ "question": "物业费怎么交" }

POST /ai/image
{ "imageUrl": "https://cdn.example.com/elev-broken.jpg" }

POST /ai/emotion
{ "text": "楼上太吵了，我快崩溃了" }

POST /ai/lost/match
{ "description": "黑色钥匙", "imageUrl": "/upload/lost/key1.jpg" }
```

## Feign（community-common）

接口：`com.community.common.feign.AiFeignClient`  
配置：`services.ai-url: http://127.0.0.1:8084`  
透传：`FeignTenantConfig` 自动带 `X-Tenant-Id` / `X-User-Id`

### repair 调用示例

```java
@EnableFeignClients(basePackages = "com.community.common.feign")
// RepairApplication

@Autowired
AiFeignClient aiFeignClient;

AiImageRepairRequest req = new AiImageRepairRequest();
req.setImageUrl(imageUrl);
R<AiImageRepairResponse> r = aiFeignClient.imageRepair(req);
// facilityType / suggestTitle 写回工单
```

联调入口：`POST /repair/ai/recognize` `{ "imageUrl": "..." }`  
创建建单时若有图无类型，会自动 Feign 填充。

### saas 调用示例

```java
AiAuditRequest req = new AiAuditRequest();
req.setContent(content);
req.setBizType("notice");
R<AiAuditResponse> r = aiFeignClient.noticeSummary(req);
```

联调入口：

- `POST /saas/ai/notice-preview` `{ "content": "..." }`
- `POST /saas/ai/chat` `{ "question": "..." }`

## Mock / 第三方入口

| 类 | 作用 |
|----|------|
| `MockAiClient` | 默认模拟：问答、识图、情绪、摘要、失物相似度 |
| `ThirdPartyAiClient` | `provider=http` 时 Chat Completions；`visionDescribe` 预留视觉 |

## 定时任务

`ScheduleRemindJob`：`ai.schedule.cron` 默认每分钟，到期写入 `sys_message`（msg_type=`schedule`）。
