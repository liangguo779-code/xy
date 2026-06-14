import request from '@/utils/request'

export function getPostList(params) {
  return request.get('/api/forum/posts', { params })
}

export function searchPosts(params) {
  return request.get('/api/forum/posts', { params })
}

export function getPostDetail(id) {
  return request.get(`/api/forum/posts/${id}`)
}

export function createPost(data) {
  return request.post('/api/forum/posts', data)
}

export function updatePost(id, data) {
  return request.put(`/api/forum/posts/${id}`, data)
}

export function deletePost(id) {
  return request.delete(`/api/forum/posts/${id}`)
}

export function likePost(id) {
  return request.post(`/api/forum/posts/${id}/like`)
}

export function getComments(postId, params) {
  return request.get(`/api/forum/posts/${postId}/comments`, { params })
}

export function getCommentTree(postId) {
  return request.get(`/api/forum/posts/${postId}/comments/tree`)
}

export function likeComment(id) {
  return request.post(`/api/forum/comments/${id}/like`)
}

export function createComment(postId, data) {
  const { parentId, ...body } = data
  return request.post(`/api/forum/posts/${postId}/comments`, body, {
    params: { parentId: parentId || 0 }
  })
}

export function toggleFavorite(postId) {
  return request.post(`/api/forum/posts/${postId}/favorite`)
}

export function getFavoriteStatus(postId) {
  return request.get(`/api/forum/posts/${postId}/favorite/status`)
}

export function getMyFavorites(params) {
  return request.get('/api/forum/favorites', { params })
}

export function getMyPosts(params) {
  return request.get('/api/forum/posts/mine', { params })
}
