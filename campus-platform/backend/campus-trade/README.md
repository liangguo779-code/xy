# campus-trade

交易模块，负责商品管理、订单流程、即时聊天、收藏/关注/拉黑、评价、举报、纠纷、跑腿配送及分类管理，是项目最核心的业务模块。

## 功能概述

- 商品发布、编辑、上下架、擦亮、搜索（Elasticsearch）、推荐
- 订单全流程：创建→确认→支付→配送→收货→评价
- 热卖商品（管理员发布、Redis 队列开单、二次确认后 worker 消费落库进派单大厅）
- 买家/卖家即时聊天（WebSocket）
- 收藏商品、关注用户、拉黑用户
- 交易评价（含申诉、回复）
- 举报与纠纷处理
- 跑腿配送（接单、取货、送达、GPS 定位）
- 商品分类树
- 管理员：商品审核、订单监控、纠纷处理、举报处理、评价管理、ES 索引重建

## 接口列表

### GoodsController `/api/goods`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/goods` | 商品列表（支持关键词/分类/类型/价格/成色/排序筛选） |
| GET | `/api/goods/my` | 我的商品列表（按状态筛选） |
| GET | `/api/goods/recommend` | 个性化推荐商品 |
| GET | `/api/goods/{id}` | 商品详情（记录浏览历史） |
| POST | `/api/goods` | 发布商品 |
| PUT | `/api/goods/{id}` | 编辑商品 |
| DELETE | `/api/goods/{id}` | 下架商品 |
| PUT | `/api/goods/{id}/sold` | 标记已售出 |
| PUT | `/api/goods/{id}/refresh` | 擦亮商品（每天限1次，提升排名） |
| PUT | `/api/goods/{id}/reshelf` | 重新上架已下架商品 |
| GET | `/api/goods/browse-history` | 浏览历史（分页） |

### OrderController `/api/orders`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/orders` | 创建订单 |
| PUT | `/api/orders/{id}/confirm` | 卖家确认订单 |
| PUT | `/api/orders/{id}/complete` | 完成自提订单（需验证码） |
| PUT | `/api/orders/{id}/pay-fee` | 买家支付配送费 |
| PUT | `/api/orders/{id}/confirm-receive` | 买家确认收货 |
| PUT | `/api/orders/{id}/confirm-delivery` | 确认配送方式（指定费用承担方） |
| PUT | `/api/orders/{id}/cancel` | 取消订单 |
| POST | `/api/orders/review` | 创建订单评价 |
| GET | `/api/orders/my` | 我的订单（按买家/卖家身份、状态筛选） |
| GET | `/api/orders/{id}` | 订单详情 |

### 热卖商品订单（OrderController，同前缀 `/api/orders`）

热卖商品由管理员发布、多库存、线下收款，走「**list 即开单队列**」模型：下单只产生 Redis 消息（票据），
**订单的「产生」= worker 消费 list 的时刻**。确认截止默认 60s（`trade.hot-order.confirm-timeout`），超时自动取消并释放库存。

