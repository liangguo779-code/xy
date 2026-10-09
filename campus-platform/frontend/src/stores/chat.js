import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMySessions } from '@/api/chat'

/** 聊天全局状态：未读角标计数与全局 WebSocket 连接 */
export const useChatStore = defineStore('chat', () => {
  // 未读消息总数（导航栏角标）
  const unreadCount = ref(0)
  // 全局唯一的聊天 WebSocket，非响应式，仅供收发消息复用
  let ws = null
  // 断线重连定时器句柄：主动断开时必须取消，否则 3 秒后又被拉起
  let reconnectTimer = null
  // 主动断开标记：为 true 时 onclose 不再调度自动重连（仅异常断线才重连）
  let manualClosed = false

  /** 拉取会话列表并汇总未读数（页面加载/清零后校准用） */
  async function loadUnreadCount() {
    try {
      const res = await getMySessions()
      unreadCount.value = (res.data || []).reduce((sum, s) => sum + (s.unreadCount || 0), 0)
    } catch (e) {
      // ignore
    }
  }

  /** 收到新消息时角标 +1 */
  function incrementUnread() {
    unreadCount.value++
  }

  /** 清零未读角标 */
  function clearUnread() {
    unreadCount.value = 0
    // 重新加载以获取准确数据
    loadUnreadCount()
  }

  /** 建立聊天 WebSocket（token 走 query 鉴权），断开后 3 秒自动重连 */
  function connectWebSocket(token) {
    // 已有连接时复用，避免重复建连
    if (ws && ws.readyState === WebSocket.OPEN) return
    // 重新建连视为非主动断开，恢复重连策略
    manualClosed = false

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
      // 主动断开（卸载/退出）不重连，仅网络异常等被动断线才自动恢复
      if (manualClosed) return
      reconnectTimer = setTimeout(() => connectWebSocket(token), 3000)
    }
  }

  /** 主动断开连接并停止自动重连（布局卸载等场景调用）；不暴露 ws 本体，收发仍走 getWs */
  function disconnectWebSocket() {
    manualClosed = true
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (ws) {
      // 先摘掉 onclose 再关闭，避免关闭动作又触发重连调度
      ws.onclose = null
      ws.close()
      ws = null
    }
  }

  /** 暴露底层连接，聊天页用它发送消息 */
  function getWs() {
    return ws
  }

  return { unreadCount, loadUnreadCount, incrementUnread, clearUnread, connectWebSocket, disconnectWebSocket, getWs }
})
