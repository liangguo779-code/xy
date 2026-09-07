import request from '@/utils/request'

export function chat(data) {
  return request.post('/api/ai/chat', data, { timeout: 120000 })
}

export async function chatStream(data, onToken, onSources, onDone, onStage, onSession, onCorrection) {
  const token = localStorage.getItem('token')
  const maxRetries = 3
  let lastError = null

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      if (attempt > 0) {
        // Exponential back-off: 1s, 2s, 4s
        await new Promise(r => setTimeout(r, 1000 * Math.pow(2, attempt - 1)))
      }

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
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      try {
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
              if (event.type === 'session' && onSession) {
                onSession(event.sessionId)
              } else if (event.type === 'stage' && onStage) {
                onStage(event)
              } else if (event.type === 'token' && onToken) {
                onToken(event.content)
              } else if (event.type === 'sources' && onSources) {
                onSources(event.sources)
              } else if (event.type === 'correction' && onCorrection) {
                // Backend stripped fabricated [来源N] citations from the answer. The cleaned
                // text replaces the displayed content; callers should ignore the correction
                // event when the response was rejected (backend will follow with a digest).
                onCorrection(event)
              } else if (event.type === 'usage' && onUsage) {
                // Token usage stats from the backend
                onUsage(event)
              } else if (event.type === 'tool_call' && onToolCall) {
                // Tool execution event (e.g. CalendarTool, GpaTool)
                onToolCall(event)
              } else if (event.type === 'done' && onDone) {
                onDone()
              }
            } catch (e) {
              // ignore parse errors
            }
          }
        }
        return  // success — exit the retry loop
      } finally {
        reader.releaseLock()
      }
    } catch (e) {
      lastError = e
      // Only retry on network errors, not HTTP status errors
      if (e.message.startsWith('HTTP ') || attempt >= maxRetries) {
        throw e
      }
    }
  }
  throw lastError
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
