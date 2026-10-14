<template>
  <div class="ticket-detail" v-if="ticket">
    <el-page-header @back="router.push('/orders')">
      <template #content>热卖订单</template>
    </el-page-header>

    <el-card style="margin-top: 20px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="订单号">{{ ticket.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(ticket.status)">{{ ticket.phaseDesc }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="商品">{{ ticket.goodsTitle }}</el-descriptions-item>
        <el-descriptions-item label="成交价">
          ¥{{ ticket.goodsAmount }}<template v-if="ticket.qty > 1">（×{{ ticket.qty }}）</template>
        </el-descriptions-item>
        <el-descriptions-item label="交易方式">平台配送</el-descriptions-item>
        <el-descriptions-item v-if="ticket.serviceFee" label="服务费">¥{{ ticket.serviceFee }}</el-descriptions-item>
        <el-descriptions-item label="收货地址" :span="2">{{ ticket.address }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(ticket.createdAt) }}</el-descriptions-item>
      </el-descriptions>

      <!-- 待确认：倒计时 + 确认/取消 -->
      <div v-if="ticket.phase === 'PENDING_CONFIRM'" class="actions">
        <el-card shadow="never" class="confirm-panel">
          <div class="confirm-title">
            请确认购买承诺
            <span class="countdown" :class="{ urgent: remainSeconds <= 10 }">
              剩余 {{ formatCountdown(remainSeconds) }}
            </span>
          </div>
          <p class="confirm-desc">
            已为您锁定库存（{{ ticket.qty || 1 }} 件，¥{{ ticket.goodsAmount }}，线下收款）。
            请在倒计时结束前确认，超时将自动取消并释放库存。
            确认后订单进入派单大厅，<b>不可自助取消</b>，如有问题请联系客服或发起纠纷。
          </p>
          <div class="confirm-actions">
            <el-button type="danger" size="large" :loading="confirming" @click="handleConfirm">
              确认购买
            </el-button>
            <el-button size="large" :loading="cancelling" @click="handleCancel">取消订单</el-button>
          </div>
        </el-card>
      </div>

      <!-- 处理中：轮询等 worker 落库 -->
      <div v-else-if="ticket.phase === 'PROCESSING'" class="actions">
        <el-card shadow="never" class="processing-panel">
          <div class="processing-title">
            <el-icon class="is-loading" style="margin-right: 8px"><Loading /></el-icon>
            订单处理中…
          </div>
          <p class="processing-desc">
            已确认购买，系统正在生成订单并推送至派单大厅，通常 1~2 秒内完成。
          </p>
          <p v-if="pollTimeout" class="processing-desc">
            仍在处理中，请稍后在订单列表查看
            <el-button text type="primary" size="small" @click="loadTicket">刷新</el-button>
          </p>
        </el-card>
      </div>

      <!-- 终态：已取消/已超时 -->
      <div v-else-if="ticket.phase === 'CANCELLED'" class="actions">
        <el-card shadow="never" class="terminal-panel">
          <p class="processing-desc">该票据已取消，库存已释放。</p>
          <el-button type="primary" @click="router.replace('/goods/' + ticket.goodsId)">重新下单</el-button>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup>
/**
 * 热卖票据详情页：待确认显示倒计时与确认/取消，确认后进入「处理中」轮询，
 * worker 落库成功即拿到 orderId 并跳转订单详情。票据无 DB id，一律按 orderNo 定位。
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { getHotTicket, confirmHotTicket, cancelHotTicket } from '@/api/order'

const route = useRoute()
const router = useRouter()
const ticket = ref(null)
const remainSeconds = ref(0)
const confirming = ref(false)
const cancelling = ref(false)
const pollTimeout = ref(false)

let countdownTimer = null
let pollTimer = null
let pollCount = 0

/** 状态码 → el-tag 颜色（与 OrderList 共用色板） */
function statusType(s) {
  const map = {
    0: 'warning', 1: '', 3: 'success', 4: 'danger', 5: 'warning',
    6: '', 7: '', 8: '', 9: 'success', 10: 'warning', 11: 'info'
  }
  return map[s] || 'info'
}

