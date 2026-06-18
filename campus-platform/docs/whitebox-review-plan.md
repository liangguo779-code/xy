# 二手交易模块 全量白盒审查计划书

> 审查日期: 2026-06-14
> 审查范围: campus-trade + campus-admin + campus-common + 前端全量代码
> 审查方法: 逐文件逐方法静态分析 + 跨模块调用链追踪

---

## 一、审查维度

每个文件/方法从以下 8 个维度审查：

| 维度 | 说明 | 检查要点 |
|------|------|---------|
| D1 状态机 | 枚举值流转是否完整 | 是否有非法状态转换、是否遗漏终态处理 |
| D2 权限控制 | 接口/方法的鉴权是否一致 | 前后端权限是否匹配、是否有越权路径 |
| D3 并发安全 | 多线程/多请求场景 | check-then-act 竞态、分布式锁、事务隔离 |
| D4 空指针 | null 值传播链 | 返回值是否判空、集合是否为空、枚举查找 |
| D5 事务边界 | @Transactional 是否覆盖完整 | 原子性是否保证、通知是否在事务外 |
| D6 数据一致性 | 跨表/跨模块数据同步 | 状态联动是否完整、计数器是否准确 |
| D7 输入校验 | 外部参数是否校验 | 枚举白名单、长度限制、类型检查 |
| D8 异常处理 | 异常是否正确传播 | 是否吞异常、是否回滚、错误信息是否安全 |

---

## 二、审查清单

### 模块 1: 订单模块 (campus-trade)

#### 1.1 OrderServiceImpl.java (14个方法)

| 方法 | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 |
|------|----|----|----|----|----|----|----|-----|
| createOrder | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| confirmOrder | ✓ | ✓ | - | ✓ | ✓ | ✓ | - | - |
| completeSelfPickup | ✓ | ✓ | - | ✓ | ✓ | ✓ | ✓ | - |
| confirmDelivery | ✓ | ✓ | - | ✓ | ✓ | ✓ | ✗ | - |
| confirmReceive | ✓ | ✓ | - | ✓ | ✓ | ✓ | - | - |
| cancelOrder | ✓ | ✓ | - | ✓ | ✓ | ✓ | - | - |
| createReview | ✓ | ✓ | ✓ | ✓ | ✓ | - | ✓ | - |
| getMyOrders | - | - | - | ✓ | - | - | ✓ | - |
| getOrderDetail | - | ✓ | - | ✓ | - | - | - | ✓ |
| payDeliveryFee | - | ✓ | - | ✓ | - | - | - | - |
| notifyUser | - | - | - | ✓ | - | - | - | ✓ |
| toVO | - | - | - | ✓ | - | - | - | ✓ |
| generateOrderNo | - | - | ✓ | - | - | - | - | - |
| getOrderById | - | - | - | ✓ | - | - | - | - |

**待审查方法详情：**

```
createOrder:
  D1: 自提 PENDING(0) → CONFIRMED(1)，配送 PENDING → DELIVERY_NEGOTIATING(5) ✓已修复
  D2: buyerId 校验逻辑（卖家不能指定） ✓已修复
  D3: FOR UPDATE 锁 ✓已修复
  D4: goods null 检查 ✓
  D5: @Transactional ✓
  D6: wantCount 初始化
  D7: dealType 取值范围校验
  D8: 异常信息是否泄露敏感数据

confirmOrder:
  D1: PENDING(0) → CONFIRMED(1) ✓
  D2: sellerId 校验 ✓
  D4: order null ✓
  D5: @Transactional ✓
  D6: 确认后是否更新商品状态

confirmDelivery:
  D7: deliveryFeePayer 未校验 ✗待修复
  D6: 是否通知骑手

confirmReceive:
  D6: 是否通知骑手 ✗待修复

cancelOrder:
  D1: 取消条件 ✓已修复
  D6: 配送工单清理 ✓已修复，wantCount ✓已修复

getOrderDetail:
  D8: OrderStatus.fromCode 对未知状态码抛异常 ✗待修复
```

#### 1.2 OrderService.java (接口)

- 方法签名与实现是否一致 ✓
- 返回类型变更（List→Page）是否同步更新 ✓

#### 1.3 OrderController.java (10个端点)

