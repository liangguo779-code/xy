import request from '@/utils/request'

/** 获取我的地址列表 */
export function getAddresses() {
  return request.get('/api/address')
}

/** 获取地址详情 */
export function getAddress(id) {
  return request.get(`/api/address/${id}`)
}

/** 获取默认地址 */
export function getDefaultAddress() {
  return request.get('/api/address/default')
}

/** 新增地址 */
export function createAddress(data) {
  return request.post('/api/address', data)
}

/** 修改地址 */
export function updateAddress(data) {
  return request.put('/api/address', data)
}

/** 删除地址 */
export function deleteAddress(id) {
  return request.delete(`/api/address/${id}`)
}
