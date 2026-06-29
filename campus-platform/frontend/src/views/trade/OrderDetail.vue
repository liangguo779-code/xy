<template>
  <div class="order-detail" v-if="order">
    <el-page-header @back="router.push('/orders')">
      <template #content>订单详情</template>
    </el-page-header>

    <el-card style="margin-top: 20px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(order.status)">{{ order.statusDesc }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="商品">{{ order.goodsTitle }}</el-descriptions-item>
        <el-descriptions-item label="成交价">¥{{ order.goodsAmount }}</el-descriptions-item>
        <el-descriptions-item label="交易方式">
          {{ order.dealType === 0 ? '线下自提' : '平台配送' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="order.serviceFee" label="服务费">
          ¥{{ order.serviceFee }}
        </el-descriptions-item>
        <el-descriptions-item label="买家">{{ order.buyerNickname }}</el-descriptions-item>
        <el-descriptions-item label="卖家">{{ order.sellerNickname }}</el-descriptions-item>
        <el-descriptions-item v-if="order.pickupLocation" label="自提地点">
          {{ order.pickupLocation }}
        </el-descriptions-item>
        <!-- 核销码只对买家显示 -->
        <el-descriptions-item label="创建时间">{{ order.createTime }}</el-descriptions-item>
      </el-descriptions>

      <!-- 自提流程操作 -->
      <div v-if="order.dealType === 0" class="actions">
        <!-- 卖家确认订单 -->
        <div v-if="isSeller && order.status === 0" class="verify-area">
          <el-button type="primary" @click="handleConfirm">确认交易</el-button>
        </div>
        <!-- 卖家核销 -->
        <div v-if="isSeller && order.status === 1" class="verify-area">
          <el-input v-model="verifyCode" placeholder="输入买家提供的核销码" style="width: 200px" />
          <el-button type="success" @click="handleComplete">核销完成</el-button>
        </div>
        <!-- 买家看到核销码 -->
        <div v-if="isBuyer && order.status === 1" class="verify-tip">
          <el-card shadow="never" style="background: #ecf5ff; border-color: #b3d8ff">
            <div style="text-align: center">
              <p style="font-size: 14px; color: #606266; margin-bottom: 12px">请将核销码提供给卖家完成交易</p>
              <div style="font-size: 32px; font-weight: bold; color: #409eff; letter-spacing: 4px; margin-bottom: 12px">
                {{ order.verifyCode }}
              </div>
              <p style="font-size: 12px; color: #909399">交易金额 ¥{{ order.goodsAmount }} 由买卖双方线下结算</p>
            </div>
          </el-card>
        </div>
      </div>

      <!-- 配送流程操作 -->
      <div v-if="order.dealType === 1" class="actions">
        <el-tag v-if="order.status === 5" type="warning">等待对方确认配送安排...</el-tag>
        <el-tag v-if="order.status === 6" type="info">等待骑手接单...</el-tag>
        <el-tag v-if="order.status === 7" type="primary">骑手已接单，等待取货</el-tag>
        <el-tag v-if="order.status === 8" type="primary">配送中，请等待送达</el-tag>
        <el-button v-if="isBuyer && order.status === 9" type="success"
                   @click="handleConfirmReceive">确认收货（线下付款）</el-button>

        <!-- 物流轨迹 -->
        <div v-if="deliveryTracks.length" class="delivery-tracks">
          <div class="tracks-title">物流轨迹</div>
          <el-timeline>
            <el-timeline-item v-for="t in deliveryTracks" :key="t.id"
                              :timestamp="t.createTime" placement="top"
                              :type="trackType(t.action)">
              {{ trackDesc(t.action) }}
              <el-image v-if="t.photoUrl" :src="t.photoUrl" fit="cover"
                        style="width: 80px; height: 80px; border-radius: 6px; margin-top: 4px"
                        :preview-src-list="[t.photoUrl]" />
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>

      <!-- 通用操作 -->
      <div class="actions" style="margin-top: 12px">
        <el-button v-if="canCancel" @click="handleCancel">取消订单</el-button>
        <el-button v-if="order.status === 3 && !reviewed" type="warning" @click="showReview = true">
          去评价
        </el-button>
        <template v-if="reviewed">
          <el-tag type="success">已评价</el-tag>
          <el-button v-if="canEditReview" text type="primary" size="small" @click="handleEditReview">修改</el-button>
          <el-button v-if="canEditReview" text type="danger" size="small" @click="handleDeleteReview">删除</el-button>
        </template>
        <el-button @click="handleContact">联系对方</el-button>
        <el-button v-if="order.status === 3 || order.status === 4" type="danger" plain @click="showDispute = true">
          发起纠纷
        </el-button>
      </div>
    </el-card>

    <!-- 评价弹窗 -->
    <el-dialog v-model="showReview" title="评价交易" width="420">
      <el-form label-width="60px">
        <el-form-item label="评分">
          <el-rate v-model="reviewForm.rating" :max="5" />
        </el-form-item>
        <el-form-item label="标签">
          <div style="display: flex; flex-wrap: wrap; gap: 8px">
            <el-check-tag v-for="tag in reviewTags" :key="tag" :checked="reviewForm.tags.includes(tag)"
                          @change="toggleTag(tag)">{{ tag }}</el-check-tag>
          </div>
        </el-form-item>
        <el-form-item label="评价">
          <el-input v-model="reviewForm.content" type="textarea" :rows="3" placeholder="说说你的交易体验..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReview = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitReview" :loading="submittingReview">提交评价</el-button>
      </template>
    </el-dialog>

    <!-- 纠纷弹窗 -->
    <el-dialog v-model="showDispute" title="发起纠纷" width="420">
      <el-form label-width="70px">
        <el-form-item label="纠纷原因">
          <el-radio-group v-model="disputeForm.reason">
            <el-radio value="商品与描述不符">商品与描述不符</el-radio>
            <el-radio value="商品有损坏">商品有损坏</el-radio>
            <el-radio value="未收到商品">未收到商品</el-radio>
            <el-radio value="其他">其他</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="补充说明">
          <el-input v-model="disputeForm.evidence" type="textarea" :rows="3" placeholder="请详细描述纠纷情况..." />
        </el-form-item>
        <el-form-item label="证据图片">
          <el-upload action="/api/upload/image" :headers="uploadHeaders"
                     list-type="picture-card" :limit="5"
                     :on-success="handleDisputeUploadSuccess" :on-remove="handleDisputeUploadRemove">
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDispute = false">取消</el-button>
        <el-button type="danger" @click="handleSubmitDispute" :loading="submittingDispute">提交纠纷</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getOrderDetail, completeOrder, confirmOrder, confirmReceive, cancelOrder, createReview
} from '@/api/order'
import { getDeliveryTracks } from '@/api/delivery'
import { createDispute } from '@/api/dispute'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const verifyCode = ref('')
const showReview = ref(false)
const reviewed = ref(false)
const submittingReview = ref(false)
const reviewTags = ['态度好', '描述准确', '发货快', '物品成色好', '沟通顺畅']
const reviewForm = ref({ rating: 5, tags: [], content: '' })
const deliveryTracks = ref([])
const showDispute = ref(false)
const submittingDispute = ref(false)
const disputeForm = ref({ reason: '商品与描述不符', evidence: '', evidenceImages: [] })
const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token')}` }
const myReview = ref(null)

// 评价24小时内可修改/删除
const canEditReview = computed(() => {
  if (!myReview.value) return false
  const createTime = new Date(myReview.value.createTime)
  const now = new Date()
  return (now - createTime) < 24 * 60 * 60 * 1000
})

function trackType(action) {
  return { accept: 'primary', pickup: 'warning', deliver: 'success', location: 'info' }[action] || 'info'
}

function trackDesc(action) {
  return { accept: '骑手已接单', pickup: '骑手已取货', deliver: '商品已送达', location: '位置更新' }[action] || action
}

function toggleTag(tag) {
  const idx = reviewForm.value.tags.indexOf(tag)
  if (idx > -1) reviewForm.value.tags.splice(idx, 1)
  else reviewForm.value.tags.push(tag)
}

async function handleSubmitReview() {
  submittingReview.value = true
  try {
    const tagsStr = reviewForm.value.tags.length > 0 ? JSON.stringify(reviewForm.value.tags) : undefined
    if (myReview.value) {
      // 编辑评价
      await request.put(`/api/reviews/${myReview.value.id}`, {
        orderId: order.value.id,
        rating: reviewForm.value.rating,
        content: reviewForm.value.content,
        tags: tagsStr
      })
      ElMessage.success('修改成功')
    } else {
      // 新建评价
      await createReview({
        orderId: order.value.id,
        rating: reviewForm.value.rating,
        content: reviewForm.value.content,
        tags: tagsStr
      })
      ElMessage.success('评价成功')
    }
    showReview.value = false
    loadOrder()
  } catch (e) { ElMessage.error(e.response?.data?.message || '评价失败') }
  finally {
    submittingReview.value = false
  }
}

const currentUserId = computed(() => {
  // 从 localStorage 或 store 获取
  return Number(localStorage.getItem('userId') || 0)
})
const isBuyer = computed(() => order.value?.buyerId === currentUserId.value)
const isSeller = computed(() => order.value?.sellerId === currentUserId.value)
const canCancel = computed(() => {
  if (!order.value) return false
  const s = order.value.status
  // 已完成(3)、已取消(4)、已派单(7)、已取货(8)、已送达(9) 不能取消
  return s !== 3 && s !== 4 && s !== 7 && s !== 8 && s !== 9
})

const statusType = (s) => {
  const map = {
    0: 'warning', 1: '', 3: 'success', 4: 'danger', 5: 'warning',
    6: '', 7: '', 8: '', 9: 'success'
  }
  return map[s] || 'info'
}

async function loadOrder() {
  const res = await getOrderDetail(route.params.id)
  order.value = res.data
  // 检查当前用户是否已评价
  try {
    const reviewRes = await request.get(`/api/reviews/order/${route.params.id}`)
    const reviews = reviewRes.data || []
    myReview.value = reviews.find(r => r.reviewerId === currentUserId.value) || null
    reviewed.value = !!myReview.value
  } catch { /* 查询评价状态失败不影响订单展示 */ }
  // 加载物流轨迹
  if (order.value.dealType === 1 && order.value.deliveryOrderId) {
    try {
      const trackRes = await getDeliveryTracks(order.value.deliveryOrderId)
      deliveryTracks.value = trackRes.data || []
    } catch { /* ignore */ }
  }
}

async function handleConfirm() {
  await confirmOrder(order.value.id)
  ElMessage.success('已确认交易，买家将收到核销码')
  loadOrder()
}

async function handleComplete() {
  if (!verifyCode.value) {
    ElMessage.warning('请输入核销码')
    return
  }
  await completeOrder(order.value.id, verifyCode.value)
  ElMessage.success('交易完成')
  loadOrder()
}

async function handleConfirmReceive() {
  await confirmReceive(order.value.id)
  ElMessage.success('已确认收货')
  loadOrder()
}

async function handleCancel() {
  await cancelOrder(order.value.id)
  ElMessage.success('已取消')
  loadOrder()
}

function handleEditReview() {
  if (!myReview.value) return
  reviewForm.value = {
    rating: myReview.value.rating,
    content: myReview.value.content || '',
    tags: myReview.value.tags ? JSON.parse(myReview.value.tags) : []
  }
  showReview.value = true
}

async function handleDeleteReview() {
  try {
    await ElMessageBox.confirm('确定删除此评价？', '提示', { type: 'warning' })
    await request.delete(`/api/reviews/${myReview.value.id}`)
    ElMessage.success('已删除')
    reviewed.value = false
    myReview.value = null
  } catch (e) {
    if (e !== 'cancel' && e?.message !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '删除失败')
    }
  }
}

async function handleContact() {
  const params = { goodsId: order.value.goodsId }
  // 卖家联系买家时，指定对方用户ID
  if (isSeller.value) {
    params.otherUserId = order.value.buyerId
  }
  const res = await request.post('/api/chat/session', null, { params })
  router.push(`/chat/${res.data.id}`)
}

function handleDisputeUploadSuccess(res) {
  if (res.code === 200) disputeForm.value.evidenceImages.push(res.data.url)
}

function handleDisputeUploadRemove(file) {
  const url = file.response?.data?.url || file.url
  const idx = disputeForm.value.evidenceImages.indexOf(url)
  if (idx > -1) disputeForm.value.evidenceImages.splice(idx, 1)
}

async function handleSubmitDispute() {
  submittingDispute.value = true
  try {
    await createDispute({
      orderId: order.value.id,
      reason: disputeForm.value.reason + (disputeForm.value.evidence ? '：' + disputeForm.value.evidence : ''),
      evidenceImages: disputeForm.value.evidenceImages.length > 0
        ? JSON.stringify(disputeForm.value.evidenceImages) : undefined
    })
    ElMessage.success('纠纷已提交')
    disputeForm.value = { reason: '商品与描述不符', evidence: '', evidenceImages: [] }
    showDispute.value = false
  } catch { ElMessage.error('提交失败') }
  finally { submittingDispute.value = false }
}

onMounted(loadOrder)
</script>

<style scoped>
.actions {
  margin-top: 20px;
}

.verify-area {
  display: flex;
  gap: 8px;
  align-items: center;
}

.verify-tip {
  margin-top: 12px;
}
</style>