| 端点 | D2 | D7 | 前后端一致性 |
|------|----|----|-------------|
| POST /api/orders | ✓ | ✓ | ✓ |
| PUT /{id}/confirm | ✓ | - | ✓ |
| PUT /{id}/complete | ✓ | ✓ | ✓ |
| PUT /{id}/pay-fee | ✓ | - | - |
| PUT /{id}/confirm-receive | ✓ | - | ✓ |
| PUT /{id}/confirm-delivery | ✓ | ✗空字符串 | ✓ |
| PUT /{id}/cancel | ✓ | - | ✓ |
| POST /review | ✓ | ✓ | ✓ |
| GET /my | ✓ | ✗role非法值 | ✓ |
| GET /{id} | ✓ | - | ✓ |

---

### 模块 2: 配送模块 (campus-trade)

#### 2.1 DeliveryServiceImpl.java (8个方法)

| 方法 | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 |
|------|----|----|----|----|----|----|----|-----|
| createDeliveryOrder | - | - | ✓ | ✓ | ✓ | ✓ | - | - |
| acceptOrder | ✓ | ✓ | ✓ | ✗ | ✓ | ✓ | - | - |
| pickupGoods | ✓ | ✓ | ✗ | ✗ | ✓ | ✓ | - | - |
| deliverGoods | ✓ | ✓ | ✗ | ✗ | ✓ | ✓ | - | - |
| getPendingOrders | - | ✗ | - | - | - | - | - | - |
| getMyDeliveries | - | - | - | - | - | - | - | - |
| toVO | - | - | - | ✓已修复 | - | - | - | ✓ |
| saveTrack | - | - | - | ✓ | - | - | - | - |

**待审查方法详情：**

```
acceptOrder:
  D4: order null 检查 ✓已修复
  D3: 锁释放 afterCommit ✓已修复，但缺 afterCompletion ✗待修复
  D2: 角色校验 ✓

pickupGoods:
  D4: order null 检查 ✓已修复
  D3: 无分布式锁 ✗待评估（@Transactional + 状态检查可接受）
  D6: 轨迹记录 ✓已修复

deliverGoods:
  D4: order null 检查 ✓已修复
  D3: 同上
  D6: 是否通知卖家 ✓已修复

getPendingOrders:
  D2: 未校验用户角色 ✗待修复
```

#### 2.2 DeliveryService.java (接口)

- 方法签名变更（添加 lat/lng 参数）是否同步 ✓

#### 2.3 DeliveryController.java (8个端点)

| 端点 | D2 | D7 |
|------|----|----|
| GET /pending | ✗无角色校验 | - |
| PUT /{id}/accept | ✓ | - |
| PUT /{id}/pickup | ✓ | - |
| PUT /{id}/deliver | ✓ | - |
| GET /my | - | - |
| GET /fee | - | ✓ |
| GET /config | - | - |
| GET /{id}/tracks | ✓ | - |
| POST /{id}/location | ✓ | ✓限频✓ |

#### 2.4 DeliveryFeeServiceImpl.java

| 方法 | D4 | D6 | D8 |
|------|----|----|-----|
| calculateFee | ✓ | - | - |
| getConfig | ✓ | - | - |
| updateConfig | ✓已修复 | - | - |

#### 2.5 DeliveryOrder.java / DeliveryOrderVO.java

- 新增字段 floor/hasElevator/deliveryFee 同步 ✓

---

### 模块 3: 商品模块 (campus-trade)

#### 3.1 GoodsServiceImpl.java (11个方法)

| 方法 | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 |
|------|----|----|----|----|----|----|----|-----|
| listGoods | - | - | - | - | - | - | ✓ | - |
| recommendGoods | - | - | - | ✓ | - | - | - | - |
| getGoodsDetail | - | - | ✓ | ✓ | - | ✓ | - | - |
| createGoods | - | - | - | ✓ | ✗ | - | ✓ | - |
| updateGoods | - | ✓ | - | ✓ | ✓ | ✓ | - | ✗ |
| deleteGoods | - | ✓ | - | ✓ | - | ✓ | - | - |
| markAsSold | - | ✓ | - | ✓ | - | ✓ | - | - |
| refreshGoods | - | ✓ | - | ✓ | - | - | - | - |
| listMyGoods | - | - | - | - | - | - | - | - |
| getBrowseHistory | - | - | - | - | - | - | - | - |

**待审查方法详情：**

```
createGoods:
  D5: 缺少 @Transactional ✗（单操作风险低）
  D7: categoryId 校验 ✓已修复

updateGoods:
  D5: @Transactional ✓已修复
  D6: 降价通知在事务内 ✗应移到 afterCommit

getGoodsDetail:
  D6: viewCount 手动 +1 ✓已修复

deleteGoods/markAsSold:
  D6: 检查进行中订单 ✓已修复
```

