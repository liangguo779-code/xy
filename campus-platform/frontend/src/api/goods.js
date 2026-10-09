import request from '@/utils/request'

/** 分页获取商品列表（params 含关键词/分类/价格与排序等筛选条件） */
export function getGoodsList(params) {
  return request.get('/api/goods', { params })
}

/** 获取推荐商品（params 可带数量等条件） */
export function getRecommendGoods(params) {
  return request.get('/api/goods/recommend', { params })
}

/** 商品详情 */
export function getGoodsDetail(id) {
  return request.get(`/api/goods/${id}`)
}

/** 发布商品（提交后进入审核） */
export function createGoods(data) {
  return request.post('/api/goods', data)
}

/** 编辑商品 */
export function updateGoods(id, data) {
  return request.put(`/api/goods/${id}`, data)
}

/** 删除商品 */
export function deleteGoods(id) {
  return request.delete(`/api/goods/${id}`)
}

/** 卖家标记商品已售出 */
export function markAsSold(id) {
  return request.put(`/api/goods/${id}/sold`)
}
