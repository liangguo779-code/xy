import request from '@/utils/request'

/** 分页获取帖子列表（params 含 page/size 与板块、排序条件） */
export function getPostList(params) {
  return request.get('/api/forum/posts', { params })
}

/** 搜索帖子（与列表同一接口，params 额外带关键词） */
export function searchPosts(params) {
  return request.get('/api/forum/posts', { params })
}

/** 热门帖子榜单（后端 Redis 热度榜 + 60 秒列表缓存，返回 top N） */
export function getHotPosts(size) {
  return request.get('/api/forum/posts/hot', { params: { size } })
}

/** 帖子详情 */
export function getPostDetail(id) {
  return request.get(`/api/forum/posts/${id}`)
}

/** 发帖 */
export function createPost(data) {
  return request.post('/api/forum/posts', data)
}

/** 编辑帖子（仅作者可操作） */
export function updatePost(id, data) {
  return request.put(`/api/forum/posts/${id}`, data)
}

/** 删除帖子（仅作者可操作） */
export function deletePost(id) {
  return request.delete(`/api/forum/posts/${id}`)
}

/** 帖子点赞/取消点赞（同一接口切换） */
export function likePost(id) {
  return request.post(`/api/forum/posts/${id}/like`)
}

/** 分页获取帖子评论 */
export function getComments(postId, params) {
  return request.get(`/api/forum/posts/${postId}/comments`, { params })
}

/** 获取评论树（一次性返回嵌套回复） */
export function getCommentTree(postId) {
  return request.get(`/api/forum/posts/${postId}/comments/tree`)
}

/** 评论点赞/取消点赞 */
export function likeComment(id) {
  return request.post(`/api/forum/comments/${id}/like`)
}

/** 发表评论：parentId 走 query 传后端，0 或空表示一级评论 */
export function createComment(postId, data) {
  const { parentId, ...body } = data
  return request.post(`/api/forum/posts/${postId}/comments`, body, {
    params: { parentId: parentId || 0 }
  })
}

/** 收藏/取消收藏帖子（同一接口切换） */
export function toggleFavorite(postId) {
  return request.post(`/api/forum/posts/${postId}/favorite`)
}

/** 查询当前用户对帖子的收藏状态 */
export function getFavoriteStatus(postId) {
  return request.get(`/api/forum/posts/${postId}/favorite/status`)
}

/** 我收藏的帖子（分页） */
export function getMyFavorites(params) {
  return request.get('/api/forum/favorites', { params })
}

/** 我发布的帖子（分页） */
export function getMyPosts(params) {
  return request.get('/api/forum/posts/mine', { params })
}
