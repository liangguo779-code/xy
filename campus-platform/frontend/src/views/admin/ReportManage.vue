<template>
  <div>
    <el-card>
      <template #header>举报管理</template>
      <el-table :data="list" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="targetType" label="目标类型" width="100" />
        <el-table-column prop="targetId" label="目标ID" width="80" />
        <el-table-column prop="reason" label="举报原因" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="['warning','success','danger'][row.status]" size="small">
              {{ ['待处理','已处理','已驳回'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" text type="success" size="small"
                       @click="handleReport(row.id, 1)">处理</el-button>
            <el-button v-if="row.status === 0" text type="danger" size="small"
                       @click="handleReport(row.id, 2)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAdminReports, handleReport as handleReportApi } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])

async function loadData() {
  const res = await getAdminReports({ page: 1, size: 50 })
  list.value = res.data?.records || []
}

async function handleReport(id, status) {
  const { value } = await ElMessageBox.prompt('处理结果', '处理举报', { inputType: 'textarea' })
  await handleReportApi(id, { result: value, status })
  ElMessage.success('已处理')
  loadData()
}

onMounted(loadData)
</script>
