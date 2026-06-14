<template>
  <div class="post-detail" v-loading="loading">
    <el-page-header @back="router.back()">
      <template #content>{{ post?.title }}</template>
    </el-page-header>

    <el-card v-if="post" style="margin-top: 20px">
      <div class="post-top">
        <h1>{{ post.title }}</h1>
        <div v-if="isAuthor" class="post-actions">
          <el-button size="small" @click="openEdit">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete">删除</el-button>
        </div>
      </div>
      <div class="meta">
        <el-tag size="small">{{ post.category || '综合' }}</el-tag>
        <span>{{ post.viewCount }} 浏览</span>
        <span class="like-btn" @click="handleLike">
          <el-icon :size="16" :color="post.liked ? '#409eff' : '#909399'">
            <component :is="post.liked ? 'StarFilled' : 'Star'" />
          </el-icon>
          {{ post.likeCount }} 点赞
        </span>
        <span class="like-btn" @click="handleFavorite">
          <el-icon :size="16" :color="favorited ? '#e6a23c' : '#909399'">
            <component :is="favorited ? 'StarFilled' : 'Star'" />
          </el-icon>
          {{ favorited ? '已收藏' : '收藏' }}
        </span>
        <span>{{ post.createTime }}</span>
        <span class="report-btn" @click="openReport('post', post.id)">举报</span>
      </div>
      <div class="content">{{ post.content }}</div>
      <div v-if="imageList.length" class="post-images">
        <el-image v-for="(img, i) in imageList" :key="i" :src="img"
                  fit="contain" style="max-width: 100%; max-height: 400px; margin: 8px 0; border-radius: 8px"
                  :preview-src-list="imageList" :initial-index="i" />
      </div>
    </el-card>

    <el-card v-if="post" style="margin-top: 16px">
      <template #header>
        <span>评论 ({{ commentCount }})</span>
      </template>
      <CommentItem v-for="c in comments" :key="c.id" :comment="c"
                   @like="handleCommentLike" @reply="handleReply"
                   @report="openReport('comment', $event.id)" />
      <el-empty v-if="comments.length === 0" description="暂无评论" :image-size="60" />

      <el-divider />
      <div v-if="replyTo" class="reply-tip">
        回复 用户 #{{ replyTo.userId }}：
        <span class="reply-content">{{ replyTo.content?.substring(0, 50) }}</span>
        <el-icon class="reply-close" @click="cancelReply"><Close /></el-icon>
      </div>
      <el-input v-model="newComment" type="textarea" :rows="3"
                :placeholder="replyTo ? '写下你的回复...' : '写下你的评论...'" />
      <el-upload action="/api/upload/image" :headers="uploadHeaders"
                 list-type="picture-card" :limit="9"
                 :on-success="handleUploadSuccess" :on-remove="handleUploadRemove"
                 style="margin-top: 12px">
        <el-icon><Plus /></el-icon>
      </el-upload>
      <el-button type="primary" style="margin-top: 12px" @click="handleComment" :loading="commenting">
        {{ replyTo ? '回复' : '发表评论' }}
      </el-button>
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="showEdit" title="编辑帖子" width="600" :close-on-click-modal="false">
      <el-form :model="editForm" label-width="70px">
        <el-form-item label="标题" required>
          <el-input v-model="editForm.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editForm.category" style="width: 100%">
            <el-option label="综合" value="综合" />
            <el-option label="学习" value="学习" />
            <el-option label="生活" value="生活" />
            <el-option label="失物招领" value="失物招领" />
            <el-option label="吐槽" value="吐槽" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="editForm.content" type="textarea" :rows="6" maxlength="5000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 举报弹窗 -->
    <el-dialog v-model="showReport" title="举报" width="420">
      <el-form label-width="70px">
        <el-form-item label="举报原因">
          <el-radio-group v-model="reportForm.reason">
            <el-radio value="垃圾广告">垃圾广告</el-radio>
            <el-radio value="色情低俗">色情低俗</el-radio>
            <el-radio value="暴力血腥">暴力血腥</el-radio>
            <el-radio value="违法信息">违法信息</el-radio>
            <el-radio value="其他">其他</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="reportForm.reason === '其他'" label="补充说明">
          <el-input v-model="reportForm.evidence" type="textarea" :rows="2" placeholder="请描述具体原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReport = false">取消</el-button>
        <el-button type="warning" @click="handleSubmitReport" :loading="reporting">提交举报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Star, StarFilled, Close } from '@element-plus/icons-vue'
import { getPostDetail, getCommentTree, createComment, likePost, likeComment, updatePost, deletePost, toggleFavorite, getFavoriteStatus } from '@/api/forum'
import { createReport } from '@/api/report'
import CommentItem from '@/components/CommentItem.vue'

const route = useRoute()
const router = useRouter()
const post = ref(null)
const loading = ref(false)
const showEdit = ref(false)
const saving = ref(false)
const editForm = ref({ title: '', content: '', category: '' })
const userId = Number(localStorage.getItem('userId') || 0)
const isAuthor = computed(() => post.value?.userId === userId)
const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token')}` }

