# 前后端接口完整性审查报告

> 审查日期: 2026-06-14
> 审查方法: 提取前端所有 API 调用 vs 后端所有 Controller 端点，交叉比对

---

## 一、幻觉接口（前端调用但后端不存在）-- 仅 2 个

| # | 前端调用 | 调用位置 | 后端状态 | 严重度 |
|---|---------|---------|---------|--------|
| H-01 | `GET /api/user/{userId}` | SellerProfile.vue:95 | UserController 无此端点 | **P0** |
| H-02 | `GET /api/reports/my` | MyReports.vue:68, api/report.js:93 | ReportController 只有 POST | **P1** |

---

## 二、已确认存在的接口（排除幻觉）

以下接口最初怀疑是幻觉，但经确认存在于对应模块：

| 前端调用 | 所在模块 | Controller |
|---------|---------|-----------|
| `GET /api/notifications` | campus-common | NotificationController |
| `PUT /api/notifications/{id}/read` | campus-common | NotificationController |
| `PUT /api/notifications/read-all` | campus-common | NotificationController |
| `GET /api/notifications/unread-count` | campus-common | NotificationController |
| `POST /api/upload/image` | campus-common | UploadController |
| `POST /api/upload/video` | campus-common | UploadController |
| `GET /api/crazy-thursday/status` | campus-common | CrazyThursdayController |
| `POST /api/crazy-thursday/register` | campus-common | CrazyThursdayController |
| `POST /api/forum/posts/{id}/like` | campus-forum | ForumController |
| `GET /api/forum/posts/{id}/comments/tree` | campus-forum | ForumController |
| `POST /api/forum/comments/{id}/like` | campus-forum | ForumController |
| `POST /api/forum/posts/{id}/favorite` | campus-forum | ForumController |
| `GET /api/forum/posts/{id}/favorite/status` | campus-forum | ForumController |
| `GET /api/forum/favorites` | campus-forum | ForumController |
| `GET /api/forum/posts/mine` | campus-forum | ForumController |
| `POST /api/ai/chat` | campus-ai | AiChatController |
| `GET /api/ai/sessions` | campus-ai | AiChatController |
| `GET /api/ai/sessions/{id}/messages` | campus-ai | AiChatController |
| `DELETE /api/ai/sessions/{id}` | campus-ai | AiChatController |

---

## 三、死接口（后端存在但前端从未调用）

| # | 后端端点 | Controller | 说明 |
|---|---------|-----------|------|
| D-01 | `PUT /api/orders/{id}/confirm` | OrderController | 自提流程由聊天室直接创建订单，跳过确认步骤 |
| D-02 | `PUT /api/orders/{id}/pay-fee` | OrderController | 支付已改为线下，此接口保留兼容 |
| D-03 | `GET /api/reviews/me` | ReviewController | 前端未使用，可能预留 |
| D-04 | `GET /api/reviews/me/rating` | ReviewController | 前端未使用，可能预留 |
| D-05 | `POST /api/auth/bootstrap-admin` | AuthController | 系统初始化用，正常流程不调用 |

---

## 四、参数匹配检查

| 接口 | 前端参数 | 后端参数 | 匹配 |
|------|---------|---------|------|
| `POST /api/orders` | { goodsId, dealType, buyerId, deliveryFeePayer } | CreateOrderReq | ✓ |
| `GET /api/orders/my` | { role, status, inProgress, page, size } | @RequestParam | ✓ |
| `POST /api/disputes` | { orderId, reason, evidenceImages } | CreateDisputeReq | ✓ |
| `POST /api/reports` | { targetType, targetId, reason, evidence } | CreateReportReq | ✓ |
| `POST /api/chat/session` | ?goodsId= | @RequestParam goodsId | ✓ |
| `PUT /api/delivery/{id}/accept` | ?lat=&lng= | @RequestParam | ✓ |
| `PUT /api/goods/{id}` | body: { title, description, price, ... } | @RequestBody Goods | ✓ |

---

## 五、汇总

| 类别 | 数量 |
|------|------|
| 前端调用的唯一接口 | ~90 |
| 后端定义的唯一接口 | 88 |
| **真正的幻觉接口** | **2** |
| 死接口 | 5 |
| 参数不匹配 | 0 |

---

## 六、修复计划

| 优先级 | 修复项 | 工作量 |
|--------|--------|--------|
| **P0** | UserController 添加 `GET /api/user/{id}` 端点 | 小 |
| **P1** | ReportController 添加 `GET /api/reports/my` 端点 | 小 |
