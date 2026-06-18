# 二手交易模块 Bug 深度排查计划书 (v2 增强版)

> 排查日期: 2026-06-14
> 排查范围: campus-trade + campus-admin 后端全模块 + 前端全页面 + 跨模块联动
> 排查深度: 全量源码逐行审查，覆盖 14 个 Entity、10+ Controller、9 个 Service、11 个前端页面

---

## 一、排查概览

共发现 **52 个潜在 Bug** + **8 个安全/性能问题**，按严重程度分为：
- **P0 严重 (6个)**: 数据错乱、资金风险、安全漏洞
- **P1 高 (16个)**: 流程阻断、状态不一致、数据污染
- **P2 中 (18个)**: 功能缺陷、体验异常、逻辑缺陷
- **P3 低 (12个)**: 边界问题、显示异常、代码质量

---

## 二、订单模块 (OrderService) -- 10 个 Bug

### BUG-01 [P0] 自提订单跳过了 PENDING 状态，confirmOrder 永远无法执行

**文件**: `OrderServiceImpl.java:createOrder()`
**问题**: 自提订单创建时直接设 `status=CONFIRMED(1)`，但 `confirmOrder()` 要求 `status == PENDING(0)`。
**影响**: confirmOrder 成为死代码，自提"卖家确认"步骤被跳过。
**修复**: 自提订单初始状态改为 PENDING(0)，或 confirmOrder 兼容 CONFIRMED。

---

### BUG-02 [P0] createOrder 的 buyerId 可被前端篡改

**文件**: `OrderServiceImpl.java:createOrder()` + `CreateOrderReq.java`
**问题**: `CreateOrderReq.buyerId` 由前端传入，攻击者可传入任意用户 ID 为其创建订单。
**影响**: 伪造订单，将无关用户绑定为买家。
**修复**: 删除 `CreateOrderReq.buyerId`，始终使用登录用户 ID。

---

### BUG-03 [P1] 防重复下单存在竞态条件

**文件**: `OrderServiceImpl.java:createOrder()`
**问题**: 先查再写无锁，并发请求可同时通过检查导致重复订单。
**修复**: 数据库唯一条件或分布式锁。

---

### BUG-04 [P1] cancelOrder 在 ASSIGNED 状态下仍可取消

**文件**: `OrderServiceImpl.java:cancelOrder()`
**问题**: 取消条件 `status >= PICKED_UP(8)`，但 ASSIGNED(7) 时骑手已接单在路上。
**修复**: 条件改为 `status >= ASSIGNED(7)`。

---

### BUG-05 [P1] getMyOrders 的 status=1 语义冲突

**文件**: `OrderServiceImpl.java:getMyOrders()` + `OrderList.vue`
**问题**: 前端传 `status=1` 表示"进行中"，后端解释为"排除3和4"，但 status=1 本身是 CONFIRMED 状态码，语义歧义。
**修复**: 使用独立参数 `inProgress=true`。

---

### BUG-06 [P2] toVO 每次 new ObjectMapper

**文件**: `OrderServiceImpl.java:toVO()`
**问题**: 每个订单项都 `new ObjectMapper()`，重量级对象频繁创建。
**修复**: 提取为 static final 常量。

---

### BUG-07 [P2] 订单列表不支持分页

**文件**: `OrderServiceImpl.java:getMyOrders()`
**问题**: 返回 `List<OrderVO>` 全量加载。
**修复**: 改为分页查询。

---

### BUG-08 [P2] generateOrderNo 存在碰撞风险

**文件**: `OrderServiceImpl.java:generateOrderNo()`
**问题**: `ORD + timestamp + 4位随机`，同毫秒内碰撞概率不可忽略。
**修复**: 使用数据库序列或 Redis 自增。

---

### BUG-09 [P3] WebSocket 推送离线用户静默丢失

**文件**: `OrderServiceImpl.java:notifyUser()`
**问题**: 用户离线时消息直接丢失，无持久化兜底。
**修复**: 结合 NotificationService 做持久化。

---

### BUG-33 [P1] 自提订单完成后配送订单标记已售出未取消配送工单

