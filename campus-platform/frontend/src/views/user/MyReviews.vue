<template>
  <div class="my-reviews">
    <el-page-header @back="router.back()">
      <template #content>我的评价</template>
    </el-page-header>

    <el-tabs v-model="activeTab" @tab-change="switchTab" style="margin-top: 16px">
      <el-tab-pane label="收到的评价" name="received" />
      <el-tab-pane label="发出的评价" name="given" />
    </el-tabs>

    <div v-loading="loading">
      <div v-for="item in list" :key="item.id" class="review-card">
        <!-- 评价头部 -->
        <div class="review-header">
          <div class="review-meta">
            <el-rate :model-value="item.rating" disabled size="small" />
            <span class="review-time">{{ item.createTime }}</span>
          </div>
          <el-tag v-if="item.status === 0" type="danger" size="small">已屏蔽</el-tag>
          <el-tag v-if="item.appealStatus === 1" type="warning" size="small">申诉中</el-tag>
          <el-tag v-if="item.appealStatus === 2" type="success" size="small">申诉通过</el-tag>
          <el-tag v-if="item.appealStatus === 3" type="info" size="small">申诉驳回</el-tag>
        </div>

        <!-- 评价内容 -->
        <div class="review-content">{{ item.content || '（用户未填写评价内容）' }}</div>

        <!-- 标签 -->
        <div v-if="parseTags(item.tags).length" class="review-tags">
          <el-tag v-for="tag in parseTags(item.tags)" :key="tag" size="small" type="info" effect="plain">{{ tag }}</el-tag>
        </div>

        <!-- 回复 -->
        <div v-if="item.reply" class="review-reply">
          <span class="reply-label">回复：</span>{{ item.reply }}
          <span class="reply-time">{{ item.replyTime }}</span>
        </div>

        <!-- 申诉信息 -->
        <div v-if="item.appealReason" class="review-appeal">
          <span class="appeal-label">申诉理由：</span>{{ item.appealReason }}
        </div>

        <!-- 操作按钮 (收到的评价) -->
        <div v-if="activeTab === 'received'" class="review-actions">
          <el-button v-if="!item.reply" text type="primary" size="small" @click="openReply(item)">
            回复
          </el-button>
          <el-button v-if="item.appealStatus === 0" text type="warning" size="small" @click="openAppeal(item)">
            申诉
          </el-button>
          <el-button text type="primary" size="small" @click="router.push(`/orders/${item.orderId}`)">
            查看订单
          </el-button>
        </div>

        <!-- 发出的评价 -->
        <div v-if="activeTab === 'given'" class="review-actions">
          <span class="review-target">评价对象: 用户 #{{ item.targetId }}</span>
          <el-button text type="primary" size="small" @click="router.push(`/orders/${item.orderId}`)">
            查看订单
          </el-button>
        </div>
      </div>

      <el-empty v-if="!loading && list.length === 0" description="暂无评价记录" />

      <div v-if="total > pageSize" class="pagination">
        <el-pagination background layout="prev, pager, next"
                       :current-page="page" :page-size="pageSize" :total="total"
                       @current-change="p => { page = p; loadData() }" />
      </div>
    </div>

    <!-- 回复弹窗 -->
    <el-dialog v-model="replyDialogVisible" title="回复评价" width="400px">
      <el-input v-model="replyContent" type="textarea" :rows="3" placeholder="输入回复内容..." maxlength="200" show-word-limit />
      <template #footer>
        <el-button @click="replyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReply">提交回复</el-button>
      </template>
    </el-dialog>

    <!-- 申诉弹窗 -->
    <el-dialog v-model="appealDialogVisible" title="申诉评价" width="400px">
      <el-input v-model="appealReason" type="textarea" :rows="3" placeholder="请说明申诉理由..." maxlength="200" show-word-limit />
      <template #footer>
        <el-button @click="appealDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAppeal">提交申诉</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyReceivedReviews, getMyGivenReviews, appealReview, replyReview } from '@/api/review'
import { ElMessage } from 'element-plus'

const router = useRouter()
const activeTab = ref('received')
const list = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)

// 回复
const replyDialogVisible = ref(false)
const replyContent = ref('')
const currentReview = ref(null)

// 申诉
const appealDialogVisible = ref(false)
const appealReason = ref('')
const submitting = ref(false)

function parseTags(tags) {
  if (!tags) return []
  try { return typeof tags === 'string' ? JSON.parse(tags) : tags }
  catch { return [] }
}

function switchTab() {
  page.value = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize }
    const res = activeTab.value === 'received'
      ? await getMyReceivedReviews(params)
      : await getMyGivenReviews(params)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) { ElMessage.error(e.response?.data?.message || '加载失败') }
  finally { loading.value = false }
}

function openReply(item) {
  currentReview.value = item
  replyContent.value = ''
  replyDialogVisible.value = true
}

async function submitReply() {
  if (!replyContent.value.trim()) return ElMessage.warning('请输入回复内容')
  submitting.value = true
  try {
    await replyReview(currentReview.value.id, { reply: replyContent.value.trim() })
    ElMessage.success('回复成功')
    replyDialogVisible.value = false
    loadData()
  } catch (e) { ElMessage.error(e.response?.data?.message || '回复失败') }
  finally { submitting.value = false }
}

function openAppeal(item) {
  currentReview.value = item
  appealReason.value = ''
  appealDialogVisible.value = true
}

async function submitAppeal() {
  if (!appealReason.value.trim()) return ElMessage.warning('请输入申诉理由')
  submitting.value = true
  try {
    await appealReview(currentReview.value.id, { reason: appealReason.value.trim() })
    ElMessage.success('申诉已提交')
    appealDialogVisible.value = false
    loadData()
  } catch (e) { ElMessage.error(e.response?.data?.message || '申诉提交失败') }
  finally { submitting.value = false }
}

onMounted(loadData)
</script>

<style scoped>
.my-reviews { padding-bottom: 20px; }

.review-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.review-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.review-time { font-size: 12px; color: #C9CDD4; }

.review-content {
  font-size: 14px;
  color: #1D2129;
  margin-bottom: 8px;
  line-height: 1.6;
}

.review-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.review-reply {
  background: #F7F8FA;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
  color: #4E5969;
  margin-bottom: 8px;
}

.reply-label { font-weight: 500; color: #1D2129; }
.reply-time { font-size: 12px; color: #C9CDD4; margin-left: 8px; }

.review-appeal {
  background: #FFF7E6;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
  color: #D46B08;
  margin-bottom: 8px;
}

.appeal-label { font-weight: 500; }

.review-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

.review-target { font-size: 12px; color: #86909C; }

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
