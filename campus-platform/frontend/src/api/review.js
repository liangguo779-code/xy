import request from '@/utils/request'

export function getMyReceivedReviews(params) {
  return request.get('/api/reviews/me/received', { params })
}

export function getMyGivenReviews(params) {
  return request.get('/api/reviews/me/given', { params })
}

export function appealReview(id, data) {
  return request.post(`/api/reviews/${id}/appeal`, data)
}

export function replyReview(id, data) {
  return request.post(`/api/reviews/${id}/reply`, data)
}
