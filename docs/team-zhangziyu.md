# 2.3.3 张子煜（Web 管理后台前端开发）

本人在智慧社区项目中主要负责 Web 管理后台前端开发工作。界面与信息架构对齐 Axure 原型：管理端（[2270660](https://www.axureshop.com/ys/2270660)）、物业端（[2317513](https://www.axureshop.com/ys/2317513)）。可视化职责卡片见 [`zhangziyu-card.html`](./zhangziyu-card.html)，前端工程见 `admin-web/`。

## 一、运营管理端（2270660）

完成 PC 运营管理端页面开发，覆盖数据监控驾驶舱、基础信息管理（园区/楼栋房屋/住户/场地）、物业管理（工单/巡检/设备/台账）、缴费管理（收银台/收费项目/账单/账务/仪表）、招商管理（房态/租赁/合同/客户/租客）、智慧物联（门禁/停车/充电桩）、统计报表与系统管理（组织账号/角色权限）。

## 二、物业工作端（2317513）

将物业侧报修、巡检、工单、设备维保等能力落地为 Web 物业工作端，包括工作台看板、住户管理、迁入迁出审批、报事报修、缴费管理、临时访客、巡检任务、设备维保、收款通知与公告通知等模块。

## 三、数据可视化与通用能力

基于 ECharts 实现双端数据可视化，并配套时间段筛选与 Excel 导出；完成后台角色权限控制页面，对接微服务网关接口，处理后台页面交互问题。

## 负责分支

| 分支名 | 对应模块 |
|--------|----------|
| `feature/admin-login-layout` | 后台登录、后台布局 |
| `feature/admin-tenant-dashboard` | 物业租户后台首页/工作台 |
| `feature/admin-user-manage` | 用户管理 |
| `feature/admin-repair-manage` | 工单管理 |
| `feature/admin-notice-activity` | 公告活动管理 |
| `feature/admin-saas-tenant` | SaaS 租户管理 |
| `feature/admin-saas-package` | 租赁套餐配置 / 续费套餐 |
| `feature/admin-order-finance` | 订单财务管理 |
| `feature/admin-stats-screen` | 社区数据大屏、营收可视化大屏 |

## 职责范围（摘要）

后台登录、后台布局、物业租户后台、用户管理、工单管理、公告活动管理、SaaS 租户管理、套餐管理、订单财务管理、社区数据大屏、营收大屏。
