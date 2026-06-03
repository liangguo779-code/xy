<template>
  <div class="dashboard">
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card>
          <template #header>最近订单</template>
          <el-table :data="recentOrders" size="small">
            <el-table-column prop="orderNo" label="订单号" />
            <el-table-column prop="goodsAmount" label="金额" />
            <el-table-column prop="status" label="状态" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>待处理事项</template>
          <div class="todo-item" v-for="item in todos" :key="item.label">
            <span>{{ item.label }}</span>
            <el-tag :type="item.count > 0 ? 'danger' : 'success'" size="small">
              {{ item.count }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getUserStats, getGoodsStats, getOrderStats, getAdminDisputes, getAdminReports } from '@/api/admin'

const cards = ref([
  { label: '总用户', value: 0 },
  { label: '在售商品', value: 0 },
  { label: '已完成订单', value: 0 },
  { label: '待处理纠纷', value: 0 }
])

const recentOrders = ref([])
const todos = ref([])

onMounted(async () => {
  const [userRes, goodsRes, orderRes, disputeRes, reportRes] = await Promise.all([
    getUserStats(), getGoodsStats(), getOrderStats(),
    getAdminDisputes({ status: 0, page: 1, size: 1 }),
    getAdminReports({ status: 0, page: 1, size: 1 })
  ])

  cards.value[0].value = userRes.data?.total || 0
  cards.value[1].value = goodsRes.data?.onSale || 0
  cards.value[2].value = orderRes.data?.completed || 0
  cards.value[3].value = disputeRes.data?.total || 0

  todos.value = [
    { label: '待处理纠纷', count: disputeRes.data?.total || 0 },
    { label: '待处理举报', count: reportRes.data?.total || 0 }
  ]
})
</script>

<style scoped>
.stat-card { text-align: center; }
.stat-value { font-size: 28px; font-weight: 700; color: #303133; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.todo-item { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
</style>
