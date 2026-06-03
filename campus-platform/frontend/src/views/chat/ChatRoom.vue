<template>
  <div class="chat-room">
    <el-page-header @back="router.push('/chat')">
      <template #content>
        <span>{{ otherNickname }}</span>
      </template>
    </el-page-header>

    <!-- 商品信息卡片 -->
    <el-card v-if="goodsInfo" class="goods-card" shadow="never">
      <div class="goods-brief">
        <el-image :src="getGoodsImage(goodsInfo)" style="width: 60px; height: 60px; border-radius: 6px"
                  fit="cover">
          <template #error><div class="img-placeholder">图</div></template>
        </el-image>
        <div>
          <div class="goods-title">{{ goodsInfo.title }}</div>
          <div class="goods-price">¥{{ goodsInfo.price }}</div>
        </div>
      </div>
    </el-card>

    <!-- 消息列表 -->
    <div class="messages" ref="messagesRef">
      <div v-for="msg in messages" :key="msg.id"
           :class="['msg-item', msg.senderId === userId ? 'self' : 'other']">
        <!-- 系统消息 -->
        <div v-if="msg.msgType === 2" class="system-msg">
          <el-divider content-position="center">{{ msg.content }}</el-divider>
        </div>
        <!-- 普通消息 -->
        <template v-else>
          <el-avatar :size="36" :src="msg.senderAvatar">
            {{ msg.senderNickname?.charAt(0) }}
          </el-avatar>
          <div class="msg-content-wrapper">
            <div class="bubble" :class="{ 'unread-bubble': isUnread(msg) }">
              <div v-if="msg.msgType === 1" class="msg-image">
                <el-image :src="msg.extra" style="max-width: 200px; border-radius: 8px" fit="contain"
                          :preview-src-list="[msg.extra]" />
              </div>
              <div v-else-if="msg.msgType === 3" class="msg-action">
                <el-tag type="warning">快捷操作</el-tag>
                <p>{{ msg.content }}</p>
              </div>
              <div v-else class="msg-text">{{ msg.content }}</div>
            </div>
            <div class="msg-meta">
              <span class="msg-time">{{ formatTime(msg.createTime) }}</span>
              <span v-if="msg.senderId === userId" class="read-status">
                <span v-if="msg.isRead === 1" class="read">已读</span>
                <span v-else class="unread">未读</span>
              </span>
              <span v-else-if="msg.isRead === 0" class="new-badge">新</span>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- 快捷操作按钮 -->
    <div class="quick-actions">
      <!-- 发起交易（自提/配送） -->
      <el-dropdown trigger="click" @command="handleAction"
                   v-if="!existingOrder && !pendingOrder">
        <el-button size="small" type="primary" plain>
          发起交易 <el-icon><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="self_pickup">约定自提</el-dropdown-item>
            <el-dropdown-item divided command="delivery_buyer">平台配送 - 我付跑腿费</el-dropdown-item>
            <el-dropdown-item command="delivery_seller">平台配送 - 对方付跑腿费</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- 对方发起了交易，非发起方看到确认/拒绝按钮 -->
      <template v-if="pendingOrder && pendingOrder.initiatorId !== userId">
        <el-button size="small" type="warning" plain @click="handleConfirmOrder">
          确认交易
        </el-button>
        <el-button size="small" type="danger" plain @click="handleRejectOrder">
          拒绝
        </el-button>
      </template>

      <!-- 发起方看到等待提示 -->
      <el-tag v-if="pendingOrder && pendingOrder.initiatorId === userId" type="info">
        等待对方确认...
      </el-tag>

      <!-- 已有订单提示 -->
      <el-tag v-if="existingOrder && !pendingOrder" type="success">
        订单已创建
      </el-tag>

      <el-button size="small" type="info" plain @click="handleAction('confirm_price')">
        确认成交价
      </el-button>
    </div>

    <!-- 输入区域 -->
    <div class="input-area">
      <div class="input-toolbar">
        <el-upload action="/api/upload/image" :headers="uploadHeaders"
                   :show-file-list="false" :on-success="handleImageUpload"
                   accept="image/*">
          <el-icon :size="20" class="toolbar-icon"><Picture /></el-icon>
        </el-upload>
      </div>
      <div class="input-row">
        <el-input v-model="inputText" placeholder="输入消息..." @keyup.enter="handleSend"
                  :disabled="sending" size="large">
          <template #append>
            <el-button type="primary" @click="handleSend" :loading="sending">发送</el-button>
          </template>
        </el-input>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { getMessages, getMySessions } from '@/api/chat'
import { createOrder, confirmDelivery, getMyOrders } from '@/api/order'
import { getGoodsDetail } from '@/api/goods'
import { useChatStore } from '@/stores/chat'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const sessionId = Number(route.params.sessionId)
const userId = Number(localStorage.getItem('userId') || 0)
const otherNickname = ref('对方')
const goodsInfo = ref(null)
const messages = ref([])
const inputText = ref('')
const sending = ref(false)
const messagesRef = ref()
const pendingOrder = ref(null)
const existingOrder = ref(null)
const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token')}` }

