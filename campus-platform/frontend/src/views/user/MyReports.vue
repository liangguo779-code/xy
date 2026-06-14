<template>
  <div class="my-reports">
    <el-page-header @back="router.back()">
      <template #content>我的举报</template>
    </el-page-header>

    <!-- 状态筛选 -->
    <el-radio-group v-model="statusFilter" @change="loadData" style="margin: 16px 0">
      <el-radio-button label="">全部</el-radio-button>
      <el-radio-button label="0">待处理</el-radio-button>
      <el-radio-button label="1">已处理</el-radio-button>
      <el-radio-button label="2">已驳回</el-radio-button>
    </el-radio-group>

    <div v-loading="loading">
      <div v-for="item in list" :key="item.id" class="report-card">
        <div class="report-header">
          <span class="report-target">
            <el-tag size="small" :type="targetTypeTag(item.targetType)">
              {{ targetTypeText(item.targetType) }}
            </el-tag>
            #{{ item.targetId }}
          </span>
          <el-tag :type="statusType(item.status)" size="small">{{ statusText(item.status) }}</el-tag>
        </div>
        <div class="report-reason">举报原因：{{ item.reason }}</div>
        <div v-if="item.result" class="report-result">
          <el-icon><CircleCheck /></el-icon> 处理结果：{{ item.result }}
        </div>
        <div class="report-time">{{ item.createTime }}</div>
      </div>

      <el-empty v-if="!loading && list.length === 0" description="暂无举报记录" />

      <div v-if="total > pageSize" class="pagination">
        <el-pagination background layout="prev, pager, next"
                       :current-page="page" :page-size="pageSize" :total="total"
                       @current-change="p => { page = p; loadData() }" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyReports } from '@/api/report'
import { CircleCheck } from '@element-plus/icons-vue'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = 20
const total = ref(0)
const statusFilter = ref('')

const statusText = (s) => ({ 0: '待处理', 1: '已处理', 2: '已驳回' }[s] || '未知')
const statusType = (s) => ({ 0: 'warning', 1: 'success', 2: 'danger' }[s] || 'info')
const targetTypeText = (t) => ({ goods: '商品', user: '用户', message: '消息', post: '帖子', comment: '评论' }[t] || t)
const targetTypeTag = (t) => ({ goods: 'warning', user: 'danger', message: 'info', post: '', comment: 'success' }[t] || '')

async function loadData() {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize }
    if (statusFilter.value !== '') params.status = statusFilter.value
    const res = await getMyReports(params)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}

onMounted(loadData)
</script>

<style scoped>
.my-reports { padding-bottom: 20px; }

.report-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}

.report-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.report-target {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #1D2129;
}

.report-reason {
  font-size: 13px;
  color: #4E5969;
  margin-bottom: 6px;
}

.report-result {
  font-size: 13px;
  color: #52C41A;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
}

.report-time {
  font-size: 12px;
  color: #C9CDD4;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
