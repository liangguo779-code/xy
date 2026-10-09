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
            <!-- 已撤回消息 -->
            <div v-if="msg.recallTime" class="bubble recalled">
              <span class="recall-text">消息已撤回</span>
            </div>
            <!-- 正常消息 -->
            <div v-else class="bubble" :class="{ 'unread-bubble': isUnread(msg) }">
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
              <span v-if="msg.senderId === userId && !msg.recallTime" class="read-status">
                <span v-if="msg.isRead === 1" class="read">已读</span>
                <span v-else class="unread">未读</span>
              </span>
              <span v-else-if="msg.isRead === 0 && !msg.recallTime" class="new-badge">新</span>
              <span v-if="canRecall(msg)" class="recall-btn" @click="handleRecall(msg)">撤回</span>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- 订单已完成提示 -->
    <div v-if="orderCompleted" class="quick-actions">
      <el-tag type="success" size="large">订单已完成</el-tag>
    </div>

    <!-- 快捷操作按钮 -->
    <div class="quick-actions" v-else>
      <!-- 发起交易（自提/配送） -->
      <el-dropdown trigger="click" @command="handleAction"
                   v-if="!existingOrder && !pendingOrder">
        <el-button size="small" type="primary" plain :disabled="goodsInfo?.status === 1 || goodsInfo?.status === 2 || goodsInfo?.status === 3">
          {{ goodsInfo?.status === 1 ? '商品已下架' : goodsInfo?.status === 2 ? '商品已售出' : goodsInfo?.status === 3 ? '商品已被预订' : '发起交易' }} <el-icon><ArrowDown /></el-icon>
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
/**
 * 聊天室：单会话实时聊天与交易协商
 * WebSocket 实时收消息与撤回推送，HTTP 发送兜底；快捷操作发起自提/配送并联动订单状态
 */
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { getMessages, getMySessions, recallMessage, sendMessage } from '@/api/chat'
import { createOrder, confirmDelivery, getMyOrders } from '@/api/order'
import { getGoodsDetail } from '@/api/goods'
import { useChatStore } from '@/stores/chat'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
/** 会话 ID 取自路由参数，进入页面后所有收发消息都限定在该会话内 */
const sessionId = Number(route.params.sessionId)
// 与后端约定登录态里存有 userId，用于判断消息方向（自己/对方）与撤回权限
const userId = Number(localStorage.getItem('userId') || 0)
const otherNickname = ref('对方')
/** 会话关联的商品信息，来自会话的 goodsId，用于顶部卡片与交易校验 */
const goodsInfo = ref(null)
const messages = ref([])
const inputText = ref('')
const sending = ref(false)
const messagesRef = ref()
/** 待确认的交易请求（含 action/goodsId/deliveryFeePayer/initiatorId），双方确认后才真正建单 */
const pendingOrder = ref(null)
/** 交易请求有效期：12 小时，超时请求作废、可重新发起 */
const REQUEST_EXPIRE_MS = 12 * 60 * 60 * 1000
/** 请求到期自动失效定时器：页面停留期间到点即清掉待确认状态 */
let expireTimer = null
/** 该商品已存在的进行中订单，存在时禁止再次发起交易 */
const existingOrder = ref(null)
/** 存在已完成订单时隐藏快捷操作，仅展示"订单已完成"提示 */
const orderCompleted = ref(false)
/** 会话对方的用户 ID，卖家确认交易时作为 buyerId 传给后端 */
const sessionOtherUserId = ref(null)
const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token')}` }

/** 聊天 WebSocket 实例，仅用于收消息/撤回推送与已读上报，发送走 HTTP 接口 */
let ws = null

/** 滚动到底部，nextTick 等消息渲染完成后再取 scrollHeight */
function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

/** 消息时间显示：当天只显时分，更早的补上日期 */
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

/** 对方发来的未读消息用于气泡高亮与"新"标记 */
function isUnread(msg) {
  return msg.senderId !== userId && msg.isRead === 0
}

