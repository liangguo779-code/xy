import request from '@/utils/request'

/** 非流式 AI 问答；RAG 检索耗时长，超时放宽到 120 秒 */
export function chat(data) {
  return request.post('/api/ai/chat', data, { timeout: 120000 })
}

/**
 * SSE 流式 AI 问答：fetch 直连后端并逐行解析 data: 事件（axios 无法读取流）。
 * 回调：onSession 会话 ID、onStage 执行阶段、onToken 增量文本、onSources 引用来源、
 *      onDone 完成、onCorrection 修正文本、onUsage 用量统计、onToolCall 工具调用事件；
 *      断网按指数退避最多重试 3 次
 * 注意：onUsage/onToolCall 必须出现在参数列表中——事件分支按名字引用这两个回调，
 * 若未声明，后端下发 usage/tool_call 事件时会抛 ReferenceError 并被解析 catch 吞掉导致事件静默失效。
 * 两者为可选扩展位（后端 campus-ai 当前仅下发 session/stage/token/sources/correction/done）。
 */
export async function chatStream(data, onToken, onSources, onDone, onStage, onSession, onCorrection, onUsage, onToolCall) {
  // fetch 不走 axios 拦截器，需手动附带 token
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
      // 按 \n 切行后可能残留半个事件，暂存到 buffer 等下一批数据拼完
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

/** 我的 AI 会话列表（侧边栏历史） */
export function getSessions() {
  return request.get('/api/ai/sessions')
}

/** 某会话的历史消息 */
export function getSessionMessages(sessionId) {
  return request.get(`/api/ai/sessions/${sessionId}/messages`)
}

/** 新建会话 */
export function createSession(title) {
  return request.post('/api/ai/sessions', { title })
}

/** 删除会话 */
export function deleteSession(sessionId) {
  return request.delete(`/api/ai/sessions/${sessionId}`)
}

/** 重命名会话 */
export function updateSessionTitle(sessionId, title) {
  return request.put(`/api/ai/sessions/${sessionId}/title`, { title })
}
