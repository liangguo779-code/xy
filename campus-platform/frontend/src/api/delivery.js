import request from '@/utils/request'

/** 获取待接单工单列表 */
export function getPendingOrders() {
  return request.get('/api/delivery/pending')
}

/** 交付员接单 */
export function acceptOrder(id, lat, lng) {
  return request.put(`/api/delivery/${id}/accept`, null, { params: { lat, lng } })
}

/** 交付员取货 */
export function pickupGoods(id, photoUrl, lat, lng) {
  return request.put(`/api/delivery/${id}/pickup`, null, { params: { photoUrl, lat, lng } })
}

/** 交付员送达 */
export function deliverGoods(id, photoUrl, lat, lng) {
  return request.put(`/api/delivery/${id}/deliver`, null, { params: { photoUrl, lat, lng } })
}

/** 我的工单 */
export function getMyDeliveries() {
  return request.get('/api/delivery/my')
}

/** 获取物流轨迹 */
export function getDeliveryTracks(deliveryId) {
  return request.get(`/api/delivery/${deliveryId}/tracks`)
}

/** 交付员上报位置 */
export function reportLocation(deliveryId, lat, lng, address) {
  return request.post(`/api/delivery/${deliveryId}/location`, null, { params: { lat, lng, address } })
}
