import request from '@/utils/request'

export function addFavorite(goodsId) {
  return request.post(`/api/favorites/${goodsId}`)
}

export function removeFavorite(goodsId) {
  return request.delete(`/api/favorites/${goodsId}`)
}

export function getMyFavorites() {
  return request.get('/api/favorites')
}

export function checkFavorite(goodsId) {
  return request.get(`/api/favorites/check/${goodsId}`)
}