**文件**: `OrderServiceImpl.java:completeSelfPickup()` / `confirmReceive()`
**问题**: 订单完成时标记商品 `status=2`（已售出），但如果存在关联的配送工单（DeliveryOrder），没有同步取消配送工单。骑手可能还在配送一个已售出商品的订单。
**修复**: 完成订单时检查并关闭关联配送工单。

---

## 三、配送模块 (DeliveryService) -- 8 个 Bug

### BUG-10 [P0] acceptOrder 的锁在事务提交前释放

**文件**: `DeliveryServiceImpl.java:acceptOrder()`
**问题**: `lock.unlock()` 在 `@Transactional` 提交前执行，并发骑手可读到旧状态重复接单。
**修复**: 使用 `@TransactionalEventListener` 或数据库乐观锁。

---

### BUG-11 [P1] createDeliveryOrder 无去重校验

**文件**: `DeliveryServiceImpl.java:createDeliveryOrder()`
**问题**: 不检查 orderId 是否已有配送工单，重复调用产生多单。
**修复**: 插入前查询或 `order_id` 加唯一约束。

---

### BUG-12 [P1] 配送费计算与楼层信息脱节

**文件**: `DeliveryServiceImpl.java:createDeliveryOrder()` vs `DeliveryFeeServiceImpl.java`
**问题**: 配送费需要 `floor` 和 `hasElevator`，但创建工单时未保存这些参数。
**修复**: 在 DeliveryOrder 中保存楼层信息。

---

### BUG-13 [P2] 骑手位置上报无频率限制

**文件**: `DeliveryController.java:reportLocation()`
**问题**: 无限制写入 `delivery_track` 表。
**修复**: 添加频率限制或 Redis 缓存。

---

### BUG-14 [P2] deliverGoods 只通知买家不通知卖家

**文件**: `DeliveryServiceImpl.java:deliverGoods()`
**问题**: 骑手送达后只推送给买家，卖家不知情。
**修复**: 补充通知卖家。

---

### BUG-15 [P3] getUserDormitory 异常被静默吞掉

**文件**: `DeliveryServiceImpl.java:getUserDormitory()`
**问题**: `catch (Exception ignored) {}` 吞掉地址查询异常。
**修复**: 区分"未设置"和"查询失败"。

---

### BUG-34 [P1] accept/pickup/deliver 操作和 addTrack 未在同一事务中

**文件**: `DeliveryController.java`
**问题**: Controller 中先调用 `deliveryService.acceptOrder()`（有事务），再调用 `deliveryTrackService.addTrack()`（独立调用）。如果 addTrack 失败，配送状态已更新但轨迹缺失。两者不在同一事务中。
```java
DeliveryOrderVO vo = deliveryService.acceptOrder(runnerId, id);
deliveryTrackService.addTrack(id, runnerId, "accept", lat, lng, null, null); // 可能失败
```
**修复**: 将 addTrack 移入 Service 层，与状态更新在同一事务中。

---

### BUG-35 [P2] DeliveryFeeService 配置为 null 时的默认逻辑有隐患

**文件**: `DeliveryFeeServiceImpl.java:getConfig()`
**问题**: `getOne(null)` 查询全部记录取第一条。如果数据库有多条配置记录，行为不确定。默认配置对象没有 ID，`updateConfig` 时 `getConfig().getId()` 返回 null。
**修复**: 使用固定 ID 或 Redis 配置，确保只有一条记录。

---

## 四、商品模块 (GoodsService) -- 8 个 Bug

### BUG-16 [P1] markAsSold/deleteGoods 不检查进行中订单

**文件**: `GoodsServiceImpl.java`
**问题**: 卖家可手动标记已售出，但不检查是否有进行中的订单。
**修复**: 操作前检查是否有未完成订单。

---

### BUG-17 [P1] 刷新（擦亮）时区问题

**文件**: `GoodsServiceImpl.java:refreshGoods()`
**问题**: `toLocalDate()` 使用系统默认时区。
**修复**: 统一使用 UTC 或明确时区。

---

### BUG-18 [P2] 价格历史 JSON 字符串拼接脆弱