let ws = null

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) + ' ' +
         d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function isUnread(msg) {
  return msg.senderId !== userId && msg.isRead === 0
}

function getGoodsImage(goods) {
  if (!goods?.images) return ''
  try {
    const imgs = typeof goods.images === 'string' ? JSON.parse(goods.images) : goods.images
    return imgs?.[0] || ''
  } catch { return '' }
}

function connectWebSocket() {
  const token = localStorage.getItem('token')
  ws = new WebSocket(`ws://${window.location.hostname}:8080/ws/chat?token=${token}`)

  ws.onmessage = (event) => {
    const data = JSON.parse(event.data)
    if (data.type === 'new_msg' && data.data?.sessionId === sessionId) {
      const msg = data.data
      messages.value.push(msg)
      scrollToBottom()
      // 检测交易请求
      if (msg.msgType === 3 && msg.extra) {
        try {
          const extra = typeof msg.extra === 'string' ? JSON.parse(msg.extra) : msg.extra
          if ((extra?.action === 'self_pickup' || extra?.action === 'delivery')
              && extra.initiatorId !== userId && !existingOrder.value) {
            pendingOrder.value = extra
          }
        } catch { /* ignore */ }
      }
      // 标记已读
      ws.send(JSON.stringify({ type: 'read', sessionId }))
      // 更新全局未读数
      chatStore.loadUnreadCount()
      // 刷新订单状态
      refreshOrderStatus()
    }
  }

  ws.onopen = () => {
    // 标记已读
    ws.send(JSON.stringify({ type: 'read', sessionId }))
  }
}

async function loadMessages() {
  const res = await getMessages(sessionId, 1, 100)
  messages.value = res.data || []
  scrollToBottom()
  // 检查是否有待确认的交易请求
  checkPendingOrder(messages.value)
  // 刷新订单状态（取消订单后恢复按钮）
  await refreshOrderStatus()
  // 进入聊天室，清除该会话未读数
  chatStore.clearUnread()
}

function handleSend() {
  const text = inputText.value.trim()
  if (!text || !ws || ws.readyState !== WebSocket.OPEN) return

  ws.send(JSON.stringify({
    type: 'send_msg',
    sessionId,
    content: text,
    msgType: 0
  }))

  // 乐观更新
  messages.value.push({
    id: Date.now(),
    senderId: userId,
    content: text,
    msgType: 0,
    createTime: new Date().toISOString()
  })
  inputText.value = ''
  scrollToBottom()
}

function handleImageUpload(res) {
  if (res.code !== 200) return
  const imageUrl = res.data.url

  // 通过 WebSocket 发送图片消息
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({
      type: 'send_msg',
      sessionId,
      content: '[图片]',
      msgType: 1,
      extra: imageUrl
    }))
  }

  // 乐观更新
  messages.value.push({
    id: Date.now(),
    senderId: userId,
    content: '[图片]',
    msgType: 1,
    extra: imageUrl,
    createTime: new Date().toISOString()
  })
  scrollToBottom()
}

async function handleAction(action) {
  try {
    const goodsId = goodsInfo.value?.id
    if (!goodsId && action !== 'confirm_price') {
      ElMessage.warning('无法获取商品信息')
      return
    }

    // 检查是否已有未处理的请求或订单
    if (existingOrder.value) {
      ElMessage.warning('该商品已有进行中的订单')
      return
    }
    if (pendingOrder.value) {
      ElMessage.warning('已有一笔待确认的交易请求，请等待对方处理')
      return
    }

    if (action === 'self_pickup') {
      // 自提：只发消息，不创建订单
      const orderInfo = { action: 'self_pickup', goodsId, initiatorId: userId }
      pendingOrder.value = orderInfo
      sendSystemMsg('请求约定自提，等待对方确认', orderInfo)
      ElMessage.success('已发送自提请求，等待对方确认')
    } else if (action === 'delivery_buyer') {
      // 配送：只发消息，不创建订单
      const orderInfo = { action: 'delivery', goodsId, deliveryFeePayer: 'buyer', initiatorId: userId }
      pendingOrder.value = orderInfo
      sendSystemMsg('请求平台配送，我方承担跑腿费，等待对方确认', orderInfo)
      ElMessage.success('已发送配送请求，等待对方确认')
    } else if (action === 'delivery_seller') {
      // 配送：只发消息，不创建订单
      const orderInfo = { action: 'delivery', goodsId, deliveryFeePayer: 'seller', initiatorId: userId }
      pendingOrder.value = orderInfo
      sendSystemMsg('请求平台配送，请求对方承担跑腿费，等待对方确认', orderInfo)
      ElMessage.success('已发送配送请求，等待对方确认')
    } else if (action === 'confirm_price') {
      inputText.value = '我确认最终成交价为 ¥___，同意交易'
    }
  } catch (e) {
    // handled by interceptor
  }
}