```
下单   Lua(查库存+DECRBY+RPUSH 消息进 hot:pending:{goodsId})   ← 无 DB 写
确认   Lua(LREM pending + RPUSH hot:confirmed:queue)            ← 无 DB 写
消费   HotOrderConsumer: LPOP→processing → @Transactional 落库
       (INSERT order(status=6) + goods.stock/sales + createDeliveryOrder)
       → afterCommit 才 LREM processing  ★LREM 绝不能进事务体（Redis 不回滚）★
超时   pending 桶扫描比对消息 expireAt → Lua(LREM + INCRBY 回补)
```

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/orders/hot` | 热卖下单：Lua 扣库存+票据入待确认队列，返回 `HotTicketVO`（无 `id`，按 `orderNo` 跳票据页） |
| PUT | `/api/orders/hot/{orderNo}/confirm-purchase?goodsId=` | 二次确认：消息迁入已确认队列，返回「处理中」；worker 随后落库进派单大厅 |
| PUT | `/api/orders/hot/{orderNo}/cancel-hot?goodsId=` | 待确认期取消并释放库存；确认后不可自助取消，走人工/纠纷 |
| GET | `/api/orders/hot/ticket/{orderNo}` | 票据详情（处理中轮询；已落库回填 `orderId` 供跳转） |
| GET | `/api/orders/hot/tickets/my` | 我的热卖票据（「我的订单」合并展示用，票据排前） |

Redis key（`HotSaleStockService` + `resources/lua/`）：

| Key | 作用 |
|---|---|
| `hot:stock:{goodsId}` | 可售库存计数（扣减权威，DB 只作账本） |
| `hot:pending:{goodsId}` | **待确认桶**，成员 = 订单消息 JSON |
| `hot:confirmed:queue` | **已确认队列**，等 worker 落库 |
| `hot:order:processing` | 消费中转区（已认领、尚未摘除的可靠消费缓冲） |

并发保证：

- 下单：Lua 原子「查库存+DECRBY+RPUSH 消息」，同商品同买家限一张在途票（Lua 内返回 -2，与 push 原子）
- **确认 vs 超时**：两边对同一成员串 LREM，恰好一方拿到 1（原子仲裁）；确认迁桶、释放回补
- **消费幂等**：`selectCount(orderNo)` 短路 → `UNIQUE(order_no)` 兜底（并发 worker 抛 `DuplicateKeyException` 视为成功）→ processing 摘除幂等
- **崩溃恢复**：认领后崩（无 DB 行）→ 对账重投已确认队列；落库后崩（有 DB 行）→ 对账按 orderNo 摘除。两段都可恢复，无丢单路径
- 超时释放走 pending 桶扫描（`trade.hot-order.release-scan-ms`，默认 10s），处理中转区对账默认 30s

### ChatController `/api/chat`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/session` | 创建聊天会话（针对某商品） |
| GET | `/api/chat/sessions` | 我的聊天会话列表 |
| GET | `/api/chat/messages/{sessionId}` | 获取会话消息（分页） |
| POST | `/api/chat/messages` | 发送消息 |
| PUT | `/api/chat/messages/{id}/recall` | 撤回消息 |

### FavoriteController `/api/favorites`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/favorites/{goodsId}` | 收藏商品 |
| DELETE | `/api/favorites/{goodsId}` | 取消收藏 |
| GET | `/api/favorites` | 我的收藏列表 |
| GET | `/api/favorites/check/{goodsId}` | 检查是否已收藏 |

### FollowController `/api/follow`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/follow/{userId}` | 关注用户 |
| DELETE | `/api/follow/{userId}` | 取消关注 |
| GET | `/api/follow/check/{userId}` | 检查是否已关注 |
| GET | `/api/follow/count/{userId}` | 获取关注/粉丝数 |

### BlockController `/api/blocks`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/blocks/{userId}` | 拉黑用户 |
| DELETE | `/api/blocks/{userId}` | 取消拉黑 |
| GET | `/api/blocks/check/{userId}` | 检查拉黑状态（双向） |
| GET | `/api/blocks` | 拉黑列表（分页） |

### CategoryController `/api/categories`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/categories` | 获取分类树 |

### ReviewController `/api/reviews`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/reviews/user/{userId}` | 获取某用户的评价列表 |
| GET | `/api/reviews/me` | 我的评价 |
| GET | `/api/reviews/me/rating` | 我的评分（平均分、评价数） |
| GET | `/api/reviews/me/received` | 我收到的评价（分页） |
| GET | `/api/reviews/me/given` | 我发出的评价（分页） |
| GET | `/api/reviews/order/{orderId}` | 获取订单的评价 |
| GET | `/api/reviews/user/{userId}/stats` | 用户评价统计（平均分、好评率） |
| POST | `/api/reviews/{id}/appeal` | 申诉评价 |
| POST | `/api/reviews/{id}/reply` | 回复评价 |
| PUT | `/api/reviews/{id}` | 修改评价 |
| DELETE | `/api/reviews/{id}` | 删除评价 |