**文件**: `GoodsServiceImpl.java:updateGoods()`
**问题**: `history.substring(0, history.length()-1) + "," + newEntry + "]"` 手动拼接 JSON。
**修复**: 使用 Jackson 序列化。

---

### BUG-19 [P2] getBrowseHistory 分页 total 不准确

**文件**: `GoodsServiceImpl.java:getBrowseHistory()`
**问题**: 分页基于浏览记录数，返回去重商品数，total 偏大。
**修复**: SQL 层去重。

---

### BUG-20 [P2] viewCount 更新后返回旧值

**文件**: `GoodsServiceImpl.java:getGoodsDetail()`
**问题**: 先查再更新再返回，前端看到的浏览数少 1。
**修复**: 更新后手动 +1 或重新查询。

---

### BUG-21 [P3] recommendGoods 无收藏时退化为全局热门

**文件**: `GoodsServiceImpl.java:recommendGoods()`
**问题**: 不考虑浏览历史，新用户体验差。
**修复**: 参考浏览历史分类。

---

### BUG-36 [P1] createGoods 不校验 categoryId 是否存在且启用

**文件**: `GoodsServiceImpl.java:createGoods()`
**问题**: 前端传入 `categoryId`，后端不校验该分类是否存在且 `status=1`。可以创建分类为已禁用或不存在的商品。
**修复**: 查询 Category 表校验。

---

### BUG-37 [P2] updateGoods 不校验商品状态

**文件**: `GoodsServiceImpl.java:updateGoods()`
**问题**: 已下架(status=1)、已售出(status=2)的商品仍可编辑。已售出商品被编辑后数据不一致。
**修复**: 只允许 status=0 的商品编辑。

---

## 五、聊天模块 (ChatService) -- 6 个 Bug

### BUG-22 [P1] 聊天记录正序分页导致看不到最新消息

**文件**: `ChatServiceImpl.java:getMessages()`
**问题**: `orderByAsc` + `page=1` 返回最早消息。
**修复**: 改为 `orderByDesc` 或游标分页。

---

### BUG-23 [P1] wantCount 只增不减

**文件**: `ChatServiceImpl.java:startSession()`
**问题**: 每次会话 `want_count + 1`，无减法操作。
**修复**: 关闭会话或取消订单时减少。

---

### BUG-24 [P2] 防诈骗敏感词可被轻易绕过

**文件**: `ChatServiceImpl.java:checkFraudKeywords()`
**问题**: 简单 `contains` 匹配，加空格/同音字/拼音即可绕过。
**修复**: 模糊匹配或接入审核服务。

---

### BUG-25 [P2] WebSocket 多设备登录覆盖旧连接

**文件**: `ChatWebSocketHandler.java`
**问题**: 同 userId 后登录覆盖前一连接，旧设备无感知断开。
**修复**: 新连接时关闭旧连接。

---

### BUG-26 [P3] toSessionVO 的 images JSON 手动解析脆弱

**文件**: `ChatServiceImpl.java:toSessionVO()`
**问题**: `indexOf("\"")` 手动提取图片 URL。
**修复**: 使用 Jackson。

---

### BUG-38 [P2] getMessages 的分页默认 size=50 但 ChatController 默认 size=50，前端未传 size

**文件**: `ChatController.java` + 前端聊天页面
**问题**: 前端如果未传 `size` 参数，默认 50 条。但聊天记录可能有上千条，用户无法加载历史消息（没有"加载更多"的翻页机制，只有 page 参数）。
**修复**: 前端实现滚动加载更多，或支持 `beforeTime` 游标。

---

## 六、评价/纠纷/举报/收藏/关注模块 -- 10 个 Bug

### BUG-27 [P1] 评价状态判断基于全局而非当前用户

**文件**: `OrderDetail.vue:loadOrder()`
**问题**: `/api/reviews/order/{id}` 返回所有评价，未按当前用户过滤。
**修复**: 后端按 reviewerId 过滤。

---

### BUG-28 [P1] 纠纷 reason 和 evidence 拼接为一个字段

