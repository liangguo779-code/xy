<template>
  <div>
    <el-card>
      <template #header>订单监控</template>
      <el-table :data="list" stripe>
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="goodsAmount" label="金额" width="100">
          <template #default="{ row }">¥{{ row.goodsAmount }}</template>
        </el-table-column>
        <el-table-column label="方式" width="80">
          <template #default="{ row }">
            <el-tag :type="row.dealType === 0 ? '' : 'success'" size="small">
              {{ row.dealType === 0 ? '自提' : '配送' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
      </el-table>
      <el-pagination style="margin-top: 16px" :current-page="page" :page-size="20"
                     :total="total" @current-change="p => { page = p; loadData() }" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAdminOrders } from '@/api/admin'

const list = ref([])
const page = ref(1)
const total = ref(0)

const statusText = (s) => ({
  0: '待确认', 1: '待核销', 3: '已完成', 4: '已取消',
  5: '待付服务费', 6: '待派单', 7: '待取货', 8: '配送中', 9: '待确认收货'
}[s] || '未知')

async function loadData() {
  const res = await getAdminOrders({ page: page.value, size: 20 })
  list.value = res.data?.records || []
  total.value = res.data?.total || 0
}

onMounted(loadData)
</script>
