import request from '@/utils/request'

// 用户管理
export function getUsers(params) {
  return request.get('/api/admin/users', { params })
}
export function updateUserStatus(id, status) {
  return request.put(`/api/admin/users/${id}/status`, null, { params: { status } })
}
export function updateUserRole(id, role) {
  return request.put(`/api/admin/users/${id}/role`, null, { params: { role } })
}
export function getUserStats() {
  return request.get('/api/admin/users/stats')
}

// 商品管理
export function getAdminGoods(params) {
  return request.get('/api/admin/goods', { params })
}
export function approveGoods(id) {
  return request.put(`/api/admin/goods/${id}/approve`)
}
export function rejectGoods(id) {
  return request.put(`/api/admin/goods/${id}/reject`)
}
export function forceOffGoods(id) {
  return request.put(`/api/admin/goods/${id}/force-off`)
}
export function getGoodsStats() {
  return request.get('/api/admin/goods/stats')
}

// 订单管理
export function getAdminOrders(params) {
  return request.get('/api/admin/orders', { params })
}
export function getOrderStats() {
  return request.get('/api/admin/orders/stats')
}

// 纠纷管理
export function getAdminDisputes(params) {
  return request.get('/api/admin/disputes', { params })
}
export function resolveDispute(id, data) {
  return request.put(`/api/admin/disputes/${id}/resolve`, data)
}

// 举报管理
export function getAdminReports(params) {
  return request.get('/api/admin/reports', { params })
}
export function handleReport(id, data) {
  return request.put(`/api/admin/reports/${id}/handle`, data)
}