**文件**: `OrderDetail.vue:handleSubmitDispute()`
**问题**: 结构化原因和自由文本拼接存入 `reason`。
**修复**: 分开存储。

---

### BUG-29 [P2] 收藏列表排序不稳定

**文件**: `FavoriteServiceImpl.java:getMyFavorites()`
**问题**: `selectBatchIds` 不保证顺序。
**修复**: 应用层排序或 SQL ORDER BY FIELD。

---

### BUG-30 [P2] 前端 canCancel 与后端取消条件不一致

**文件**: `OrderDetail.vue` vs `OrderServiceImpl.java`
**问题**: 前端允许 status=7 取消，后端拒绝。
**修复**: 前后端条件统一。

---

### BUG-31 [P3] evidenceImages 可能双重 JSON 编码

**文件**: `OrderDetail.vue` vs `DisputeController.java`
**问题**: 前端 `JSON.stringify(array)` 后传给后端 string 字段，数据库存的是 JSON 字符串。
**修复**: 统一类型。

---

### BUG-32 [P3] "联系对方" 传参 sellerId 和后端 buyerId 不匹配

**文件**: `OrderDetail.vue:handleContact()`
**问题**: 卖家联系买家时传 `sellerId=买家ID`，后端用 `goodsId` 创建会话，参数名混乱。
**修复**: 使用正确参数名。

---

### BUG-39 [P1] 举报 createReport 不校验 targetType 和 targetId 的有效性

**文件**: `ReportServiceImpl.java:createReport()`
**问题**: 不校验 `targetType` 是否合法（必须是 goods/user/message/post/comment），不校验 `targetId` 对应的记录是否存在。可以对不存在的商品发起举报。
**修复**: 校验 targetType 枚举值和 targetId 存在性。

---

### BUG-40 [P1] 纠纷和举报可重复提交

**文件**: `DisputeServiceImpl.java:createDispute()` / `ReportServiceImpl.java:createReport()`
**问题**: 同一用户对同一订单/目标可多次提交纠纷/举报，没有去重。
**修复**: 检查是否已有 pending 状态的记录。

---

### BUG-41 [P2] ReviewController 和 OrderController 都有 createReview 接口

**文件**: `ReviewController.java:POST /api/reviews` + `OrderController.java:POST /api/orders/review`
**问题**: 两个接口都能创建评价，但调用的 Service 实现不同（ReviewServiceImpl vs OrderServiceImpl.createReview）。前端 `order.js` 调用 `/api/orders/review`，但 `ReviewController` 也存在。如果混用可能导致重复评价或校验不一致。
**修复**: 统一为一个接口，删除冗余。

---

### BUG-42 [P2] FollowServiceImpl 没有事务注解

**文件**: `FollowServiceImpl.java:follow()`
**问题**: `follow()` 方法先查再写（先 count 再 save），没有 `@Transactional`。并发下可能产生重复关注记录（虽然有唯一约束会报错，但用户体验差）。
**修复**: 加 `@Transactional` 或依赖数据库唯一约束返回友好提示。

---

## 七、管理后台模块 (Admin) -- 6 个 Bug

### BUG-43 [P1] AdminGoodsController.approve 可将已下架商品重新上架

**文件**: `AdminGoodsController.java:approve()`
**问题**: 审核通过条件 `status != 1 && status != 3`，即已下架(status=1)的商品也可被审核通过变为上架(status=0)。这允许卖家主动下架的商品被管理员强制上架。
```java
if (goods.getStatus() != 1 && goods.getStatus() != 3) {
    throw new BusinessException("只有已下架或待审核的商品才能审核通过");
}
```
**修复**: 审核通过应只允许 status=3（待审核）的商品。

---

### BUG-44 [P1] AdminDisputeController.resolve 不校验 status 参数合法性

**文件**: `AdminDisputeController.java:resolve()` + `DisputeServiceImpl.java:resolveDispute()`
**问题**: `ResolveReq.status` 是 int 类型，前端可传入任意值（如 5、99）。后端只检查"已处理完毕"，不校验 status 是否为合法的 2（已解决）或 3（已驳回）。
**修复**: 校验 status 只能为 2 或 3。

---

