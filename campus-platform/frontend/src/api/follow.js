import request from '@/utils/request'

/** 关注用户 */
export function followUser(userId) {
  return request.post(`/api/follow/${userId}`)
}

/** 取消关注用户 */
export function unfollowUser(userId) {
  return request.delete(`/api/follow/${userId}`)
}

/** 查询当前用户是否已关注对方 */
export function checkFollow(userId) {
  return request.get(`/api/follow/check/${userId}`)
}

/** 获取对方的粉丝/关注数（个人主页展示） */
export function getFollowCount(userId) {
  return request.get(`/api/follow/count/${userId}`)
}
