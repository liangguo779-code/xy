import request from '@/utils/request'

/** 分页获取通知列表（params 含 page/size 与已读状态筛选） */
export function getNotifications(params) {
  return request.get('/api/notifications', { params })
}

/** 标记单条通知已读 */
export function markRead(id) {
  return request.put(`/api/notifications/${id}/read`)
}

/** 全部通知标记已读 */
export function markAllRead() {
  return request.put('/api/notifications/read-all')
}

/** 未读通知数（导航角标轮询用） */
export function getUnreadCount() {
  return request.get('/api/notifications/unread-count')
}
