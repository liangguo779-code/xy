import request from '@/utils/request'

/** 发起交易(自提/配送) */
export function createOrder(data) {
  return request.post('/api/orders', data)
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