#### 3.2 GoodsController.java (9个端点)

| 端点 | D2 | D7 |
|------|----|----|
| GET / | - | ✓ |
| GET /my | ✓ | - |
| GET /recommend | ✓ | - |
| GET /{id} | - | - |
| POST / | ✓ | ✓ |
| PUT /{id} | ✓ | - |
| DELETE /{id} | ✓ | - |
| PUT /{id}/sold | ✓ | - |
| PUT /{id}/refresh | ✓ | - |
| GET /browse-history | ✓ | - |

---

### 模块 4: 聊天模块 (campus-trade)

#### 4.1 ChatServiceImpl.java (6个方法)

| 方法 | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 |
|------|----|----|----|----|----|----|----|-----|
| startSession | - | ✓ | ✓ | ✓ | ✓ | ✓ | - | - |
| getMySessions | - | ✓ | - | ✓ | - | - | - | - |
| getMessages | - | ✓ | - | ✓ | - | - | - | - |
| sendMessage | - | ✓ | - | ✓ | ✓ | - | ✗ | - |
| markAsRead | - | ✗ | - | ✓ | ✓ | - | - | - |
| checkFraudKeywords | - | - | - | ✓ | - | - | - | - |

**待审查方法详情：**

```
sendMessage:
  D7: 消息内容无长度限制 ✗待修复
  D6: WebSocket 推送在事务内 ✗

markAsRead:
  D2: 未校验 session 存在性和用户归属 ✗待修复
```

#### 4.2 ChatWebSocketHandler.java

| 维度 | 检查结果 |
|------|---------|
| D2 | userId 从 session attributes 获取 ✓ |
| D3 | ConcurrentHashMap 线程安全 ✓，多设备覆盖 ✗ |
| D4 | payload 解析 null 检查 ✓ |
| D8 | 异常捕获并推送错误消息 ✓ |

---

### 模块 5: 收藏/关注模块 (campus-trade)

#### 5.1 FavoriteServiceImpl.java

| 方法 | D3 | D4 | D5 | D6 |
|------|----|----|----|-----|
| addFavorite | ✗并发竞态 | ✓ | ✓ | ✓ |
| removeFavorite | ✗并发竞态 | ✓ | ✓ | ✓ |
| isFavorited | - | ✓ | - | - |
| getMyFavorites | - | ✓ | - | ✓ |

#### 5.2 FollowServiceImpl.java

| 方法 | D3 | D4 | D5 |
|------|----|----|-----|
| follow | ✗并发竞态 | ✓ | ✓ |
| unfollow | - | ✓ | - |

---

### 模块 6: 评价模块 (campus-trade)

#### 6.1 ReviewServiceImpl.java

| 方法 | D1 | D2 | D3 | D4 | D7 |
|------|----|----|----|----|-----|
| createReview | ✓ | ✓ | ✓ | ✓ | ✓ |
| getReviewsForUser | - | - | - | ✓ | - |
| getReviewsByOrder | - | - | - | ✓ | - |
| getAverageRating | - | - | - | ✓ | - |

---

### 模块 7: 纠纷/举报模块 (campus-trade)

#### 7.1 DisputeServiceImpl.java

| 方法 | D2 | D3 | D5 | D7 |
|------|----|----|----|-----|
| createDispute | ✓ | ✗并发 | ✗缺事务 | - |
| listMyDisputes | ✓ | - | - | - |
| listDisputes | - | - | - | - |
| resolveDispute | - | - | - | ✓已修复 |

#### 7.2 ReportServiceImpl.java

| 方法 | D2 | D3 | D5 | D7 |
|------|----|----|----|-----|
| createReport | ✓ | ✗并发 | ✗缺事务 | ✓已修复 |
| listReports | - | - | - | - |
| handleReport | ✗无权限校验 | - | - | ✓已修复 |

---

### 模块 8: 管理后台 (campus-admin)

#### 8.1 AdminGoodsController.java

| 方法 | D2 | D3 | D4 | D6 |
|------|----|----|----|-----|
| list | ✓ | - | - | - |
| approve | ✓ | ✗竞态 | ✓ | ✗未通知卖家 |
| reject | ✓ | ✗竞态 | ✓ | - |
| forceOff | ✓ | ✗竞态 | ✓ | - |
| stats | ✓ | - | - | - |