/** 可撤回：仅自己发的文本/图片消息，且发出后 2 分钟内（含 30 秒时钟偏差容错） */
function canRecall(msg) {
  if (Number(msg.senderId) !== userId || msg.recallTime) return false
  if (msg.msgType !== 0 && msg.msgType !== 1) return false
  const createTime = new Date(msg.createTime)
  const now = new Date()
  const diff = now.getTime() - createTime.getTime()
  // diff 允许为负（-30 秒内）：客户端与服务器时钟可能不同步
  return diff > -30000 && diff < 2 * 60 * 1000
}

/** 撤回消息：调接口后就地改成本地撤回态，避免整列表重拉 */
async function handleRecall(msg) {
  try {
    await ElMessageBox.confirm('确定撤回此消息？', '提示', { type: 'warning' })
  } catch { return }
  try {
    const res = await recallMessage(msg.id)
    const data = res.data
    msg.recallTime = data.recallTime
    msg.content = data.content
    msg.extra = data.extra
    ElMessage.success('已撤回')
  } catch {
    // 拦截器已统一处理错误提示
  }
}

/** 商品图字段可能是 JSON 字符串或数组，统一取第一张作为封面 */
function getGoodsImage(goods) {
  if (!goods?.images) return ''
  try {
    const imgs = typeof goods.images === 'string' ? JSON.parse(goods.images) : goods.images
    return imgs?.[0] || ''
  } catch { return '' }
}

