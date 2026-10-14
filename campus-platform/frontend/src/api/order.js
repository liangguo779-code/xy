import request from '@/utils/request'

/** 发起交易(自提/配送) */
export function createOrder(data) {
  return request.post('/api/orders', data)
}

/** 热卖下单：Lua 原子扣库存+票据入队（消费才产生订单），返回票据（orderNo 跳票据页） */
export function createHotOrder(data) {
  return request.post('/api/orders/hot', data)
}

/** 热卖票据二次确认（购买承诺）：迁入已确认队列，返回「处理中」 */
export function confirmHotTicket(orderNo, goodsId) {
  return request.put(`/api/orders/hot/${orderNo}/confirm-purchase`, null, { params: { goodsId } })
}

/** 热卖票据待确认期取消并释放库存（确认后不可自助取消） */
export function cancelHotTicket(orderNo, goodsId) {
  return request.put(`/api/orders/hot/${orderNo}/cancel-hot`, null, { params: { goodsId } })
}

/** 热卖票据详情（处理中轮询用；已落库时回填 orderId） */
export function getHotTicket(orderNo) {
  return request.get(`/api/orders/hot/ticket/${orderNo}`)
}

/** 我的热卖票据（「我的订单」合并展示用） */
export function getMyHotTickets() {
  return request.get('/api/orders/hot/tickets/my')
}

/** 卖家确认订单(自提) */
export function confirmOrder(id) {
  return request.put(`/api/orders/${id}/confirm`)
}

/** 卖家核销完成(自提) */
export function completeOrder(id, verifyCode) {
  return request.put(`/api/orders/${id}/complete`, null, { params: { verifyCode } })
}

/** 买家支付配送服务费 */
export function payDeliveryFee(id) {
  return request.put(`/api/orders/${id}/pay-fee`)
}

/** 买家确认收货(配送) */
export function confirmReceive(id) {
  return request.put(`/api/orders/${id}/confirm-receive`)
}

/** 确认配送安排(协商后确认跑腿费付款方) */
// deliveryFeePayer 枚举：buyer-买家承担 seller-卖家承担
export function confirmDelivery(id, deliveryFeePayer) {
  return request.put(`/api/orders/${id}/confirm-delivery`, null, { params: { deliveryFeePayer } })
}

/** 取消订单 */
export function cancelOrder(id) {
  return request.put(`/api/orders/${id}/cancel`)
}

/** 评价交易 */
export function createReview(data) {
  return request.post('/api/orders/review', data)
}

/** 我的订单列表 */
export function getMyOrders(params) {
  return request.get('/api/orders/my', { params })
}

/** 订单详情 */
export function getOrderDetail(id) {
  return request.get(`/api/orders/${id}`)
}