const imageList = computed(() => {
  if (!post.value?.images) return []
  try {
    return typeof post.value.images === 'string' ? JSON.parse(post.value.images) : post.value.images
  } catch { return [] }
})
const comments = ref([])
const commentCount = ref(0)
const newComment = ref('')
const commentImages = ref([])
const commenting = ref(false)
const replyTo = ref(null)
const showReport = ref(false)
const reporting = ref(false)
const reportForm = ref({ targetType: '', targetId: 0, reason: '垃圾广告', evidence: '' })
const favorited = ref(false)

function countComments(list) {
  let count = 0
  for (const c of list) {
    count += 1
    if (c.children?.length) count += countComments(c.children)
  }
  return count
}

async function loadComments() {
  const res = await getCommentTree(route.params.id)
  comments.value = res.data || []
  commentCount.value = countComments(comments.value)
}

onMounted(async () => {
  loading.value = true
  try {
    const [postRes] = await Promise.all([
      getPostDetail(route.params.id),
      loadComments()
    ])
    post.value = postRes.data
    // 加载收藏状态
    try {
      const favRes = await getFavoriteStatus(route.params.id)
      favorited.value = favRes.data
    } catch { /* ignore */ }
  } catch (e) {
    ElMessage.error('加载帖子失败')
  } finally {
    loading.value = false
  }
})

async function handleLike() {
  try {
    const res = await likePost(route.params.id)
    const liked = res.data
    post.value.liked = liked
    post.value.likeCount += liked ? 1 : -1
  } catch (e) { /* handled */ }
}

async function handleFavorite() {
  try {
    const res = await toggleFavorite(route.params.id)
    favorited.value = res.data
    ElMessage.success(res.data ? '已收藏' : '已取消收藏')
  } catch (e) { /* handled */ }
}

function openEdit() {
  editForm.value = {
    title: post.value.title,
    content: post.value.content,
    category: post.value.category || '综合'
  }
  showEdit.value = true
}

async function handleSaveEdit() {
  if (!editForm.value.title.trim() || !editForm.value.content.trim()) {
    ElMessage.warning('请填写标题和内容')
    return
  }
  saving.value = true
  try {
    const res = await updatePost(route.params.id, editForm.value)
    post.value.title = res.data.title
    post.value.content = res.data.content
    post.value.category = res.data.category
    showEdit.value = false
    ElMessage.success('编辑成功')
  } catch (e) {
    ElMessage.error('编辑失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定要删除这篇帖子吗？删除后不可恢复。', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deletePost(route.params.id)
    ElMessage.success('已删除')
    router.push('/forum')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

function handleUploadSuccess(res) {
  if (res.code === 200) {
    commentImages.value.push(res.data.url)
  }
}

function handleUploadRemove(file) {
  const url = file.response?.data?.url || file.url
  const idx = commentImages.value.indexOf(url)
  if (idx > -1) commentImages.value.splice(idx, 1)
}

async function handleCommentLike(comment) {
  try {
    const res = await likeComment(comment.id)
    comment.liked = res.data
    comment.likeCount = (comment.likeCount || 0) + (res.data ? 1 : -1)
  } catch (e) { /* handled */ }
}

function handleReply(comment) {
  replyTo.value = comment
  newComment.value = ''
}

function cancelReply() {
  replyTo.value = null
}

function openReport(targetType, targetId) {
  reportForm.value = { targetType, targetId, reason: '垃圾广告', evidence: '' }
  showReport.value = true
}

async function handleSubmitReport() {
  reporting.value = true
  try {
    await createReport({
      targetType: reportForm.value.targetType,
      targetId: reportForm.value.targetId,
      reason: reportForm.value.reason,
      evidence: reportForm.value.evidence || ''
    })
    ElMessage.success('举报已提交')
    showReport.value = false
  } catch (e) {
    ElMessage.error('举报失败')
  } finally {
    reporting.value = false
  }
}

async function handleComment() {
  if (!newComment.value.trim()) return
  commenting.value = true
  try {
    const data = { content: newComment.value }
    if (commentImages.value.length > 0) {
      data.images = JSON.stringify(commentImages.value)
    }
    if (replyTo.value) {
      data.parentId = replyTo.value.id
    }
    await createComment(route.params.id, data)
    ElMessage.success(replyTo.value ? '回复成功' : '评论成功')
    newComment.value = ''
    commentImages.value = []
    replyTo.value = null
    await loadComments()
  } catch (e) {
    ElMessage.error('评论失败')
  } finally {
    commenting.value = false
  }
}
</script>

<style scoped>
.post-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.post-top h1 {
  margin: 0;
  font-size: 22px;
}

.post-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #909399;
  margin: 12px 0 20px;
}

.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  user-select: none;
  transition: color 0.2s;
}

.like-btn:hover {
  color: #409eff;
}

.report-btn {
  font-size: 12px;
  color: #909399;
  cursor: pointer;
  margin-left: auto;
}

.report-btn:hover {
  color: #f56c6c;
}

.content {
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
}

.reply-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
  color: #909399;
}

.reply-content {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #606266;
}

.reply-close {
  cursor: pointer;
  color: #c0c4cc;
}

.reply-close:hover {
  color: #f56c6c;
}
</style>
