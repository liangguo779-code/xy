<template>
  <div class="my-disputes">
    <el-page-header @back="router.back()">
      <template #content>我的纠纷</template>
    </el-page-header>

    <!-- 状态筛选 -->
    <el-radio-group v-model="statusFilter" @change="loadData" style="margin: 16px 0">
      <el-radio-button label="">全部</el-radio-button>
      <el-radio-button label="0">待处理</el-radio-button>
      <el-radio-button label="1">处理中</el-radio-button>
      <el-radio-button label="2">已解决</el-radio-button>
      <el-radio-button label="3">已驳回</el-radio-button>
    </el-radio-group>

    <div v-loading="loading">
      <div v-for="item in list" :key="item.id" class="dispute-card">
        <div class="dispute-header">
          <span class="dispute-order">订单 #{{ item.orderId }}</span>
          <el-tag :type="statusType(item.status)" size="small">{{ statusText(item.status) }}</el-tag>
        </div>
        <div class="dispute-reason">纠纷原因：{{ item.reason }}</div>
        <div v-if="item.evidenceImages" class="dispute-images">
          <el-image v-for="(img, i) in parseImages(item.evidenceImages)" :key="i" :src="img"
                    fit="cover" style="width: 60px; height: 60px; border-radius: 6px; margin-right: 6px"
                    :preview-src-list="parseImages(item.evidenceImages)" />
        </div>
        <div v-if="item.result" class="dispute-result">
          <el-icon><CircleCheck /></el-icon> 处理结果：{{ item.result }}
        </div>
        <div class="dispute-footer">
          <span class="dispute-time">{{ item.createTime }}</span>
          <el-button v-if="item.orderId" text type="primary" size="small"
                     @click="router.push(`/orders/${item.orderId}`)">查看订单</el-button>
        </div>
      </div>

      <el-empty v-if="!loading && list.length === 0" description="暂无纠纷记录" />

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
import { getMyDisputes } from '@/api/dispute'
import { CircleCheck } from '@element-plus/icons-vue'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = 20
const total = ref(0)
const statusFilter = ref('')

const statusText = (s) => ({ 0: '待处理', 1: '处理中', 2: '已解决', 3: '已驳回' }[s] || '未知')
const statusType = (s) => ({ 0: 'warning', 1: '', 2: 'success', 3: 'danger' }[s] || 'info')

function parseImages(images) {
  if (!images) return []
  try { return typeof images === 'string' ? JSON.parse(images) : images }
  catch { return [] }
}

async function loadData() {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize }
    if (statusFilter.value !== '') params.status = statusFilter.value
    const res = await getMyDisputes(params)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}

onMounted(loadData)
</script>

<style scoped>
.my-disputes { padding-bottom: 20px; }

.dispute-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}

.dispute-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.dispute-order {
  font-size: 14px;
  font-weight: 500;
  color: #1D2129;
}

.dispute-reason {
  font-size: 13px;
  color: #4E5969;
  margin-bottom: 8px;
}

.dispute-images {
  margin-bottom: 8px;
}

.dispute-result {
  font-size: 13px;
  color: #52C41A;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
}

.dispute-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dispute-time {
  font-size: 12px;
  color: #C9CDD4;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
