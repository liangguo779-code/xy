import request from '@/utils/request'

/** 买家点击"我想要"，创建/获取聊天会话 */
export function startSession(goodsId) {
  return request.post(`/api/chat/session?goodsId=${goodsId}`)
}

/** 获取我的聊天会话列表 */
export function getMySessions() {
  return request.get('/api/chat/sessions')
}

/** 获取历史消息(分页) */
export function getMessages(sessionId, page = 1, size = 50) {
  return request.get(`/api/chat/messages/${sessionId}`, { params: { page, size } })
}

/** HTTP 方式发送消息(降级方案) */
export function sendMessage(data) {
  return request.post('/api/chat/messages', data)
}
