import request from '@/utils/request'

export function chat(data) {
  return request.post('/api/ai/chat', data, { timeout: 120000 })
}

export async function chatStream(data, onToken, onSources, onDone) {
  const token = localStorage.getItem('token')
  const res = await fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      if (!line.startsWith('data:')) continue
      try {
        const jsonStr = line.startsWith('data: ') ? line.slice(6) : line.slice(5)
        const event = JSON.parse(jsonStr)
        if (event.type === 'token' && onToken) {
          onToken(event.content)
        } else if (event.type === 'sources' && onSources) {
          onSources(event.sources)
        } else if (event.type === 'done' && onDone) {
          onDone()
        }
      } catch (e) {
        // ignore parse errors
      }
    }
  }
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
