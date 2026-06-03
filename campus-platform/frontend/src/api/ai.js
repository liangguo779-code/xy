import request from '@/utils/request'

export function chat(data) {
  return request.post('/api/ai/chat', data, { timeout: 120000 })
}

export function getSessions() {
  return request.get('/api/ai/sessions')
}

export function getSessionMessages(sessionId) {
  return request.get(`/api/ai/sessions/${sessionId}/messages`)
}

export function createSession(title) {
  return request.post('/api/ai/sessions', { title })
}

export function deleteSession(sessionId) {
  return request.delete(`/api/ai/sessions/${sessionId}`)
}

export function updateSessionTitle(sessionId, title) {
  return request.put(`/api/ai/sessions/${sessionId}/title`, { title })
}
