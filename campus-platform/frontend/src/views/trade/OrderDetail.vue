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
        <el-tag v-if="order.status === 5" type="warning">等待跑腿接单中...</el-tag>
        <el-tag v-if="order.status === 7" type="primary">跑腿已接单，等待取货</el-tag>
        <el-tag v-if="order.status === 8" type="primary">配送中，请等待送达</el-tag>
        <el-button v-if="isBuyer && order.status === 9" type="success"
                   @click="handleConfirmReceive">确认收货（线下付款）</el-button>
      </div>

      <!-- 通用操作 -->
      <div class="actions" style="margin-top: 12px">
        <el-button v-if="canCancel" @click="handleCancel">取消订单</el-button>
        <el-button v-if="order.status === 3 && !reviewed" type="warning" @click="showReview = true">
          去评价
        </el-button>
        <el-tag v-if="reviewed" type="success">已评价</el-tag>
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getOrderDetail, confirmOrder, completeOrder,
  payDeliveryFee, confirmReceive, cancelOrder, createReview
} from '@/api/order'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const verifyCode = ref('')
const showReview = ref(false)
const reviewed = ref(false)
const submittingReview = ref(false)
const reviewTags = ['态度好', '描述准确', '发货快', '物品成色好', '沟通顺畅']
const reviewForm = ref({ rating: 5, tags: [], content: '' })

function toggleTag(tag) {
  const idx = reviewForm.value.tags.indexOf(tag)
  if (idx > -1) reviewForm.value.tags.splice(idx, 1)
  else reviewForm.value.tags.push(tag)
}

async function handleSubmitReview() {
  submittingReview.value = true
  try {
    await createReview({
      orderId: order.value.id,
      rating: reviewForm.value.rating,
      content: reviewForm.value.content
    })
    ElMessage.success('评价成功')
    showReview.value = false
    reviewed.value = true
  } finally {
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
  return s !== 3 && s !== 4 && s !== 8 // 已完成、已取消、配送中不能取消
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
}

async function handleConfirm() {
  await confirmOrder(order.value.id)
  ElMessage.success('已确认')
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

async function handleCompleteNoCode() {
  await completeOrder(order.value.id)
  ElMessage.success('交易完成')
  loadOrder()
}

async function handlePayFee() {
  await payDeliveryFee(order.value.id)
  ElMessage.success('服务费支付成功')
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
