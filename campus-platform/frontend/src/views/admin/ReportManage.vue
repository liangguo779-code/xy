<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>举报管理</span>
          <el-input v-model="keyword" placeholder="搜索..." style="width: 200px"
                    @keyup.enter="loadData" clearable @clear="loadData" />
        </div>
      </template>

      <!-- 状态筛选 -->
      <el-radio-group v-model="statusFilter" @change="loadData" style="margin-bottom: 12px">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="0">待处理</el-radio-button>
        <el-radio-button label="1">已处理</el-radio-button>
        <el-radio-button label="2">已驳回</el-radio-button>
      </el-radio-group>

      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="举报目标" width="140">
          <template #default="{ row }">
            <el-tag :type="targetTypeTag(row.targetType)" size="small">{{ targetTypeText(row.targetType) }}</el-tag>
            <span style="margin-left: 4px">#{{ row.targetId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reporterId" label="举报人" width="80" />
        <el-table-column prop="reason" label="举报原因" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="['warning','success','danger'][row.status]" size="small">
              {{ ['待处理','已处理','已驳回'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="result" label="处理结果" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="举报时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" text type="success" size="small"
                       @click="handleApprove(row)">处理</el-button>
            <el-button v-if="row.status === 0" text type="danger" size="small"
                       @click="handleReject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top: 16px; justify-content: center" background
                     layout="prev, pager, next" :current-page="page" :page-size="20"
                     :total="total" @current-change="p => { page = p; loadData() }" />
    </el-card>

    <!-- 处理弹窗 -->
    <el-dialog v-model="showHandle" title="处理举报" width="460">
      <el-form label-width="90px">
        <el-form-item label="处理结果">
          <el-input v-model="handleForm.result" type="textarea" :rows="3" placeholder="请输入处理说明..." />
        </el-form-item>
        <el-form-item v-if="handleForm.targetType === 'goods'" label="下架商品">
          <el-switch v-model="handleForm.offShelf" />
        </el-form-item>
        <el-form-item label="封禁用户">
          <el-switch v-model="handleForm.banUser" />
        </el-form-item>
        <template v-if="handleForm.banUser">
          <el-form-item label="封禁类型">
            <el-select v-model="handleForm.banType" style="width: 100%">
              <el-option label="全部" value="all" />
              <el-option label="交易" value="trade" />
              <el-option label="消息" value="message" />
              <el-option label="论坛" value="forum" />
            </el-select>
          </el-form-item>
          <el-form-item label="封禁天数">
            <el-select v-model="handleForm.banDays" style="width: 100%">
              <el-option :label="1" :value="1" />
              <el-option :label="3" :value="3" />
              <el-option :label="7" :value="7" />
              <el-option :label="15" :value="15" />
              <el-option :label="30" :value="30" />
              <el-option label="永久" :value="36500" />
            </el-select>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="showHandle = false">取消</el-button>
        <el-button type="success" @click="submitHandle(1)" :loading="submitting">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAdminReports, handleReport as handleReportApi } from '@/api/admin'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const statusFilter = ref('')
const keyword = ref('')
const showHandle = ref(false)
const submitting = ref(false)
const handleForm = ref({
  reportId: 0,
  targetType: '',
  targetId: 0,
  result: '',
  offShelf: false,
  banUser: false,
  banType: 'all',
  banDays: 7
})

const targetTypeText = (t) => ({ goods: '商品', user: '用户', message: '消息', post: '帖子', comment: '评论' }[t] || t)
const targetTypeTag = (t) => ({ goods: 'warning', user: 'danger', message: 'info', post: '', comment: 'success' }[t] || '')

async function loadData() {
  loading.value = true
  try {
    const params = { page: page.value, size: 20 }
    if (statusFilter.value !== '') params.status = statusFilter.value
    const res = await getAdminReports(params)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function handleApprove(row) {
  handleForm.value = {
    reportId: row.id,
    targetType: row.targetType,
    targetId: row.targetId,
    result: '',
    offShelf: row.targetType === 'goods',
    banUser: false,
    banType: 'all',
    banDays: 7
  }
  showHandle.value = true
}

async function handleReject(row) {
  const { value } = await ElMessageBox.prompt('驳回原因', '驳回举报', { inputType: 'textarea' })
  if (value === undefined) return
  await handleReportApi(row.id, { result: value || '举报不成立', status: 2 })
  ElMessage.success('已驳回')
  loadData()
}

async function submitHandle(status) {
  submitting.value = true
  try {
    // 处理举报
    await handleReportApi(handleForm.value.reportId, {
      result: handleForm.value.result || '举报属实',
      status
    })
    // 自动下架商品
    if (handleForm.value.offShelf && handleForm.value.targetType === 'goods') {
      await request.delete(`/api/goods/${handleForm.value.targetId}`, { params: { reason: '因举报被下架' } })
    }
    // 封禁用户
    if (handleForm.value.banUser) {
      let banUserId = null
      if (handleForm.value.targetType === 'user') {
        banUserId = handleForm.value.targetId
      } else if (handleForm.value.targetType === 'goods') {
        // 获取商品卖家ID
        try {
          const goodsRes = await request.get(`/api/goods/${handleForm.value.targetId}`)
          banUserId = goodsRes.data?.userId
        } catch { /* ignore */ }
      }
      if (banUserId) {
        await request.post('/api/admin/bans/user', {
          userId: banUserId,
          banType: handleForm.value.banType,
          reason: handleForm.value.result || '因举报被封禁',
          days: handleForm.value.banDays
        })
      }
    }
    ElMessage.success('已处理')
    showHandle.value = false
    loadData()
  } catch (e) {
    ElMessage.error('处理失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>
