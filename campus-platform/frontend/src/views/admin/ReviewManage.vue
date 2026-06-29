<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>评价管理</span>
          <div style="display: flex; gap: 12px; align-items: center">
            <el-tag v-if="stats" type="info" size="small">共 {{ stats.total }} 条评价</el-tag>
            <el-tag v-if="stats?.appealed" type="warning" size="small">{{ stats.appealed }} 条待处理申诉</el-tag>
            <el-tag v-if="stats?.hidden" type="danger" size="small">{{ stats.hidden }} 条已屏蔽</el-tag>
          </div>
        </div>
      </template>

      <!-- 筛选 -->
      <el-radio-group v-model="appealFilter" @change="loadData" style="margin-bottom: 12px">
        <el-radio-button label="">全部申诉</el-radio-button>
        <el-radio-button label="1">待处理</el-radio-button>
        <el-radio-button label="2">已通过</el-radio-button>
        <el-radio-button label="3">已驳回</el-radio-button>
      </el-radio-group>

      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderId" label="订单" width="80">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="router.push(`/orders/${row.orderId}`)">
              #{{ row.orderId }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="reviewerId" label="评价者" width="80" />
        <el-table-column prop="targetId" label="被评价者" width="80" />
        <el-table-column prop="rating" label="评分" width="100">
          <template #default="{ row }">
            <el-rate :model-value="row.rating" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="content" label="评价内容" min-width="150" show-overflow-tooltip />
        <el-table-column prop="appealReason" label="申诉理由" min-width="150" show-overflow-tooltip />
        <el-table-column prop="appealStatus" label="申诉状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.appealStatus === 0" type="info" size="small">无申诉</el-tag>
            <el-tag v-if="row.appealStatus === 1" type="warning" size="small">申诉中</el-tag>
            <el-tag v-if="row.appealStatus === 2" type="success" size="small">已通过</el-tag>
            <el-tag v-if="row.appealStatus === 3" type="danger" size="small">已驳回</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="评价状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '已屏蔽' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="评价时间" width="160" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <template v-if="row.appealStatus === 1">
              <el-button text type="success" size="small" @click="handleApprove(row)">通过</el-button>
              <el-button text type="danger" size="small" @click="handleReject(row)">驳回</el-button>
            </template>
            <template v-if="row.status === 1">
              <el-button text type="warning" size="small" @click="handleHide(row)">屏蔽</el-button>
            </template>
            <template v-if="row.status === 0">
              <el-button text type="primary" size="small" @click="handleRestore(row)">恢复</el-button>
            </template>
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
import { getReviewAppeals, handleReviewAppeal, updateReviewStatus, getReviewStats } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const appealFilter = ref('')
const stats = ref(null)

async function loadData() {
  loading.value = true
  try {
    const params = { page: page.value, size: 20 }
    if (appealFilter.value !== '') params.appealStatus = appealFilter.value
    const res = await getReviewAppeals(params)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function loadStats() {
  try {
    const res = await getReviewStats()
    stats.value = res.data
  } catch { /* ignore */ }
}

async function handleApprove(row) {
  await ElMessageBox.confirm('确认通过该申诉？评价将被屏蔽', '处理申诉')
  await handleReviewAppeal(row.id, { approved: true, result: '申诉通过，评价已屏蔽' })
  ElMessage.success('申诉已通过')
  loadData()
  loadStats()
}

async function handleReject(row) {
  const { value } = await ElMessageBox.prompt('驳回原因', '驳回申诉', { inputType: 'textarea' })
  if (value === undefined) return
  await handleReviewAppeal(row.id, { approved: false, result: value || '申诉不成立' })
  ElMessage.success('申诉已驳回')
  loadData()
}

async function handleHide(row) {
  await ElMessageBox.confirm('确认屏蔽该评价？', '屏蔽评价')
  await updateReviewStatus(row.id, { status: 0 })
  ElMessage.success('评价已屏蔽')
  loadData()
  loadStats()
}

async function handleRestore(row) {
  await updateReviewStatus(row.id, { status: 1 })
  ElMessage.success('评价已恢复')
  loadData()
  loadStats()
}

onMounted(() => {
  loadData()
  loadStats()
})
</script>
