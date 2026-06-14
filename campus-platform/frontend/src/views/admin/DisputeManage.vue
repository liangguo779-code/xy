<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>纠纷处理</span>
          <el-input v-model="keyword" placeholder="搜索..." style="width: 200px"
                    @keyup.enter="loadData" clearable @clear="loadData" />
        </div>
      </template>

      <!-- 状态筛选 -->
      <el-radio-group v-model="statusFilter" @change="loadData" style="margin-bottom: 12px">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="0">待处理</el-radio-button>
        <el-radio-button label="1">处理中</el-radio-button>
        <el-radio-button label="2">已解决</el-radio-button>
        <el-radio-button label="3">已驳回</el-radio-button>
      </el-radio-group>

      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderId" label="订单ID" width="90">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="router.push(`/orders/${row.orderId}`)">
              #{{ row.orderId }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="reporterId" label="申请人" width="80" />
        <el-table-column prop="reason" label="纠纷原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="证据" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.evidenceImages" type="info" size="small">有图片</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="['warning','','success','danger'][row.status]" size="small">
              {{ ['待处理','处理中','已解决','已驳回'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="result" label="处理结果" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="申请时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" text type="primary" size="small"
                       @click="handleStatus(row.id, 1)">受理</el-button>
            <el-button v-if="row.status === 0 || row.status === 1" text type="success" size="small"
                       @click="handleResolve(row)">解决</el-button>
            <el-button v-if="row.status === 0 || row.status === 1" text type="danger" size="small"
                       @click="handleReject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top: 16px; justify-content: center" background
                     layout="prev, pager, next" :current-page="page" :page-size="20"
                     :total="total" @current-change="p => { page = p; loadData() }" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAdminDisputes, resolveDispute } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const statusFilter = ref('')
const keyword = ref('')

async function loadData() {
  loading.value = true
  try {
    const params = { page: page.value, size: 20 }
    if (statusFilter.value !== '') params.status = statusFilter.value
    const res = await getAdminDisputes(params)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function handleStatus(id, status) {
  await resolveDispute(id, { result: '已受理', status })
  ElMessage.success('已受理')
  loadData()
}

async function handleResolve(row) {
  const { value } = await ElMessageBox.prompt('请输入解决方案', '解决纠纷', { inputType: 'textarea' })
  if (value === undefined) return
  await resolveDispute(row.id, { result: value || '纠纷已解决', status: 2 })
  ElMessage.success('已解决')
  loadData()
}

async function handleReject(row) {
  const { value } = await ElMessageBox.prompt('驳回原因', '驳回纠纷', { inputType: 'textarea' })
  if (value === undefined) return
  await resolveDispute(row.id, { result: value || '纠纷不成立', status: 3 })
  ElMessage.success('已驳回')
  loadData()
}

onMounted(loadData)
</script>