### ReportController `/api/reports`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/reports` | 提交举报（目标类型、原因、证据） |
| GET | `/api/reports/my` | 我的举报列表（分页） |

### DisputeController `/api/disputes`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/disputes` | 发起订单纠纷 |
| GET | `/api/disputes/my` | 我的纠纷列表（分页） |

### DeliveryController `/api/delivery`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/delivery/pending` | 待接单配送列表 |
| PUT | `/api/delivery/{id}/accept` | 跑腿员接单 |
| PUT | `/api/delivery/{id}/pickup` | 跑腿员取货 |
| PUT | `/api/delivery/{id}/deliver` | 跑腿员送达 |
| GET | `/api/delivery/my` | 我的配送记录 |
| GET | `/api/delivery/{id}/tracks` | 配送轨迹 |
| POST | `/api/delivery/{id}/location` | 上报 GPS 位置（限频：10秒/次） |

### AdminGoodsController `/api/admin/goods`（需管理员权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/goods` | 商品列表（按状态/关键词筛选） |
| PUT | `/api/admin/goods/{id}/approve` | 审核通过商品 |
| PUT | `/api/admin/goods/{id}/reject` | 审核驳回商品（附原因） |
| PUT | `/api/admin/goods/{id}/force-off` | 强制下架商品（附原因） |
| GET | `/api/admin/goods/stats` | 商品统计（总数、在售、已预订、已售） |
| PUT | `/api/admin/goods/reindex` | 全量重建 Elasticsearch 索引 |

### AdminOrderController `/api/admin/orders`（需管理员权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/orders` | 订单列表（按状态/交易类型筛选） |
| GET | `/api/admin/orders/stats` | 订单统计（总数、已完成、已取消） |

### AdminHotGoodsController `/api/admin/hot-goods`（需管理员权限）

热卖商品仅管理员发布，发布即上架（免审核），库存计数写入 Redis（`hot:stock:{goodsId}`）。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/hot-goods` | 发布热卖商品（初始库存/限购/发货点） |
| PUT | `/api/admin/hot-goods/{id}/stock` | 调整库存（delta 正补货/负盘亏，DB+Redis 同步） |
| PUT | `/api/admin/hot-goods/{id}/off` | 下架（拒新单，在途订单继续履约） |
| GET | `/api/admin/hot-goods` | 热卖商品列表（可售库存取 Redis 计数） |
| GET | `/api/admin/hot-goods/orders` | 热卖订单监控（DB 订单，可按 status 筛选） |
| GET | `/api/admin/hot-goods/tickets` | 热卖票据监控（待确认/已确认/处理中三桶全量，前端与 DB 订单合并展示） |
| GET | `/api/admin/hot-goods/{id}/stock-overview` | 库存概览（DB/Redis/已售/在途票据数/队列积压，对账观测） |

### AdminDisputeController `/api/admin/disputes`（需管理员权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/disputes` | 纠纷列表（按状态筛选） |
| PUT | `/api/admin/disputes/{id}/resolve` | 处理纠纷（附处理结果） |

### AdminReportController `/api/admin/reports`（需管理员权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/reports` | 举报列表（按状态筛选） |
| PUT | `/api/admin/reports/{id}/handle` | 处理举报（附处理结果） |

### AdminReviewController `/api/admin/reviews`（需管理员权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/reviews/appeals` | 评价申诉列表（按状态筛选） |
| PUT | `/api/admin/reviews/{id}/appeal` | 处理评价申诉（通过/驳回） |
| PUT | `/api/admin/reviews/{id}/status` | 更新评价可见状态 |
| GET | `/api/admin/reviews/stats` | 评价统计（总数、申诉中、已隐藏） |

## 依赖

- `campus-common`（公共模块）
- Elasticsearch Java Client（商品全文搜索）
