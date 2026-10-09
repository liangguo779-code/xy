import request from '@/utils/request'

/** 我收到的评价（params 为分页条件） */
export function getMyReceivedReviews(params) {
  return request.get('/api/reviews/me/received', { params })
}

/** 我发出的评价（params 为分页条件） */
export function getMyGivenReviews(params) {
  return request.get('/api/reviews/me/given', { params })
}

/** 对评价发起申诉（提交后进入管理端申诉列表） */
export function appealReview(id, data) {
  return request.post(`/api/reviews/${id}/appeal`, data)
}

/** 卖家回复评价 */
export function replyReview(id, data) {
  return request.post(`/api/reviews/${id}/reply`, data)
}