async function handleConfirmOrder() {
  if (!pendingOrder.value) return
  try {
    const info = pendingOrder.value
    if (info.action === 'self_pickup') {
      // 自提确认：创建订单
      // 发起方是执行方（取货/送货），确认方是接收方
      // 买家=接收方，卖家=商品主人
      // 如果发起方是卖家 → 买家是当前确认者
      // 如果发起方是买家 → 买家是发起者
      const isInitiatorSeller = goodsInfo.value?.userId === info.initiatorId
      const buyerId = isInitiatorSeller ? userId : info.initiatorId
      const res = await createOrder({
        goodsId: info.goodsId,
        dealType: 0,
        buyerId
      })
      existingOrder.value = res.data
      pendingOrder.value = null
      ElMessage.success('已确认自提交易')
      sendSystemMsg('已确认自提交易，请线下完成交易后核销')
    } else if (info.action === 'delivery') {
      // 配送确认：创建订单 + 推送派单大厅
      const res = await createOrder({
        goodsId: info.goodsId,
        dealType: 1,
        deliveryFeePayer: info.deliveryFeePayer,
        buyerId: info.initiatorId
      })
      existingOrder.value = res.data
      await confirmDelivery(res.data.id, info.deliveryFeePayer)
      ElMessage.success('已确认配送安排，已推送到派单大厅')
      sendSystemMsg('已确认配送安排，等待骑手接单')
    }
    pendingOrder.value = null
  } catch (e) { /* handled */ }
}

async function handleRejectOrder() {
  if (!pendingOrder.value) return
  sendSystemMsg('拒绝了交易请求，可重新发起')
  pendingOrder.value = null
  ElMessage.info('已拒绝')
}

function sendSystemMsg(content, extra) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({
      type: 'send_msg',
      sessionId,
      content,
      msgType: 3,
      extra: extra ? JSON.stringify(extra) : null
    }))
  }
}

// 检查消息中是否有对方发起的待确认交易请求
function checkPendingOrder(msgs) {
  if (existingOrder.value) {
    pendingOrder.value = null
    return
  }
  for (const msg of msgs) {
    if (msg.msgType === 3 && msg.extra) {
      try {
        const extra = typeof msg.extra === 'string' ? JSON.parse(msg.extra) : msg.extra
        // 只检测对方发起的请求（非自己发起）
        if ((extra?.action === 'self_pickup' || extra?.action === 'delivery')
            && extra.initiatorId && extra.initiatorId !== userId) {
          pendingOrder.value = extra
          return
        }
      } catch { /* ignore */ }
    }
  }
  // 没找到对方发起的请求
  pendingOrder.value = null
}

async function refreshOrderStatus() {
  if (!goodsInfo.value?.id) return
  try {
    const ordersRes = await getMyOrders()
    const activeOrder = (ordersRes.data || []).find(o =>
      o.goodsId === goodsInfo.value.id && o.status !== 3 && o.status !== 4
    )
    existingOrder.value = activeOrder || null
    // 订单已确认 → 清除待确认状态
    if (activeOrder) {
      pendingOrder.value = null
    }
  } catch (e) { /* ignore */ }
}

onMounted(async () => {
  loadMessages()
  connectWebSocket()
  // 加载会话信息获取商品ID
  try {
    const res = await getMySessions()
    const session = (res.data || []).find(s => s.id === sessionId)
    if (session?.goodsId) {
      const goodsRes = await getGoodsDetail(session.goodsId)
      goodsInfo.value = goodsRes.data
      otherNickname.value = session.otherNickname || '对方'
      await refreshOrderStatus()
    }
  } catch (e) { /* ignore */ }
})

onUnmounted(() => {
  if (ws) ws.close()
})
</script>

<style scoped>
.chat-room {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
}

.goods-card {
  margin: 12px 0;
}

.goods-brief {
  display: flex;
  gap: 12px;
  align-items: center;
}

.goods-title {
  font-weight: 500;
  margin-bottom: 4px;
}

.goods-price {
  color: #f56c6c;
  font-weight: bold;
}

.img-placeholder {
  width: 60px;
  height: 60px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.msg-item {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  align-items: flex-start;
}

.msg-item.self {
  flex-direction: row-reverse;
}

.bubble {
  max-width: 65%;
  padding: 10px 14px;
  border-radius: 12px;
  background: #f0f2f5;
  line-height: 1.6;
}

.msg-item.self .bubble {
  background: #409eff;
  color: white;
}

.msg-content-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.msg-item.other .msg-content-wrapper {
  align-items: flex-start;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 11px;
}

.msg-time {
  color: #c0c4cc;
}

.read-status .read {
  color: #67c23a;
}

.read-status .unread {
  color: #c0c4cc;
}

.new-badge {
  background: #409eff;
  color: #fff;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 8px;
}

.unread-bubble {
  border: 2px solid #409eff;
}

.system-msg {
  width: 100%;
  text-align: center;
}

.msg-action p {
  margin: 4px 0 0;
  font-size: 13px;
}

.quick-actions {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}

.input-area {
  padding: 12px;
  border-top: 1px solid #ebeef5;
}

.input-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}

.toolbar-icon {
  cursor: pointer;
  color: #606266;
  transition: color 0.2s;
}

.toolbar-icon:hover {
  color: #409eff;
}

.input-row {
  display: flex;
  gap: 8px;
}
</style>
