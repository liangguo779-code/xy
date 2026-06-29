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
export function reindexGoods() {
  return request.put('/api/admin/goods/reindex')
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

// 评价管理
export function getReviewAppeals(params) {
  return request.get('/api/admin/reviews/appeals', { params })
}
export function handleReviewAppeal(id, data) {
  return request.put(`/api/admin/reviews/${id}/appeal`, data)
}
export function updateReviewStatus(id, data) {
  return request.put(`/api/admin/reviews/${id}/status`, data)
}
export function getReviewStats() {
  return request.get('/api/admin/reviews/stats')
}

// 知识库管理
export function getKnowledgeList() {
  return request.get('/api/admin/knowledge/list')
}
export function uploadKnowledge(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/api/admin/knowledge/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export function getKnowledgeContent(filename) {
  return request.get(`/api/admin/knowledge/${filename}/content`)
}
export function updateKnowledgeContent(filename, content) {
  return request.put(`/api/admin/knowledge/${filename}`, { content })
}
export function toggleKnowledge(filename) {
  return request.post(`/api/admin/knowledge/toggle/${filename}`)
}
export function deleteKnowledge(filename) {
  return request.delete(`/api/admin/knowledge/${filename}`)
}
export function rebuildKnowledge() {
  return request.post('/api/admin/knowledge/rebuild')
}

export function getRebuildStatus() {
  return request.get('/api/admin/knowledge/rebuild/status')
}
