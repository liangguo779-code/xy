import request from '@/utils/request'

export function followUser(userId) {
  return request.post(`/api/follow/${userId}`)
}

export function unfollowUser(userId) {
  return request.delete(`/api/follow/${userId}`)
}

export function checkFollow(userId) {
  return request.get(`/api/follow/check/${userId}`)
}

export function getFollowCount(userId) {
  return request.get(`/api/follow/count/${userId}`)
}
