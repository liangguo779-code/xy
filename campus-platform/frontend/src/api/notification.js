import request from '@/utils/request'

export function getNotifications(params) {
  return request.get('/api/notifications', { params })
}

export function markRead(id) {
  return request.put(`/api/notifications/${id}/read`)
}

export function markAllRead() {
  return request.put('/api/notifications/read-all')
}

export function getUnreadCount() {
  return request.get('/api/notifications/unread-count')
}
