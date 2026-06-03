<template>
  <div class="order-list">
    <div class="page-header">
      <h2>我的订单</h2>
      <el-radio-group v-model="statusFilter" @change="loadOrders">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="0">待确认</el-radio-button>
        <el-radio-button label="5">待付服务费</el-radio-button>
        <el-radio-button label="3">已完成</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="orders" stripe @row-click="goDetail">
      <el-table-column prop="orderNo" label="订单号" width="180" />
      <el-table-column prop="goodsTitle" label="商品" />
      <el-table-column label="交易方式" width="100">
        <template #default="{ row }">
          <el-tag :type="row.dealType === 0 ? '' : 'success'" size="small">
            {{ row.dealType === 0 ? '自提' : '配送' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="goodsAmount" label="成交价" width="100">
        <template #default="{ row }">¥{{ row.goodsAmount }}</template>
      </el-table-column>
      <el-table-column prop="statusDesc" label="状态" width="140">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ row.statusDesc }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" />
    </el-table>

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

const statusType = (s) => {
  if (s === 3) return 'success'
  if (s === 4) return 'danger'
  if (s === 0 || s === 5) return 'warning'
  return ''
}

async function loadOrders() {
  const res = await getMyOrders(statusFilter.value || undefined)
  orders.value = res.data || []
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
  margin-bottom: 20px;
}
</style>