#### 8.2 AdminUserController.java

| 方法 | D2 | D7 |
|------|----|-----|
| list | ✓ | - |
| updateStatus | ✓ | ✓ |
| updateRole | ✓ | ✓ |
| stats | ✓ | - |

#### 8.3 AdminBanController.java

| 方法 | D2 | D4 | D7 |
|------|----|----|-----|
| banUser | ✓已修复 | ✓ | ✗banType未校验 |
| banIp | ✓ | ✓ | ✗banType未校验 |
| unban | ✓ | ✓ | - |
| list | ✓ | - | - |
| checkUserBan | ✓ | - | - |

#### 8.4 AdminDisputeController.java

| 方法 | D2 | D7 |
|------|----|-----|
| list | ✓ | - |
| resolve | ✓ | ✓已修复 |

#### 8.5 AdminReportController.java

| 方法 | D2 | D7 |
|------|----|-----|
| list | ✓ | - |
| handle | ✓ | ✓已修复 |

#### 8.6 AdminOrderController.java

| 方法 | D2 |
|------|----|
| list | ✓ |
| stats | ✓ |

---

### 模块 9: 前端页面

#### 9.1 OrderDetail.vue

| 功能 | D1 | D2 | D4 | D7 |
|------|----|----|----|-----|
| loadOrder | - | ✓ | ✓ | - |
| canCancel | ✓已修复 | - | - | - |
| handleComplete | ✓ | ✓ | ✓ | - |
| handleConfirmReceive | ✓ | ✓ | - | - |
| handleCancel | - | ✓ | - | - |
| handleSubmitReview | - | ✓ | - | ✗rating=0 |
| handleSubmitDispute | - | ✓ | - | - |
| handleContact | - | ✓ | - | - |
| reviewed判断 | ✓已修复 | - | - | - |

#### 9.2 OrderList.vue

| 功能 | D4 | D7 |
|------|----|-----|
| loadOrders | ✓ | ✓已修复 |
| 分页 | ✗硬编码50 | - |

#### 9.3 GoodsDetail.vue

| 功能 | D2 | D4 | D6 |
|------|----|----|-----|
| handleWant/handleChat | - | - | ✗重复 |
| handleFavorite | ✓ | ✓ | ✓ |
| handleFollow | ✓ | ✓ | ✓ |
| handleDelist | - | - | ✗reason未传 |
| handleSaveEdit | ✓ | ✓ | - |

#### 9.4 ChatRoom.vue

| 功能 | D1 | D4 | D6 |
|------|----|----|-----|
| refreshOrderStatus | - | - | ✓已修复 |
| handleConfirmOrder | ✓ | ✓已修复 | - |
| buyerId逻辑 | ✓已修复 | ✓已修复 | - |
| WebSocket连接 | - | - | ✗硬编码端口 |

#### 9.5 MyGoods.vue

| 功能 | 检查 |
|------|------|
| 分页 | ✗硬编码size=50 |

#### 9.6 SellerProfile.vue

| 功能 | 检查 |
|------|------|
| 用户不存在处理 | ✗未处理 |

#### 9.7 DispatchHall.vue

| 功能 | 检查 |
|------|------|
| 角色校验 | ✗未校验 |

---

### 模块 10: DTO/VO/Entity

| 文件 | D4 | D7 |
|------|----|-----|
| CreateOrderReq | ✓ | ✓ |
| OrderVO | ✓ | - |
| CreateReviewReq | ✓ | ✓ |
| DeliveryOrderVO | ✓ | - |
| SendMessageReq | ✓ | ✗无长度校验 |
| Order entity | ✓ | - |
| DeliveryOrder entity | ✓ | - |
| Goods entity | ✓ | - |

---

### 模块 11: 数据库 (init.sql)

| 表 | 约束检查 |
|----|---------|
| order | order_no UNIQUE ✓，无 buyerId+status 索引 ✗ |
| delivery_order | 无 order_id UNIQUE ✗ |
| review | (order_id, reviewer_id) UNIQUE ✓ |
| favorite | (user_id, goods_id) UNIQUE ✓ |
| follow | (follower_id, following_id) UNIQUE ✓ |
| chat_session | (goods_id, buyer_id) UNIQUE ✓ |
| dispute | 无 (order_id, reporter_id, status) UNIQUE ✗ |
| report | 无 (reporter_id, target_type, target_id, status) UNIQUE ✗ |

