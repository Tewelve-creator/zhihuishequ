# 邻里倾诉 · 社区朋友圈 API 说明

复用表 `emotion_appeal`（`status=normal` 为朋友圈动态；`pending/processing/done` 为历史 AI 工单）。
附属表：`appeal_comment` / `appeal_like` / `appeal_user_mute` / `appeal_op_log`。
补丁脚本：`sql/apply_appeal_moments.py`（库名 `smart_community`）。

## 住户端（APP）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/appeal/feed` | 朋友圈动态流（仅未删除） |
| POST | `/appeal` | 发布动态；body: `content`, `imageUrls[]`, `houseId`, `houseLabel`, `nickname`；禁言时返回 403「您已被物业禁言，暂时无法发布动态」 |
| DELETE | `/appeal/{id}` | 删除自己的动态（软删） |
| GET | `/appeal/{id}/comments` | 评论列表 |
| POST | `/appeal/{id}/comments` | 发表评论；body: `content`；禁言拦截 |
| DELETE | `/appeal/comments/{id}` | 删除自己的评论 |
| POST | `/appeal/{id}/like` | 点赞/取消点赞；返回 `{ liked, likeCount }` |
| GET | `/appeal/mute/me` | 查询本人禁言状态 |

## 物业后台（admin-web）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/appeal/admin/list?filter=` | `all` / `normal` / `deleted` / `muted` |
| GET | `/appeal/admin/{id}` | 详情 + 全部评论（含已删） |
| DELETE | `/appeal/admin/{id}` | 违规删除动态；body 可选 `{ reason }` |
| DELETE | `/appeal/admin/comments/{id}` | 违规删除评论 |
| POST | `/appeal/admin/mute` | 禁言；`{ userId, duration: forever\|3d\|7d, reason }` |
| POST | `/appeal/admin/unmute` | 解除禁言；`{ userId }` |
| GET | `/appeal/admin/op-logs` | 操作日志（删除/禁言溯源） |

## 兼容旧工单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/appeal/tickets` | AI 倾诉工单列表（pending/processing/done） |
| PUT | `/appeal/{id}/handle` | 处理工单 |

实现类：`AppealController` + `AppealMomentsService`（community-repair）。
