import request from '@/utils/request'

// 用户管理
/** 分页检索用户（params 为关键词/状态等筛选条件） */
export function getUsers(params) {
  return request.get('/api/admin/users', { params })
}
/** 启用/禁用账号（status：0-禁用 1-启用） */
export function updateUserStatus(id, status) {
  return request.put(`/api/admin/users/${id}/status`, null, { params: { status } })
}
/** 调整用户角色 */
export function updateUserRole(id, role) {
  return request.put(`/api/admin/users/${id}/role`, null, { params: { role } })
}
/** 用户数据统计 */
export function getUserStats() {
  return request.get('/api/admin/users/stats')
}

// 商品管理
/** 分页检索商品（含待审核，用于审核台） */
export function getAdminGoods(params) {
  return request.get('/api/admin/goods', { params })
}
/** 审核通过商品 */
export function approveGoods(id) {
  return request.put(`/api/admin/goods/${id}/approve`)
}
/** 审核驳回商品 */
export function rejectGoods(id) {
  return request.put(`/api/admin/goods/${id}/reject`)
}
/** 强制下架在售商品 */
export function forceOffGoods(id) {
  return request.put(`/api/admin/goods/${id}/force-off`)
}
/** 商品数据统计 */
export function getGoodsStats() {
  return request.get('/api/admin/goods/stats')
}
/** 重建商品搜索索引 */
export function reindexGoods() {
  return request.put('/api/admin/goods/reindex')
}

// 订单管理
/** 分页检索订单 */
export function getAdminOrders(params) {
  return request.get('/api/admin/orders', { params })
}
/** 订单数据统计 */
export function getOrderStats() {
  return request.get('/api/admin/orders/stats')
}

// 热卖商品管理（仅管理员可发布/调库存/下架，发布即上架免审核）
/** 发布热卖商品（初始库存/限购/发货点，Redis 同步初始化库存计数） */
export function publishHotGoods(data) {
  return request.post('/api/admin/hot-goods', data)
}
/** 调整库存（delta 正数补货/负数盘亏，DB+Redis 同步） */
export function adjustHotStock(id, delta) {
  return request.put(`/api/admin/hot-goods/${id}/stock`, null, { params: { delta } })
}
/** 下架热卖商品（拒新单，在途订单继续履约） */
export function offHotGoods(id, reason) {
  return request.put(`/api/admin/hot-goods/${id}/off`, null, { params: { reason } })
}
/** 热卖商品列表（可售库存取 Redis 计数） */
export function getHotGoods(params) {
  return request.get('/api/admin/hot-goods', { params })
}
/** 热卖订单监控（可按 status=10 看待确认） */
export function getHotOrders(params) {
  return request.get('/api/admin/hot-goods/orders', { params })
}

/** 热卖票据监控：待确认/已确认/处理中三桶全量（订单尚未落库的在途单） */
export function getHotTickets() {
  return request.get('/api/admin/hot-goods/tickets')
}
/** 库存概览（DB/Redis/已售/待确认数，对账观测） */
export function getHotStockOverview(id) {
  return request.get(`/api/admin/hot-goods/${id}/stock-overview`)
}

// 纠纷管理
/** 分页检索纠纷（params 可按处理状态过滤） */
export function getAdminDisputes(params) {
  return request.get('/api/admin/disputes', { params })
}
/** 裁决纠纷，data 含处理结论与更新后的纠纷状态 */
export function resolveDispute(id, data) {
  return request.put(`/api/admin/disputes/${id}/resolve`, data)
}

// 举报管理
/** 分页检索举报 */
export function getAdminReports(params) {
  return request.get('/api/admin/reports', { params })
}
/** 处理举报，data 为处理结论 */
export function handleReport(id, data) {
  return request.put(`/api/admin/reports/${id}/handle`, data)
}
/** 查询被举报目标的原文与作者（举报裁决依据） */
export function getReportTarget(id) {
  return request.get(`/api/admin/reports/${id}/target`)
}

// 评价管理
/** 分页查询评价申诉（params 可按申诉状态过滤） */
export function getReviewAppeals(params) {
  return request.get('/api/admin/reviews/appeals', { params })
}
/** 处理申诉，data 含是否支持申诉与处理结论，通过则屏蔽评价 */
export function handleReviewAppeal(id, data) {
  return request.put(`/api/admin/reviews/${id}/appeal`, data)
}
/** 直接屏蔽/恢复评价（status：1-正常 0-已屏蔽） */
export function updateReviewStatus(id, data) {
  return request.put(`/api/admin/reviews/${id}/status`, data)
}
/** 评价数据统计（总数/申诉中/已隐藏） */
export function getReviewStats() {
  return request.get('/api/admin/reviews/stats')
}

// 知识库管理
/** 知识库文档列表 */
export function getKnowledgeList() {
  return request.get('/api/admin/knowledge/list')
}
/** 上传知识库文档（multipart 文件流） */
export function uploadKnowledge(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/api/admin/knowledge/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
/** 读取文档正文（在线编辑用） */
export function getKnowledgeContent(filename) {
  return request.get(`/api/admin/knowledge/${filename}/content`)
}
/** 保存文档正文 */
export function updateKnowledgeContent(filename, content) {
  return request.put(`/api/admin/knowledge/${filename}`, { content })
}
/** 启用/停用文档（切换状态） */
export function toggleKnowledge(filename) {
  return request.post(`/api/admin/knowledge/toggle/${filename}`)
}
/** 删除文档 */
export function deleteKnowledge(filename) {
  return request.delete(`/api/admin/knowledge/${filename}`)
}
/** 触发知识库向量重建（异步，进度用 getRebuildStatus 查询） */
export function rebuildKnowledge() {
  return request.post('/api/admin/knowledge/rebuild')
}

/** 查询知识库重建进度与结果 */
export function getRebuildStatus() {
  return request.get('/api/admin/knowledge/rebuild/status')
}

// 内容安全复核台
/** 人审队列：按评论状态筛选（默认 3-待人审） */
export function getAuditQueue(params) {
  return request.get('/api/admin/audit/comments', { params })
}
/** 审核详情：评论 + AI 结论 + 审计流水 + 举报 */
export function getAuditDetail(id) {
  return request.get(`/api/admin/audit/comment/${id}`)
}
/** 人工修正评论状态（0-4 任意流转） */
export function fixCommentStatus(id, data) {
  return request.put(`/api/admin/audit/comment/${id}/status`, data)
}
/** KPI 统计（识别准确率、人审减负率等） */
export function getAuditStats() {
  return request.get('/api/admin/audit/stats')
}

// 自定义敏感词库
/** 词库列表 */
export function getSensitiveWords(params) {
  return request.get('/api/admin/sensitive-words', { params })
}
/** 新增敏感词 */
export function addSensitiveWord(data) {
  return request.post('/api/admin/sensitive-words', data)
}
/** 更新敏感词 */
export function updateSensitiveWord(id, data) {
  return request.put(`/api/admin/sensitive-words/${id}`, data)
}
/** 删除敏感词 */
export function deleteSensitiveWord(id) {
  return request.delete(`/api/admin/sensitive-words/${id}`)
}