/** 建立聊天 WebSocket，token 走查询参数鉴权；消息发送仍走 HTTP 接口 */
function connectWebSocket() {
  const token = localStorage.getItem('token')
  const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${wsProtocol}//${window.location.host}/ws/chat?token=${token}`)

  ws.onmessage = (event) => {
    const data = JSON.parse(event.data)
    // 处理消息撤回
    if (data.type === 'recall_msg' && data.data?.sessionId === sessionId) {
      const idx = messages.value.findIndex(m => m.id === data.data.messageId)
      if (idx !== -1) {
        messages.value[idx].recallTime = new Date().toISOString()
        messages.value[idx].content = null
        messages.value[idx].extra = null
      }
      return
    }
    // 后端延时队列判定交易请求过期：清掉待确认状态，可重新发起
    if (data.type === 'trade_request_expired' && data.data?.sessionId === sessionId) {
      const idx = messages.value.findIndex(m => m.id === data.data.messageId)
      if (idx !== -1 && messages.value[idx].extra) {
        try {
          const extra = typeof messages.value[idx].extra === 'string'
            ? JSON.parse(messages.value[idx].extra) : messages.value[idx].extra
          extra.expired = true
          messages.value[idx].extra = JSON.stringify(extra)
        } catch { /* ignore */ }
      }
      pendingOrder.value = null
      ElMessage.info('交易请求已过期，可重新发起')
      return
    }
    if (data.type === 'new_msg' && data.data?.sessionId === sessionId) {
      const msg = data.data
      // 去重：按 ID 判断是否已存在
      if (messages.value.some(m => m.id === msg.id)) return
      messages.value.push(msg)
      scrollToBottom()
      // 检测交易请求（包括自己发起的，刷新页面后恢复状态）；过期/拒绝的请求不挂起
      if (msg.msgType === 3 && msg.extra) {
        try {
          const extra = typeof msg.extra === 'string' ? JSON.parse(msg.extra) : msg.extra
          if ((extra?.action === 'self_pickup' || extra?.action === 'delivery')
              && extra.initiatorId && !existingOrder.value
              && !isRequestExpired(extra, msg.createTime)) {
            applyRequest(extra, msg.createTime)
          } else if (extra?.action === 'rejected' || extra?.action === 'confirmed') {
            pendingOrder.value = null
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

/** 首次加载最近 100 条消息，并同步恢复交易请求与订单状态 */
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

/** 发送文本消息，接口回包后本地追加并滚到底部 */
async function handleSend() {
  const text = inputText.value.trim()
  if (!text) return

  try {
    const res = await sendMessage({ sessionId, content: text, msgType: 0 })
    const msg = res.data
    // 按 ID 去重：HTTP 回包与 WS 广播可能重复送达同一条消息
    if (msg && !messages.value.some(m => m.id === msg.id)) {
      messages.value.push(msg)
      scrollToBottom()
    }
  } catch { /* 拦截器已处理错误提示 */ }
  inputText.value = ''
}

/** 图片上传成功后转成 msgType 1 消息发出，图片地址存 extra，正文为占位文案 */
async function handleImageUpload(res) {
  if (res.code !== 200) return
  const imageUrl = res.data.url

  try {
    const result = await sendMessage({ sessionId, content: '[图片]', msgType: 1, extra: imageUrl })
    const msg = result.data
    // 去重：HTTP 回包与 WS 广播可能重复送达
    if (msg && !messages.value.some(m => m.id === msg.id)) {
      messages.value.push(msg)
      scrollToBottom()
    }
  } catch { /* 拦截器已处理错误提示 */ }
}

/** 发起交易请求（自提/配送），仅发快捷操作消息不建单，等对方确认后才创建订单 */
async function handleAction(action) {
  try {
    const goodsId = goodsInfo.value?.id
    if (!goodsId) {
      ElMessage.warning('无法获取商品信息')
      return
    }

    // 检查是否已有未处理的请求或订单
    if (existingOrder.value) {
      ElMessage.warning('该商品已有进行中的订单')
      return
    }
    if (pendingOrder.value && !isRequestExpired(pendingOrder.value)) {
      ElMessage.warning('已有一笔待确认的交易请求，请等待对方处理')
      return
    }

    if (action === 'self_pickup') {
      // 自提：只发消息不建单；后端在消息落库前扣减库存占位，失败（重复发起/被抢占）会中断发送
      const orderInfo = { action: 'self_pickup', goodsId, initiatorId: userId }
      if (await sendSystemMsg('请求约定自提，等待对方确认', orderInfo)) {
        applyRequest(orderInfo)
        ElMessage.success('已发送自提请求，等待对方确认')
      }
    } else if (action === 'delivery_buyer') {
      // 配送：只发消息不建单；后端在消息落库前扣减库存占位
      const orderInfo = { action: 'delivery', goodsId, deliveryFeePayer: 'buyer', initiatorId: userId }
      if (await sendSystemMsg('请求平台配送，我方承担跑腿费，等待对方确认', orderInfo)) {
        applyRequest(orderInfo)
        ElMessage.success('已发送配送请求，等待对方确认')
      }
    } else if (action === 'delivery_seller') {
      // 配送：只发消息不建单；后端在消息落库前扣减库存占位
      const orderInfo = { action: 'delivery', goodsId, deliveryFeePayer: 'seller', initiatorId: userId }
      if (await sendSystemMsg('请求平台配送，请求对方承担跑腿费，等待对方确认', orderInfo)) {
        applyRequest(orderInfo)
        ElMessage.success('已发送配送请求，等待对方确认')
      }
    }
  } catch (e) {
    // handled by interceptor
  }
}

/** 确认交易请求：自提建单，配送额外推送派单大厅等骑手接单 */
async function handleConfirmOrder() {
  if (!pendingOrder.value) return
  // 请求 12h 有效：过期即作废（定时器已兜底，这里防后台标签页定时器被节流）
  if (isRequestExpired(pendingOrder.value)) {
    ElMessage.warning('交易请求已过期，请重新发起')
    pendingOrder.value = null
    return
  }
  try {
    const info = pendingOrder.value
    if (info.action === 'self_pickup') {
      // 自提确认：创建订单
      // 买家判定：当前用户是卖家 → 对方是买家；当前用户是买家 → 自己是买家
      const isCurrentUserSeller = goodsInfo.value?.userId === userId
      const buyerId = isCurrentUserSeller ? sessionOtherUserId.value : userId
      if (isCurrentUserSeller && !buyerId) {
        ElMessage.error('对方用户信息不可用，无法创建订单')
        return
      }
      const res = await createOrder({
        goodsId: info.goodsId,
        dealType: 0,
        buyerId,
        // 会话ID用于后端校验库存占位归属并转移给订单（发起交易时已扣减）
        chatSessionId: sessionId
      })
      existingOrder.value = res.data
      pendingOrder.value = null
      ElMessage.success('已确认自提交易')
      await sendSystemMsg('已确认自提交易，请线下完成交易后核销', { action: 'confirmed' })
    } else if (info.action === 'delivery') {
      // 配送确认：创建订单 + 推送派单大厅
      const isCurrentUserSeller = goodsInfo.value?.userId === userId
      const buyerId = isCurrentUserSeller ? sessionOtherUserId.value : userId
      if (isCurrentUserSeller && !buyerId) {
        ElMessage.error('对方用户信息不可用，无法创建订单')
        return
      }
      const res = await createOrder({
        goodsId: info.goodsId,
        dealType: 1,
        deliveryFeePayer: info.deliveryFeePayer,
        buyerId,
        // 会话ID用于后端校验库存占位归属并转移给订单（发起交易时已扣减）
        chatSessionId: sessionId
      })
      existingOrder.value = res.data
      await confirmDelivery(res.data.id, info.deliveryFeePayer)
      ElMessage.success('已确认配送安排，已推送到派单大厅')
      await sendSystemMsg('已确认配送安排，等待骑手接单', { action: 'confirmed' })
    }
    pendingOrder.value = null
  } catch (e) { /* handled */ }
}

/** 拒绝交易请求：发带 rejected 标记的系统消息（后端借此释放库存占位），成功后清待确认状态，可重新发起 */
async function handleRejectOrder() {
  if (!pendingOrder.value) return
  if (await sendSystemMsg('拒绝了交易请求，可重新发起', { action: 'rejected' })) {
    pendingOrder.value = null
    ElMessage.info('已拒绝')
  }
}

/** 发送快捷操作消息（msgType 3），extra 存交易参数 JSON 供双方恢复协商状态；返回是否发送成功 */
async function sendSystemMsg(content, extra) {
  try {
    const res = await sendMessage({
      sessionId,
      content,
      msgType: 3,
      extra: extra ? JSON.stringify(extra) : null
    })
    const msg = res.data
    // 同样按 ID 去重，防止 WS 广播重复
    if (msg && !messages.value.some(m => m.id === msg.id)) {
      messages.value.push(msg)
      scrollToBottom()
    }
    return true
  } catch {
    /* 拦截器已处理错误提示（如库存扣减失败：重复发起/被其他买家抢占） */
    return false
  }
}

/** 交易请求是否已过期：优先认后端过期标记 expired，其次按 expireAt/消息时间兜底判定时效 */
function isRequestExpired(info, msgCreateTime) {
  if (info?.expired) return true
  const expireAt = info?.expireAt
    || (msgCreateTime ? new Date(msgCreateTime).getTime() + REQUEST_EXPIRE_MS : 0)
  return expireAt > 0 && Date.now() > expireAt
}

/** 挂起待确认请求并安排到点自动失效；发起方调用时补写 expireAt，保证双方以同一 deadline 判过期 */
function applyRequest(info, msgCreateTime) {
  if (!info.expireAt) {
    info.expireAt = msgCreateTime
      ? new Date(msgCreateTime).getTime() + REQUEST_EXPIRE_MS
      : Date.now() + REQUEST_EXPIRE_MS
  }
  if (isRequestExpired(info)) {
    pendingOrder.value = null
    return
  }
  pendingOrder.value = info
  if (expireTimer) clearTimeout(expireTimer)
  expireTimer = setTimeout(() => {
    // 到点自动作废：仅当请求仍挂着才提示（可能已被确认/拒绝抢先清掉）
    if (!pendingOrder.value) return
    pendingOrder.value = null
    ElMessage.info('交易请求已过期，可重新发起')
  }, info.expireAt - Date.now())
}

// 从最新消息往前查找待确认的交易请求（包括自己发起的），只认最新一条：
// 过期请求视为不存在（可重新发起），被拒绝同样作废
function checkPendingOrder(msgs) {
  if (existingOrder.value) {
    pendingOrder.value = null
    return
  }
  for (let i = msgs.length - 1; i >= 0; i--) {
    const msg = msgs[i]
    if (msg.msgType === 3 && msg.extra) {
      try {
        const extra = typeof msg.extra === 'string' ? JSON.parse(msg.extra) : msg.extra
        if ((extra?.action === 'self_pickup' || extra?.action === 'delivery')
            && extra.initiatorId) {
          if (!isRequestExpired(extra, msg.createTime)) {
            applyRequest(extra, msg.createTime)
          } else {
            pendingOrder.value = null
          }
          return
        }
        if (extra?.action === 'rejected' || extra?.action === 'confirmed') {
          pendingOrder.value = null
          return
        }
      } catch { /* ignore */ }
    }
  }
  pendingOrder.value = null
}

/** 根据该商品的订单状态刷新交易入口：有进行中订单则禁发起，有已完成订单则整栏隐藏 */
async function refreshOrderStatus() {
  if (!goodsInfo.value?.id) return
  try {
    // 同时查询买家和卖家订单，确保双方都能看到
    const [buyerRes, sellerRes] = await Promise.all([
      getMyOrders({ role: 'buyer', page: 1, size: 50 }),
      getMyOrders({ role: 'seller', page: 1, size: 50 })
    ])
    const allOrders = [
      ...(buyerRes.data?.records || []),
      ...(sellerRes.data?.records || [])
    ]
    const orders = allOrders.filter(o => o.goodsId === goodsInfo.value.id)
    // 进行中订单排除 3-已完成 与 4-已取消
    const activeOrder = orders.find(o => o.status !== 3 && o.status !== 4)
    existingOrder.value = activeOrder || null
    // 订单已确认 → 清除待确认状态
    if (activeOrder) {
      pendingOrder.value = null
    }
    // 存在已完成的订单 → 隐藏快捷操作，显示"订单已完成"
    if (orders.some(o => o.status === 3)) {
      orderCompleted.value = true
    }
  } catch (e) { /* ignore */ }
}

/** 进入聊天室：拉历史消息、建 WS 长连接，再补商品信息用于交易校验 */
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
      sessionOtherUserId.value = session.otherUserId
      await refreshOrderStatus()
    }
  } catch (e) { /* ignore */ }
})

// 离开页面即断开 WS 并清掉过期定时器，防止旧连接/定时器泄漏
onUnmounted(() => {
  if (ws) ws.close()
  if (expireTimer) clearTimeout(expireTimer)
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
  border-radius: 12px;
}

.goods-brief {
  display: flex;
  gap: 12px;
  align-items: center;
}

.goods-title {
  font-weight: 600;
  margin-bottom: 4px;
  color: #1D2129;
}

.goods-price {
  color: #F56C6C;
  font-weight: 700;
}

.img-placeholder {
  width: 60px;
  height: 60px;
  background: #F2F3F5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #C9CDD4;
  border-radius: 8px;
}

.messages {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px;
}

.msg-item {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  align-items: flex-start;
  min-width: 0;
}

.msg-item.self {
  flex-direction: row-reverse;
}

.bubble {
  max-width: 65%;
  padding: 10px 14px;
  border-radius: 16px;
  background: #F2F3F5;
  line-height: 1.6;
  color: #1D2129;
  word-break: break-word;
  overflow-wrap: break-word;
  min-width: 0;
}

.msg-item.self .bubble {
  background: linear-gradient(135deg, #5B8FF9, #6366F1);
  color: #fff;
}

.msg-content-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  max-width: calc(100% - 58px);
  min-width: 0;
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
  color: #C9CDD4;
}

.read-status .read {
  color: #52C41A;
}

.read-status .unread {
  color: #C9CDD4;
}

.new-badge {
  background: #5B8FF9;
  color: #fff;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
}

.unread-bubble {
  border: 2px solid #5B8FF9;
}

.recalled {
  background: #f5f5f5 !important;
  color: #999 !important;
}

.recall-text {
  font-size: 13px;
  font-style: italic;
}

.recall-btn {
  color: #909399;
  cursor: pointer;
  font-size: 11px;
}

.recall-btn:hover {
  color: #409eff;
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
  padding: 10px 12px;
  border-top: 1px solid #F2F3F5;
  background: #F7F8FA;
}

.input-area {
  padding: 12px;
  border-top: 1px solid #F2F3F5;
}

.input-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}

.toolbar-icon {
  cursor: pointer;
  color: #4E5969;
  transition: color 0.2s;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.toolbar-icon:hover {
  color: #5B8FF9;
  background: #E8EEFE;
}

.input-row {
  display: flex;
  gap: 8px;
}
</style>
