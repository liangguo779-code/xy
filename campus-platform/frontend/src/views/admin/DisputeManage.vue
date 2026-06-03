<template>
  <div>
    <el-card>
      <template #header>纠纷处理</template>
      <el-table :data="list" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderId" label="订单ID" width="100" />
        <el-table-column prop="reason" label="原因" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="['warning','','success','danger'][row.status]" size="small">
              {{ ['待处理','处理中','已解决','已驳回'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="160" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" text type="success" size="small"
                       @click="handleResolve(row.id, 2)">解决</el-button>
            <el-button v-if="row.status === 0" text type="danger" size="small"
                       @click="handleResolve(row.id, 3)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAdminDisputes, resolveDispute } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])

async function loadData() {
  const res = await getAdminDisputes({ page: 1, size: 50 })
  list.value = res.data?.records || []
}

async function handleResolve(id, status) {
  const { value } = await ElMessageBox.prompt('请输入处理结果', '处理纠纷', { inputType: 'textarea' })
  await resolveDispute(id, { result: value, status })
  ElMessage.success('已处理')
  loadData()
}

onMounted(loadData)
</script>
