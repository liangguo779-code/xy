import request from '@/utils/request'

/** 收藏商品 */
export function addFavorite(goodsId) {
  return request.post(`/api/favorites/${goodsId}`)
}

/** 取消收藏商品 */
export function removeFavorite(goodsId) {
  return request.delete(`/api/favorites/${goodsId}`)
}

/** 我的收藏列表 */
export function getMyFavorites() {
  return request.get('/api/favorites')
}

/** 查询当前用户是否已收藏该商品（详情页心形按钮状态） */
export function checkFavorite(goodsId) {
  return request.get(`/api/favorites/check/${goodsId}`)
}