### BUG-45 [P1] AdminReportController.handle 不校验 status 参数合法性

**文件**: `AdminReportController.java:handle()`
**问题**: 同上，`HandleReq.status` 无校验。
**修复**: 校验 status 只能为 1（已处理）或 2（已驳回）。

---

### BUG-46 [P2] AdminBanController 可封禁管理员

**文件**: `AdminBanController.java:banUser()`
**问题**: 没有校验目标用户是否为管理员。一个管理员可以封禁另一个管理员，甚至可以封禁自己（虽然封禁后无法再操作）。
**修复**: 校验不能封禁管理员角色，或至少不能封禁自己。

---

### BUG-47 [P2] bootstrap-admin 端点无速率限制和一次性保护

**文件**: `AuthController.java:bootstrapAdmin()`
**问题**: 注释说"仅当系统无管理员时可用"，但如果 UserService.bootstrapAdmin 实现有漏洞（如只检查用户名是否存在而非系统是否有管理员），可被滥用创建多个管理员。
**修复**: 确保实现严格检查系统无管理员，且添加速率限制。

---

### BUG-48 [P2] AdminForumController.deletePost 是真删除而非软删除

**文件**: `AdminForumController.java:deletePost()`
**问题**: 使用 `postMapper.deleteById(id)` 物理删除，但 `hidePost` 是软删除（status=0）。两者行为不一致，且物理删除无法恢复。
**修复**: 统一使用软删除。

---

## 八、前端页面模块 -- 8 个 Bug

### BUG-49 [P1] GoodsDetail.vue "私聊沟通" 和 "我想要" 按钮绑定同一个 handleWant

**文件**: `GoodsDetail.vue`
**问题**: 两个按钮都调用 `handleWant()`，功能完全相同。"私聊沟通" 应该是直接开始聊天，"我想要" 应该是表达购买意向（可能需要不同的交互）。
```html
<el-button @click="handleWant">私聊沟通</el-button>
<el-button type="danger" @click="handleWant">我想要</el-button>
```
**修复**: 区分两个按钮的功能。

---

### BUG-50 [P1] GoodsList.vue 发布商品后不刷新列表

**文件**: `GoodsList.vue`
**问题**: 用户发布商品后（createGoods API 调用成功），列表不会自动刷新。用户需要手动刷新页面才能看到新商品。
**修复**: 发布成功后调用 `loadGoods()` 刷新列表。

---

### BUG-51 [P2] SellerProfile.vue 不处理用户不存在的情况

**文件**: `SellerProfile.vue`
**问题**: 如果 `userId` 对应的用户不存在或已被删除，页面会显示空白或报错，没有友好提示。
**修复**: 加载时检查用户是否存在，不存在则显示 404。

---

### BUG-52 [P2] DispatchHall.vue 骑手接单后列表不自动刷新

**文件**: `DispatchHall.vue`
**问题**: 骑手接单/取货/送达后，待接单列表和我的订单列表不自动刷新。
**修复**: 操作成功后重新加载两个列表。

---

### BUG-53 [P2] OrderList.vue statusFilter 传空字符串给后端

**文件**: `OrderList.vue`
**问题**: `statusFilter` 初始值为 `''`（空字符串），传给后端时 `params.status = ''`。后端 `@RequestParam(required = false) Integer status` 接收空字符串会报类型转换异常。
**修复**: 空字符串时不传 status 参数。

---

### BUG-54 [P2] GoodsDetail.vue 编辑弹窗不更新 images 和 category

**文件**: `GoodsDetail.vue`
**问题**: 编辑弹窗 `editForm` 只有 title、description、price、condition 四个字段，没有 images 和 category。保存时 `request.put('/api/goods/${id}', editForm.value)` 不会更新图片和分类。
**修复**: 补充编辑弹窗的完整字段。

---

### BUG-55 [P2] Favorites.vue / BrowseHistory.vue 收藏和浏览历史中的已下架商品仍显示

**文件**: `FavoriteServiceImpl.java:getMyFavorites()` / `GoodsServiceImpl.java:getBrowseHistory()`
**问题**: 收藏和浏览历史返回商品时不过滤 status。已下架/已售出的商品仍然显示在列表中。
**修复**: 查询时过滤 `status=0`，或前端标记已下架状态。

