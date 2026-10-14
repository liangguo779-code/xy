<template>
  <div class="order-list">
    <div class="page-header">
      <h2 class="page-title">我的订单</h2>
    </div>

    <!-- 买/卖 tab -->
    <div class="role-tabs">
      <span :class="['role-tab', { active: roleFilter === 'buyer' }]" @click="roleFilter = 'buyer'; loadOrders()">我买到的</span>
      <span :class="['role-tab', { active: roleFilter === 'seller' }]" @click="roleFilter = 'seller'; loadOrders()">我卖出的</span>
    </div>

    <!-- 状态筛选 -->
    <el-radio-group v-model="statusFilter" @change="loadOrders" class="filter-group">
      <el-radio-button label="">全部</el-radio-button>
      <el-radio-button label="inProgress">进行中</el-radio-button>
      <el-radio-button label="3">已完成</el-radio-button>
      <el-radio-button label="4">已取消</el-radio-button>
    </el-radio-group>

    <!-- 订单卡片列表，点击进入订单详情（票据无 id，按 orderNo 跳票据页） -->
    <div class="order-cards">
      <div v-for="order in orders" :key="order.id || order.orderNo" class="order-card" @click="goDetail(order)">
        <div class="order-card-header">
          <span class="order-no">{{ order.orderNo }}</span>
          <el-tag :type="statusType(order.status)" size="small" effect="plain">{{ order.statusDesc }}</el-tag>
        </div>
        <div class="order-card-body">
          <el-image v-if="order.goodsImage" :src="order.goodsImage" fit="cover" class="order-thumb">
            <template #error><div class="thumb-placeholder">图</div></template>
          </el-image>
          <div class="order-info">
            <div class="order-goods-title">{{ order.goodsTitle }}</div>
            <div class="order-meta">
              <el-tag v-if="order.goodsType === 1" type="danger" size="small" effect="plain">热卖</el-tag>
              <el-tag :type="order.dealType === 0 ? 'info' : 'success'" size="small" effect="plain">
                {{ order.dealType === 0 ? '自提' : '配送' }}
              </el-tag>
              <span v-if="order.quantity > 1" class="order-time">×{{ order.quantity }}</span>
              <span class="order-time">{{ order.createTime }}</span>
            </div>
          </div>
          <div class="order-price">¥{{ order.goodsAmount }}</div>
        </div>
      </div>
    </div>

    <el-empty v-if="orders.length === 0" description="暂无订单" />
  </div>
</template>

<script setup>
/**
 * 我的订单页：按角色（我买到的/我卖出的）与状态筛选订单，点击卡片进入详情。
 * 热卖票据（尚未落库的在途单）合并展示在最前，无 id 的卡片按 orderNo 跳票据页。
 * 状态约定：0-待卖家确认 1-待核销 3-已完成 4-已取消；配送侧 5-协商跑腿费 6-待派单 7-待取货 8-配送中 9-待确认收货；
 * 票据侧 10-待确认 11-处理中。
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyOrders, getMyHotTickets } from '@/api/order'

const router = useRouter()
const orders = ref([])
/** 状态筛选值：空-全部，inProgress-后端聚合的进行中状态，3-已完成，4-已取消 */
const statusFilter = ref('')
/** 角色筛选：buyer-我买到的，seller-我卖出的，透传给后端 role 参数 */
const roleFilter = ref('buyer')

/** 订单状态映射 el-tag 颜色，数字为后端状态码 */
const statusType = (s) => {
  if (s === 3) return 'success'
  if (s === 4) return 'danger'
  if (s === 0 || s === 5 || s === 10) return 'warning'
  if (s === 11) return 'info'
  if (s === 7 || s === 8 || s === 9) return 'primary'
  return 'info'
}

/** 票据 VO → 与 OrderVO 同构的列表行（前端合并展示用） */
function normalizeTicket(t) {
  return {
    id: null,
    orderNo: t.orderNo,
    goodsId: t.goodsId,
    goodsTitle: t.goodsTitle,
    goodsImage: t.goodsImage,
    goodsType: 1,
    dealType: 1,
    quantity: t.qty,
    goodsAmount: t.goodsAmount,
    status: t.status,
    statusDesc: t.phaseDesc,
    createTime: t.createdAt ? new Date(t.createdAt).toLocaleString() : '—'
  }
}

/** 按角色与状态筛选加载订单（买家视角合并热卖票据，票据排前） */
async function loadOrders() {
  // 列表暂未接分页控件，固定取前 50 条
  const params = { page: 1, size: 50 }
  // inProgress 是后端聚合条件，覆盖所有未完结状态
  if (statusFilter.value === 'inProgress') {
    params.inProgress = true
  } else if (statusFilter.value) {
    params.status = statusFilter.value
  }
  if (roleFilter.value) params.role = roleFilter.value
  const res = await getMyOrders(params)
  let rows = res.data?.records || []

  // 热卖票据只在买家视角的「全部/进行中」下合并（票据是买家侧在途单）
  if (roleFilter.value === 'buyer' && !statusFilter.value) {
    try {
      const ticketRes = await getMyHotTickets()
      const tickets = (ticketRes.data || []).map(normalizeTicket)
      rows = [...tickets, ...rows]
    } catch { /* 票据接口失败不影响订单列表 */ }
  }
  orders.value = rows
}

/** 有 id 走订单详情；票据无 id，按 orderNo 跳票据页 */
function goDetail(row) {
  if (row.id) {
    router.push(`/orders/${row.id}`)
  } else {
    router.push(`/hot-ticket/${row.orderNo}`)
  }
}

onMounted(loadOrders)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: #1D2129;
  margin: 0;
}

.role-tabs {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
  border-bottom: 1px solid #F2F3F5;
  padding-bottom: 8px;
}

.role-tab {
  padding: 6px 2px;
  font-size: 15px;
  color: #86909C;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.25s;
}

.role-tab:hover { color: #4E5969; }

.role-tab.active {
  color: #1D2129;
  font-weight: 600;
  border-bottom-color: #5B8FF9;
}

.filter-group {
  margin-bottom: 16px;
}

.order-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: box-shadow 0.25s, transform 0.25s;
}

.order-card:hover {
  box-shadow: 0 6px 20px rgba(91, 143, 249, 0.1);
  transform: translateY(-2px);
}

.order-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #F2F3F5;
}

.order-no {
  font-size: 13px;
  color: #86909C;
}

.order-card-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.order-thumb {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  flex-shrink: 0;
}

.thumb-placeholder {
  width: 60px;
  height: 60px;
  background: #F2F3F5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #C9CDD4;
  border-radius: 8px;
  font-size: 12px;
}

.order-info {
  flex: 1;
  min-width: 0;
}

.order-goods-title {
  font-size: 15px;
  font-weight: 600;
  color: #1D2129;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.order-time {
  font-size: 12px;
  color: #C9CDD4;
}

.order-price {
  font-size: 20px;
  font-weight: 700;
  color: #F56C6C;
  flex-shrink: 0;
  margin-left: 16px;
}
</style>
