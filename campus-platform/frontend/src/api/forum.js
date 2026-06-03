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

export function likePost(id) {
  return request.post(`/api/forum/posts/${id}/like`)
}

export function getComments(postId, params) {
  return request.get(`/api/forum/posts/${postId}/comments`, { params })
}

export function createComment(postId, data) {
  return request.post(`/api/forum/posts/${postId}/comments`, data)
}