---

## 三、待修复问题清单（按优先级排序）

### P0 级（必须立即修复）

| 编号 | 模块 | 问题 | 状态 |
|------|------|------|------|
| WB-01 | 订单 | 取消订单未清理配送工单 | ✅已修复 |
| WB-06 | 聊天 | 聊天室卖家看不到订单 | ✅已修复 |

### P1 级（优先修复）

| 编号 | 模块 | 问题 | 状态 |
|------|------|------|------|
| WB-02 | 订单 | FOR UPDATE 结果未使用 | ✅已修复 |
| WB-03 | 订单 | seller 可伪造 buyerId | ✅已修复 |
| WB-04 | 配送 | toVO DeliveryStatus 越界 | ✅已修复 |
| WB-05 | 配送 | updateConfig 空表异常 | ✅已修复 |
| WB-07 | 聊天 | sessionOtherUserId 空检查 | ✅已修复 |
| WB-08 | 配送 | acceptOrder 锁释放 afterCompletion | ⬜待修复 |
| WB-09 | 配送 | DeliveryStatus 需同步更新 -1 取消状态 | ⬜待修复 |

### P2 级（计划修复）

| 编号 | 模块 | 问题 | 状态 |
|------|------|------|------|
| WB-10 | 订单 | confirmDelivery deliveryFeePayer 未校验 | ⬜待修复 |
| WB-11 | 订单 | confirmReceive 未通知骑手 | ⬜待修复 |
| WB-12 | 订单 | OrderStatus.fromCode 未知状态码异常 | ⬜待修复 |
| WB-13 | 配送 | getPendingOrders 无角色校验 | ⬜待修复 |
| WB-14 | 聊天 | sendMessage 消息长度无限制 | ⬜待修复 |
| WB-15 | 聊天 | markAsRead 未校验 session 归属 | ⬜待修复 |
| WB-16 | 商品 | updateGoods 通知在事务内 | ⬜待修复 |
| WB-17 | 纠纷 | createDispute 缺 @Transactional | ⬜待修复 |
| WB-18 | 举报 | createReport 缺 @Transactional | ⬜待修复 |
| WB-19 | 管理 | banType 未做白名单校验 | ⬜待修复 |
| WB-20 | 管理 | AdminGoods approve/reject/forceOff 竞态 | ⬜待修复 |
| WB-21 | 前端 | WebSocket 硬编码端口 | ⬜待修复 |

### P3 级（持续优化）

| 编号 | 模块 | 问题 |
|------|------|------|
| WB-22 | 订单 | generateOrderNo 碰撞风险 |
| WB-23 | 配送 | getPendingOrders/getMyDeliveries 无分页 |
| WB-24 | 商品 | createGoods 缺 @Transactional |
| WB-25 | 商品 | getGoodsDetail 浏览历史 N+1 |
| WB-26 | 聊天 | checkFraudKeywords 仅提醒不阻断 |
| WB-27 | 收藏 | addFavorite/removeFavorite 并发竞态 |
| WB-28 | 关注 | follow 并发竞态 |
| WB-29 | 前端 | OrderList 硬编码 size=50 |
| WB-30 | 前端 | handleDelist reason 未传递 |
| WB-31 | 前端 | handleFavorite/handleFollow 异常吞掉 |
| WB-32 | 前端 | SellerProfile 用户不存在处理 |
| WB-33 | 前端 | DispatchHall 角色校验 |
| WB-34 | 前端 | OrderDetail rating=0 校验 |
| WB-35 | 数据库 | delivery_order.order_id 缺 UNIQUE |
| WB-36 | 数据库 | dispute 缺 (order_id, reporter_id, status) UNIQUE |
| WB-37 | 数据库 | report 缺 (reporter_id, target_type, target_id, status) UNIQUE |

---

## 四、执行计划

### 第一阶段：P1 修复（立即）
- WB-08: acceptOrder 锁释放 afterCompletion
- WB-09: DeliveryStatus 同步 -1 取消状态
- WB-10: confirmDelivery deliveryFeePayer 校验
- WB-11: confirmReceive 通知骑手
- WB-12: OrderStatus.fromCode 防御处理

### 第二阶段：P2 修复（1-2天）
- WB-13~WB-21: 各模块 P2 问题

### 第三阶段：P3 + 数据库优化（持续）
- WB-22~WB-37: 性能优化、前端体验、数据库约束
