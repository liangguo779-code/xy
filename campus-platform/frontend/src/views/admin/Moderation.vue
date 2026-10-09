<template>
  <div>
    <!-- KPI 统计卡片 -->
    <el-row :gutter="12" style="margin-bottom: 16px">
      <el-col :span="4"><el-card shadow="hover"><el-statistic title="识别准确率" :value="stats.accuracy * 100" :precision="1" suffix="%" /></el-card></el-col>
      <el-col :span="4"><el-card shadow="hover"><el-statistic title="人审减负率" :value="stats.autoResolveRate * 100" :precision="1" suffix="%" /></el-card></el-col>
      <el-col :span="4"><el-card shadow="hover"><el-statistic title="机审自动结案" :value="stats.machineAutoClosed" /></el-card></el-col>
      <el-col :span="4"><el-card shadow="hover"><el-statistic title="转人审" :value="stats.machineToHuman" /></el-card></el-col>
      <el-col :span="4"><el-card shadow="hover"><el-statistic title="待人审队列" :value="stats.pendingReview" /></el-card></el-col>
      <el-col :span="4"><el-card shadow="hover"><el-statistic title="发布拦截" :value="stats.publishBlocked" /></el-card></el-col>
    </el-row>

    <el-tabs v-model="tab">
      <!-- ============ 复核队列 ============ -->
      <el-tab-pane label="复核队列" name="queue">
        <el-card>
          <el-radio-group v-model="statusFilter" @change="loadQueue" style="margin-bottom: 12px">
            <el-radio-button :label="3">待人审</el-radio-button>
            <el-radio-button :label="2">机审拦截</el-radio-button>
            <el-radio-button :label="4">人审违规</el-radio-button>
            <el-radio-button :label="1">正常</el-radio-button>
            <el-radio-button :label="0">已删除</el-radio-button>
          </el-radio-group>

          <el-table :data="queue" stripe v-loading="loading">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="nickname" label="作者" width="110" show-overflow-tooltip />
            <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="发布时间" width="160" />
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openDetail(row)">详情</el-button>
                <el-button v-if="row.status !== 1" text type="success" size="small"
                           @click="quickFix(row, 1)">恢复</el-button>
                <el-button v-if="row.status !== 2 && row.status !== 4" text type="danger" size="small"
                           @click="quickFix(row, 2)">拦截</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination style="margin-top: 16px; justify-content: center" background
                         layout="prev, pager, next, total" :current-page="page" :page-size="20"
                         :total="total" @current-change="p => { page = p; loadQueue() }" />
        </el-card>
      </el-tab-pane>

      <!-- ============ 敏感词库 ============ -->
      <el-tab-pane label="敏感词库" name="words">
        <el-card>
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span>敏感词库（本地关键词过滤，发布时实时匹配）</span>
              <el-button type="primary" @click="openWordForm()">新增敏感词</el-button>
            </div>
          </template>

          <el-table :data="words" stripe v-loading="wordsLoading">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="word" label="敏感词" width="180" />
            <el-table-column prop="category" label="类目" width="110" />
            <el-table-column label="处置级别" width="120">
              <template #default="{ row }">
                <el-tag :type="['danger','warning','info'][row.level - 1]" size="small">
                  {{ ['拒绝发布', '替换***', '送审'][row.level - 1] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
                  {{ row.enabled === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="160" />
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openWordForm(row)">编辑</el-button>
                <el-button text :type="row.enabled === 1 ? 'warning' : 'success'" size="small"
                           @click="toggleWord(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
                <el-button text type="danger" size="small" @click="removeWord(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 审核详情抽屉 -->
    <el-drawer v-model="detailVisible" title="内容审核详情" size="520px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="评论 ID">{{ detail.comment.id }}</el-descriptions-item>
          <el-descriptions-item label="作者">{{ detail.authorNickname }}</el-descriptions-item>
          <el-descriptions-item label="内容">{{ detail.comment.content }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="statusTag(detail.comment.status)" size="small">{{ statusText(detail.comment.status) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 人工修正 -->
        <el-card shadow="never" style="margin-bottom: 16px">
          <div style="font-weight: 600; margin-bottom: 8px">人工修正状态</div>
          <el-select v-model="fixForm.status" style="width: 100%; margin-bottom: 8px">
            <el-option v-for="(text, val) in statusOptions" :key="val" :label="text" :value="Number(val)" />
          </el-select>
          <el-input v-model="fixForm.note" type="textarea" :rows="2" placeholder="修正理由（写入审计流水）" style="margin-bottom: 8px" />
          <el-button type="primary" @click="submitFix">提交修正</el-button>
        </el-card>

        <!-- AI / 人工审计流水 -->
        <div style="font-weight: 600; margin-bottom: 8px">审计流水</div>
        <el-timeline style="margin-bottom: 16px">
          <el-timeline-item v-for="log in detail.auditLogs" :key="log.id"
                            :timestamp="log.createTime" placement="top">
            <div>
              <el-tag size="small" :type="triggerTag(log.triggerType)">
                {{ triggerText(log.triggerType) }}
              </el-tag>
              <el-tag v-if="log.aiVerdict != null" size="small" style="margin-left: 6px"
                      :type="['success','danger','warning'][log.aiVerdict]">
                AI：{{ ['合规','违规','不确定'][log.aiVerdict] }}
                <template v-if="log.aiConfidence != null"> ({{ (log.aiConfidence * 100).toFixed(0) }}%)</template>
              </el-tag>
              <el-tag size="small" type="info" style="margin-left: 6px">
                → {{ statusText(log.finalStatus) }}
              </el-tag>
            </div>
            <div v-if="log.hitWords" style="color: #e6a23c; font-size: 12px; margin-top: 4px">
              命中词：{{ log.hitWords }}
            </div>
            <div v-if="log.aiReason" style="color: #909399; font-size: 12px; margin-top: 4px">
              理由：{{ log.aiReason }}
            </div>
            <div v-if="log.reviewNote" style="color: #409eff; font-size: 12px; margin-top: 4px">
              备注：{{ log.reviewNote }}
            </div>
          </el-timeline-item>
        </el-timeline>

        <!-- 相关举报 -->
        <div style="font-weight: 600; margin-bottom: 8px">相关举报（{{ detail.reports.length }}）</div>
        <el-table :data="detail.reports" size="small" stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="reason" label="举报理由" min-width="140" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="['warning','success','info'][row.status]" size="small">
                {{ ['待处理','已处理','已驳回'][row.status] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="result" label="处理结果" min-width="120" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>

    <!-- 敏感词编辑对话框 -->
    <el-dialog v-model="wordFormVisible" :title="wordForm.id ? '编辑敏感词' : '新增敏感词'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="敏感词">
          <el-input v-model="wordForm.word" placeholder="如：代考、办证" />
        </el-form-item>
        <el-form-item label="类目">
          <el-select v-model="wordForm.category" style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置级别">
          <el-select v-model="wordForm.level" style="width: 100%">
            <el-option label="1-拒绝发布" :value="1" />
            <el-option label="2-替换***" :value="2" />
            <el-option label="3-送审（发布但进人审队列）" :value="3" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="wordFormVisible = false">取消</el-button>
        <el-button type="primary" @click="saveWord">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAuditQueue, getAuditDetail, fixCommentStatus, getAuditStats,
  getSensitiveWords, addSensitiveWord, updateSensitiveWord, deleteSensitiveWord
} from '@/api/admin'

/** 内容安全治理页：机器机审结果复核 + 自定义敏感词库管理 + KPI 看板 */
const tab = ref('queue')

// ---------- KPI ----------
const stats = reactive({
  accuracy: 1, autoResolveRate: 0, machineAutoClosed: 0, machineToHuman: 0,
  pendingReview: 0, publishBlocked: 0
})
async function loadStats() {
  try {
    // 拦截器返回 {code,msg,data} 包体，业务数据在 res.data
    Object.assign(stats, (await getAuditStats()).data || {})
  } catch { /* 首屏失败不阻塞 */ }
}

// ---------- 复核队列 ----------
const queue = ref([])
const loading = ref(false)
const statusFilter = ref(3)
const page = ref(1)
const total = ref(0)

const statusOptions = { 0: '0-已删除', 1: '1-正常', 2: '2-机审拦截', 3: '3-待人审', 4: '4-人审违规' }
const statusText = s => statusOptions[s] ?? String(s)
const statusTag = s => ['info', 'success', 'danger', 'warning', 'danger'][s] ?? 'info'

async function loadQueue() {
  loading.value = true
  try {
    const res = await getAuditQueue({ status: statusFilter.value, page: page.value, size: 20 })
    queue.value = res.data?.rows || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

// ---------- 审核详情 ----------
const detailVisible = ref(false)
const detail = ref(null)
const fixForm = reactive({ status: 3, note: '' })

async function openDetail(row) {
  detail.value = (await getAuditDetail(row.id)).data
  fixForm.status = row.status
  fixForm.note = ''
  detailVisible.value = true
}

async function submitFix() {
  await fixCommentStatus(detail.value.comment.id, { status: fixForm.status, note: fixForm.note })
  ElMessage.success('已修正状态并写入审计流水')
  detailVisible.value = false
  loadQueue()
  loadStats()
}

async function quickFix(row, status) {
  const { value } = await ElMessageBox.prompt(
    `确认将评论 #${row.id} 状态修正为「${statusText(status)}」？请输入理由：`,
    '人工修正', { inputPlaceholder: '修正理由（写入审计流水）', type: 'warning' }
  ).catch(() => ({ value: null }))
  if (value === null) return
  await fixCommentStatus(row.id, { status, note: value })
  ElMessage.success('已修正')
  loadQueue()
  loadStats()
}

const triggerText = t => ({ 1: '发布词库', 2: '举报机审', 3: '人审', 4: '巡查', 5: '申诉' }[t] ?? t)
const triggerTag = t => ({ 1: 'warning', 2: 'primary', 3: 'success', 4: 'info', 5: 'info' }[t] ?? 'info')

// ---------- 敏感词库 ----------
const words = ref([])
const wordsLoading = ref(false)
const categories = ['政治', '辱骂', '色情', '广告', '谣言', '隐私', '其他']
const wordFormVisible = ref(false)
const wordForm = reactive({ id: null, word: '', category: '广告', level: 1 })

async function loadWords() {
  wordsLoading.value = true
  try {
    words.value = (await getSensitiveWords()).data || []
  } finally {
    wordsLoading.value = false
  }
}

function openWordForm(row) {
  if (row) {
    Object.assign(wordForm, { id: row.id, word: row.word, category: row.category, level: row.level })
  } else {
    Object.assign(wordForm, { id: null, word: '', category: '广告', level: 1 })
  }
  wordFormVisible.value = true
}

async function saveWord() {
  const payload = { word: wordForm.word, category: wordForm.category, level: wordForm.level }
  if (wordForm.id) {
    await updateSensitiveWord(wordForm.id, payload)
  } else {
    await addSensitiveWord(payload)
  }
  ElMessage.success('已保存，下一条发布即生效')
  wordFormVisible.value = false
  loadWords()
}

async function toggleWord(row) {
  await updateSensitiveWord(row.id, { word: row.word, category: row.category, level: row.level, enabled: row.enabled === 1 ? 0 : 1 })
  loadWords()
}

async function removeWord(row) {
  await ElMessageBox.confirm(`确认删除敏感词「${row.word}」？`, '删除', { type: 'warning' })
  await deleteSensitiveWord(row.id)
  ElMessage.success('已删除')
  loadWords()
}

onMounted(() => {
  loadStats()
  loadQueue()
  loadWords()
})
</script>
