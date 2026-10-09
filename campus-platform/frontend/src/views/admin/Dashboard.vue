<template>
  <div class="dashboard">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近订单与待处理事项 -->
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
/**
 * 管理端数据看板：统计卡片 + 最近订单 + 待处理事项
 * 挂载时并发拉取用户/商品/订单/纠纷/举报统计，纠纷与举报只取 total 作为待办数
 */
import { ref, onMounted } from 'vue'
import { getUserStats, getGoodsStats, getOrderStats, getAdminDisputes, getAdminReports } from '@/api/admin'

/** 统计卡片定义，onMounted 按固定下标回填对应数值 */
const cards = ref([
  { label: '总用户', value: 0 },
  { label: '在售商品', value: 0 },
  { label: '已完成订单', value: 0 },
  { label: '待处理纠纷', value: 0 }
])

/** 最近订单暂无数据来源（后端未提供列表接口），保持为空数组 */
const recentOrders = ref([])
/** 待办项，count 取自纠纷/举报接口的 total 字段 */
const todos = ref([])

/** 并发拉取各项统计并回填卡片与待办 */
onMounted(async () => {
  // 纠纷/举报仅查 status 0-待处理，size 取 1，用返回的 total 得到待办数量
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
