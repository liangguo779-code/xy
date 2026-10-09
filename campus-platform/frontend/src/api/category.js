import request from '@/utils/request'

/** 获取商品分类列表（发布页与筛选器共用） */
export function getCategories() {
  return request.get('/api/categories')
}
