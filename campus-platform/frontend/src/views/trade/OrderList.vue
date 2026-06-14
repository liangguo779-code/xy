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

    <div class="order-cards">
      <div v-for="order in orders" :key="order.id" class="order-card" @click="goDetail(order)">
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
              <el-tag :type="order.dealType === 0 ? 'info' : 'success'" size="small" effect="plain">
                {{ order.dealType === 0 ? '自提' : '配送' }}
              </el-tag>
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyOrders } from '@/api/order'

const router = useRouter()
const orders = ref([])
const statusFilter = ref('')
const roleFilter = ref('buyer')

const statusType = (s) => {
  if (s === 3) return 'success'
  if (s === 4) return 'danger'
  if (s === 0 || s === 5) return 'warning'
  if (s === 7 || s === 8 || s === 9) return 'primary'
  return 'info'
}

async function loadOrders() {
  const params = { page: 1, size: 50 }
  if (statusFilter.value === 'inProgress') {
    params.inProgress = true
  } else if (statusFilter.value) {
    params.status = statusFilter.value
  }
  if (roleFilter.value) params.role = roleFilter.value
  const res = await getMyOrders(params)
  orders.value = res.data?.records || []
}

function goDetail(row) {
  router.push(`/orders/${row.id}`)
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
