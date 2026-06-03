import request from '@/utils/request'

export function getGoodsList(params) {
  return request.get('/api/goods', { params })
}

export function getRecommendGoods(params) {
  return request.get('/api/goods/recommend', { params })
}

export function getGoodsDetail(id) {
  return request.get(`/api/goods/${id}`)
}

export function createGoods(data) {
  return request.post('/api/goods', data)
}

export function updateGoods(id, data) {
  return request.put(`/api/goods/${id}`, data)
}

export function deleteGoods(id) {
  return request.delete(`/api/goods/${id}`)
}

export function markAsSold(id) {
  return request.put(`/api/goods/${id}/sold`)
}