---

### BUG-56 [P3] MyGoods.vue 我的商品列表 size 默认 50 但无分页控件

**文件**: `MyGoods.vue` + `GoodsController.java:myGoods()`
**问题**: 后端默认 `size=50`，但如果用户商品超过 50 个，前端没有分页加载机制。
**修复**: 前端添加分页或滚动加载。

---

## 九、安全与性能问题汇总

| 编号 | 类型 | 问题 | 严重度 |
|------|------|------|--------|
| SEC-01 | 安全 | buyerId 可被前端篡改 (BUG-02) | P0 |
| SEC-02 | 安全 | WebSocket 允许所有来源 `setAllowedOrigins("*")` | P2 |
| SEC-03 | 安全 | 核销码 6 位无暴力破解防护，无尝试次数限制 | P2 |
| SEC-04 | 安全 | 敏感词检测可被绕过 (BUG-24) | P2 |
| SEC-05 | 安全 | bootstrap-admin 端点暴露，需确认实现是否安全 (BUG-47) | P2 |
| SEC-06 | 安全 | AdminBanController 可封禁其他管理员 (BUG-46) | P2 |
| SEC-07 | 安全 | 管理员接口 `/api/admin/goods/{id}/force-off` 无操作日志审计 | P3 |
| SEC-08 | 安全 | 举报/纠纷的 targetType 无枚举校验，可注入任意字符串 (BUG-39) | P2 |
| PERF-01 | 性能 | ObjectMapper 频繁创建 (BUG-06) | P2 |
| PERF-02 | 性能 | 订单列表不分页 (BUG-07) | P2 |
| PERF-03 | 性能 | 位置上报无频率限制 (BUG-13) | P2 |
| PERF-04 | 性能 | 收藏列表 selectBatchIds 无排序 (BUG-29) | P2 |

---

## 十、排查执行计划

### 阶段一：P0 修复（1-2天）-- 6 个
- [ ] BUG-01: 自提订单状态流转修复
- [ ] BUG-02: 移除 buyerId 外部传入
- [ ] BUG-10: 分布式锁与事务交互修复
- [ ] SEC-01: buyerId 安全加固

### 阶段二：P1 修复（3-4天）-- 16 个
- [ ] BUG-03: 防重复下单加锁
- [ ] BUG-04: 取消条件优化
- [ ] BUG-05: 状态筛选语义统一
- [ ] BUG-11: 配送工单去重
- [ ] BUG-16: 商品状态与订单联动
- [ ] BUG-22: 聊天记录排序修正
- [ ] BUG-27: 评价状态按用户判断
- [ ] BUG-30: 前后端取消条件一致
- [ ] BUG-33: 订单完成时关闭配送工单
- [ ] BUG-34: accept/pickup/deliver 与 addTrack 同事务
- [ ] BUG-36: createGoods 校验 categoryId
- [ ] BUG-39: 举报 targetType/targetId 校验
- [ ] BUG-40: 纠纷/举报去重
- [ ] BUG-43: 审核通过只允许待审核商品
- [ ] BUG-44/45: 管理员操作 status 参数校验
- [ ] BUG-49/50: 前端按钮功能区分和列表刷新

### 阶段三：P2 修复（4-5天）-- 18 个
- [ ] BUG-06~08: 订单号和性能优化
- [ ] BUG-12~14: 配送费和通知完善
- [ ] BUG-17~20: 商品模块优化
- [ ] BUG-23~25: 聊天模块优化
- [ ] BUG-28~29: 纠纷数据规范化
- [ ] BUG-35/37/38: 配置和状态校验
- [ ] BUG-41/42: 接口去重和事务
- [ ] BUG-46~48: 管理后台安全
- [ ] BUG-51~55: 前端体验优化

### 阶段四：P3 + 安全加固（持续）-- 12 个
- [ ] BUG-09/15/21/26/31/32/56: 边界和代码质量
- [ ] SEC-02~08: 安全加固
- [ ] PERF-01~04: 性能优化
