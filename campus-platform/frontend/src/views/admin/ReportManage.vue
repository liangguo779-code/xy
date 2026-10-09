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

      <!-- 举报列表：状态 0-待处理 1-已处理 2-已驳回，处理/驳回仅对待处理开放 -->
      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="举报目标" width="140">
          <template #default="{ row }">
            <el-tag :type="targetTypeTag(row.targetType)" size="small">{{ targetTypeText(row.targetType) }}</el-tag>
            <!-- 商品类目标给出跳转：原文只有标题，需打开商品详情页核对完整信息才能鉴别 -->
            <el-link v-if="row.targetType === 'goods'" :href="`/goods/${row.targetId}`" target="_blank"
                     type="primary" style="margin-left: 4px">#{{ row.targetId }}</el-link>
            <span v-else style="margin-left: 4px">#{{ row.targetId }}</span>
          </template>
        </el-table-column>
        <!-- 被举报原文：处理裁决的依据，按行回查目标内容 -->
        <el-table-column label="被举报内容" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="row.targetContent">
              <el-tag v-if="row.targetAuthorName" size="small" type="info" style="margin-right: 6px">{{ row.targetAuthorName }}</el-tag>
              <span>{{ row.targetContent }}</span>
            </template>
            <span v-else style="color: #c0c4cc">{{ row.targetLoaded ? '（目标已不存在）' : '加载中...' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reporterId" label="举报人" width="80" />
        <el-table-column prop="reason" label="举报原因" min-width="150" show-overflow-tooltip />
        <el-table-column label="补充说明" min-width="200">
          <template #default="{ row }">
            <span v-if="row.evidence">{{ row.evidence }}</span>
            <span v-else style="color: #c0c4cc">无</span>
          </template>
        </el-table-column>
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
      <!-- 裁决依据：被举报原文 -->
      <div v-if="handleForm.targetContent" style="background: #fdf6ec; border: 1px solid #faecd8; border-radius: 4px; padding: 10px; margin-bottom: 12px; max-height: 160px; overflow: auto; white-space: pre-wrap; font-size: 13px; line-height: 1.6">
        <div style="color: #e6a23c; font-weight: 600; margin-bottom: 4px">
          被举报内容<span v-if="handleForm.targetAuthorName">（{{ handleForm.targetAuthorName }}）</span>
          <el-link v-if="handleForm.targetType === 'goods'" :href="`/goods/${handleForm.targetId}`" target="_blank"
                   type="primary" style="margin-left: 8px; font-size: 12px">查看商品</el-link>
        </div>
        {{ handleForm.targetContent }}
      </div>
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
              <!-- 永久封禁用 36500 天表示（约 100 年），后端按天数计算解封时间 -->
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
/** 举报处理页：分页查询举报，处理时可联动下架商品、封禁用户，或驳回举报；一次处理最多串行调用三个接口 */
import { ref, onMounted } from 'vue'
import { getAdminReports, handleReport as handleReportApi, getReportTarget } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
/** 状态筛选：空串为全部，0-待处理 1-已处理 2-已驳回 */
const statusFilter = ref('')
// 搜索框仅触发重新加载，后端列表接口只收 status/page/size，keyword 未随请求下发
const keyword = ref('')
const showHandle = ref(false)
const submitting = ref(false)
/** 处理弹窗表单：记录本次处置选项，提交时按开关组合决定是否下架/封禁 */
const handleForm = ref({
  reportId: 0,
  targetType: '',
  targetId: 0,
  targetContent: '',
  targetAuthorId: null,
  targetAuthorName: '',
  result: '',
  offShelf: false,
  banUser: false,
  banType: 'all',
  banDays: 7
})

/** 举报目标类型：goods-商品 user-用户 message-消息 post-帖子 comment-评论，文案与 tag 配色按下标映射 */
const targetTypeText = (t) => ({ goods: '商品', user: '用户', message: '消息', post: '帖子', comment: '评论' }[t] || t)
const targetTypeTag = (t) => ({ goods: 'warning', user: 'danger', message: 'info', post: '', comment: 'success' }[t] || '')

/** 加载举报分页列表 */
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
  // 逐行回查被举报原文（裁决依据），并行请求不阻塞表格渲染
  list.value.forEach(async (row) => {
    try {
      const t = (await getReportTarget(row.id)).data
      row.targetContent = t?.content || ''
      row.targetAuthorId = t?.authorId ?? null
      row.targetAuthorName = t?.authorNickname || ''
    } catch { /* 单行失败显示占位 */ }
    finally { row.targetLoaded = true }
  })
}

/** 打开处理弹窗，商品类举报默认勾选下架 */
function handleApprove(row) {
  handleForm.value = {
    reportId: row.id,
    targetType: row.targetType,
    targetId: row.targetId,
    targetContent: row.targetContent || '',
    targetAuthorId: row.targetAuthorId ?? null,
    targetAuthorName: row.targetAuthorName || '',
    result: '',
    offShelf: row.targetType === 'goods',
    banUser: false,
    banType: 'all',
    banDays: 7
  }
  showHandle.value = true
}

/** 驳回举报：status 置 2，未填原因时用默认文案 */
async function handleReject(row) {
  const { value } = await ElMessageBox.prompt('驳回原因', '驳回举报', { inputType: 'textarea' })
  if (value === undefined) return
  await handleReportApi(row.id, { result: value || '举报不成立', status: 2 })
  ElMessage.success('已驳回')
  loadData()
}

/** 提交处理：先记处理结果，再按需下架商品/封禁用户，任一步失败即中断并提示 */
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
    // 封禁用户：统一取被举报目标的作者（评论/帖子/消息作者、商品卖家、被举报用户本身）
    if (handleForm.value.banUser) {
      const banUserId = handleForm.value.targetAuthorId
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
