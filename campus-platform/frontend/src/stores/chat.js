import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMySessions } from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const unreadCount = ref(0)
  let ws = null

  async function loadUnreadCount() {
    try {
      const res = await getMySessions()
      unreadCount.value = (res.data || []).reduce((sum, s) => sum + (s.unreadCount || 0), 0)
    } catch (e) {
      // ignore
    }
  }

  function incrementUnread() {
    unreadCount.value++
  }

  function clearUnread() {
    unreadCount.value = 0
    // 重新加载以获取准确数据
    loadUnreadCount()
  }

  function connectWebSocket(token) {
    if (ws && ws.readyState === WebSocket.OPEN) return

    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${wsProtocol}//${window.location.host}/ws/chat?token=${token}`
    ws = new WebSocket(wsUrl)

    ws.onmessage = (event) => {
      const data = JSON.parse(event.data)
      if (data.type === 'new_msg') {
        incrementUnread()
      }
    }

    ws.onclose = () => {
      setTimeout(() => connectWebSocket(token), 3000)
    }
  }

  function getWs() {
    return ws
  }

  return { unreadCount, loadUnreadCount, incrementUnread, clearUnread, connectWebSocket, getWs }
})