/** epoch 毫秒 → 本地时间串 */
function formatTime(ms) {
  return ms ? new Date(ms).toLocaleString() : '—'
}

/** 秒数格式化为 mm:ss */
function formatCountdown(sec) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

/** 加载票据：按 phase 决定启动倒计时 / 轮询 / 直接跳转 */
async function loadTicket() {
  try {
    const res = await getHotTicket(route.params.orderNo)
    ticket.value = res.data
  } catch {
    // 票据不存在（已过期被清理且无订单行）
    ElMessage.error('票据不存在或已过期')
    router.replace('/orders')
    return
  }

  const phase = ticket.value.phase
  if (phase === 'DONE' && ticket.value.orderId) {
    router.replace('/orders/' + ticket.value.orderId)
    return
  }
  if (phase === 'PENDING_CONFIRM') {
    stopPoll()
    startCountdown()
    return
  }
  if (phase === 'PROCESSING') {
    stopCountdown()
    startPoll()
    return
  }
  // CANCELLED 或其它终态
  stopCountdown()
  stopPoll()
}

/** 待确认倒计时：本地 setInterval，归零后刷新等扫描落地 */
function startCountdown() {
  stopCountdown()
  const expireAt = ticket.value.expireAt
  remainSeconds.value = expireAt
    ? Math.max(0, Math.floor((expireAt - Date.now()) / 1000))
    : 0
  countdownTimer = setInterval(() => {
    if (remainSeconds.value > 0) {
      remainSeconds.value -= 1
    } else {
      stopCountdown()
      loadTicket()
    }
  }, 1000)
}

function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

/** 处理中轮询：1s 一次，约 30s 仍未出结果则降级提示 */
function startPoll() {
  stopPoll()
  pollCount = 0
  pollTimeout.value = false
  pollTimer = setInterval(async () => {
    pollCount += 1
    if (pollCount > 30) {
      stopPoll()
      pollTimeout.value = true
      return
    }
    await loadTicket()
  }, 1000)
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

/** 确认购买：消息迁入已确认队列，随即进入「处理中」 */
async function handleConfirm() {
  confirming.value = true
  try {
    await confirmHotTicket(ticket.value.orderNo, ticket.value.goodsId)
    ElMessage.success('已确认购买，订单处理中')
    await loadTicket()
  } catch { /* 错误已由拦截器弹出 */ }
  finally {
    confirming.value = false
  }
}

/** 待确认期取消：释放库存 */
async function handleCancel() {
  cancelling.value = true
  try {
    await cancelHotTicket(ticket.value.orderNo, ticket.value.goodsId)
    ElMessage.success('订单已取消，库存已释放')
    await loadTicket()
  } catch { /* 错误已由拦截器弹出 */ }
  finally {
    cancelling.value = false
  }
}

onMounted(loadTicket)
onUnmounted(() => {
  stopCountdown()
  stopPoll()
})
</script>

<style scoped>
.actions {
  margin-top: 20px;
}

.confirm-panel {
  border-color: #fde2e2;
  background: #fef0f0;
}

.confirm-title {
  font-size: 16px;
  font-weight: 600;
  color: #1D2129;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.countdown {
  font-size: 20px;
  font-weight: 700;
  color: #E6A23C;
  font-variant-numeric: tabular-nums;
}

.countdown.urgent {
  color: #F56C6C;
}

.confirm-desc,
.processing-desc {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
  margin: 0 0 14px;
}

.confirm-actions {
  display: flex;
  gap: 12px;
}

.processing-panel {
  border-color: #d9ecff;
  background: #ecf5ff;
}

.processing-title {
  font-size: 16px;
  font-weight: 600;
  color: #1D2129;
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.terminal-panel {
  border-color: #ebeef5;
  background: #f5f7fa;
}
</style>
