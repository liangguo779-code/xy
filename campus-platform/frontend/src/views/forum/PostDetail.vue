<template>
  <div class="post-detail" v-loading="loading">
    <el-page-header @back="router.back()">
      <template #content>{{ post?.title }}</template>
    </el-page-header>

    <!-- 正文/评论主栏 + 热门榜单侧栏 -->
    <div class="detail-wrap">
      <div class="detail-main">
        <!-- 帖子正文卡片：楼主可见编辑/删除，其余用户可点赞、收藏、举报 -->
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

        <!-- 评论区：树形评论列表（含楼中楼回复）加评论/回复输入 -->
        <el-card v-if="post" style="margin-top: 16px">
          <template #header>
            <span>评论 ({{ commentCount }})</span>
          </template>
          <CommentItem v-for="c in comments" :key="c.id" :comment="c"
                       @like="handleCommentLike" @reply="handleReply"
                       @report="openReport('comment', $event.id)" />
          <el-empty v-if="comments.length === 0" description="暂无评论" :image-size="60" />

          <el-divider />
          <!-- 回复目标提示条：展示被回复楼层摘要，可取消回到普通评论模式 -->
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
      </div>

      <!-- 热门榜单：加载失败静默降级（无数据时不渲染），不影响详情与评论 -->
      <aside v-if="hotPosts.length" class="hot-panel">
        <el-card class="hot-card">
          <template #header>
            <span class="hot-title">热门榜单</span>
          </template>
          <div v-for="(p, i) in hotPosts" :key="p.id" class="hot-item"
               @click="router.push(`/forum/${p.id}`)">
            <span :class="['hot-rank', `rank-${i + 1}`]">{{ i + 1 }}</span>
            <div class="hot-info">
              <div class="hot-item-title">{{ p.title }}</div>
              <div class="hot-item-meta">
                <span>{{ p.viewCount }} 浏览</span>
                <span>{{ p.likeCount }} 点赞</span>
              </div>
            </div>
          </div>
        </el-card>
      </aside>
    </div>

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
/**
 * 帖子详情页：展示正文与图片，支持点赞/收藏/举报、楼主编辑删除，以及树形评论与楼中楼回复
 */
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Star, StarFilled, Close } from '@element-plus/icons-vue'
import { getPostDetail, getCommentTree, createComment, likePost, likeComment, updatePost, deletePost, toggleFavorite, getFavoriteStatus, getHotPosts } from '@/api/forum'
import { createReport } from '@/api/report'
import CommentItem from '@/components/CommentItem.vue'

const route = useRoute()
const router = useRouter()
const post = ref(null)
const loading = ref(false)
const showEdit = ref(false)
const saving = ref(false)
const editForm = ref({ title: '', content: '', category: '' })
// 登录时缓存的当前用户 id，用于比对楼主身份
const userId = Number(localStorage.getItem('userId') || 0)
/** 是否楼主：仅楼主展示编辑/删除按钮 */
const isAuthor = computed(() => post.value?.userId === userId)
// 评论图片上传接口要求 Authorization 头
const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token')}` }

/** 帖子图片列表：后端 images 存 JSON 字符串，防御性兼容数组格式 */
const imageList = computed(() => {
  if (!post.value?.images) return []
  try {
    return typeof post.value.images === 'string' ? JSON.parse(post.value.images) : post.value.images
  } catch { return [] }
})
/** 评论树（后端返回嵌套 children 结构）与递归统计出的总评论数 */
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
// 热门榜单 top5：失败静默降级为空数组，侧栏自动隐藏
const hotPosts = ref([])

/** 加载热门榜单：失败静默降级（不弹错误提示），不影响详情与评论 */
async function loadHotPosts() {
  try {
    const res = await getHotPosts(5)
    hotPosts.value = res.data || []
  } catch (e) { /* 静默降级：侧栏不渲染即可 */ }
}

/** 递归统计评论树节点总数（含各层级回复），后端不直接返回计数 */
function countComments(list) {
  let count = 0
  for (const c of list) {
    count += 1
    if (c.children?.length) count += countComments(c.children)
  }
  return count
}

/** 加载评论树；发表评论/回复后重新拉取整树以同步最新计数 */
async function loadComments() {
  const res = await getCommentTree(route.params.id)
  comments.value = res.data || []
  commentCount.value = countComments(comments.value)
}

/** 加载帖子详情：并行加载正文与评论树，收藏状态单独请求且失败不阻断主内容 */
async function loadDetail() {
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
}

/** 进入页面：加载帖子详情与热门榜单 */
onMounted(() => {
  loadDetail()
  loadHotPosts()
})

// 同路由切换帖子 id（如侧栏热门榜单点击）时组件被复用，需按新 id 重新加载；离开详情页时 id 变空不处理
watch(() => route.params.id, (id) => {
  if (id) loadDetail()
})

/** 点赞为切换语义：后端返回当前是否已赞，本地同步增减计数避免整页刷新 */
async function handleLike() {
  try {
    const res = await likePost(route.params.id)
    const liked = res.data
    post.value.liked = liked
    post.value.likeCount += liked ? 1 : -1
  } catch (e) { /* handled */ }
}

/** 收藏切换：后端返回最新收藏状态 */
async function handleFavorite() {
  try {
    const res = await toggleFavorite(route.params.id)
    favorited.value = res.data
    ElMessage.success(res.data ? '已收藏' : '已取消收藏')
  } catch (e) { /* handled */ }
}

/** 打开编辑弹窗：以当前帖子数据初始化表单 */
function openEdit() {
  editForm.value = {
    title: post.value.title,
    content: post.value.content,
    category: post.value.category || '综合'
  }
  showEdit.value = true
}

/** 保存编辑：直接用响应体覆盖本地展示，省去重新拉详情 */
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

/** 删除帖子：确认框点取消会以 'cancel' reject，此时不提示失败 */
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

/** 上传成功：后端约定 code 200 时 data.url 为图片地址，收集后随评论一起提交 */
function handleUploadSuccess(res) {
  if (res.code === 200) {
    commentImages.value.push(res.data.url)
  }
}

/** 删除图片：兼容刚上传（response 带 url）与回显（file.url）两种 file 结构 */
function handleUploadRemove(file) {
  const url = file.response?.data?.url || file.url
  const idx = commentImages.value.indexOf(url)
  if (idx > -1) commentImages.value.splice(idx, 1)
}

/** 评论点赞切换：就地更新该节点数据，无需重拉整棵评论树 */
async function handleCommentLike(comment) {
  try {
    const res = await likeComment(comment.id)
    comment.liked = res.data
    comment.likeCount = (comment.likeCount || 0) + (res.data ? 1 : -1)
  } catch (e) { /* handled */ }
}

/** 进入回复模式：记录被回复的评论并清空输入框 */
function handleReply(comment) {
  replyTo.value = comment
  newComment.value = ''
}

/** 取消回复，恢复为普通评论模式 */
function cancelReply() {
  replyTo.value = null
}

/** 打开举报弹窗：targetType 为 post/comment，与后端举报记录的类型枚举约定一致 */
function openReport(targetType, targetId) {
  reportForm.value = { targetType, targetId, reason: '垃圾广告', evidence: '' }
  showReport.value = true
}

/** 提交举报：reason 为固定文案枚举，选'其他'时由 evidence 承载具体描述 */
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

/** 发表评论/回复：带 parentId 即楼中楼回复；图片与帖子一致序列化为 JSON 字符串入库 */
async function handleComment() {
  if (!newComment.value.trim()) return
  commenting.value = true
  try {
    const data = { content: newComment.value }
    // 仅在有图时传 images 字段，后端允许为空
    if (commentImages.value.length > 0) {
      data.images = JSON.stringify(commentImages.value)
    }
    // 携带 parentId 表示回复某条评论，后端据此挂到对应楼层下
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

/* pre-wrap 保留用户输入的换行：帖子正文是纯文本而非 Markdown */
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

.detail-wrap {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.detail-main {
  flex: 1;
  min-width: 0;
}

.hot-panel {
  width: 280px;
  flex-shrink: 0;
  margin-top: 20px;
  position: sticky;
  top: 16px;
}

.hot-card {
  border-radius: 12px;
}

.hot-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.hot-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.25s;
}

.hot-item:hover {
  background: #ecf5ff;
}

.hot-rank {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  background: #f5f7fa;
}

.hot-rank.rank-1 {
  color: #fff;
  background: #f56c6c;
}

.hot-rank.rank-2 {
  color: #fff;
  background: #e6a23c;
}

.hot-rank.rank-3 {
  color: #fff;
  background: #409eff;
}

.hot-info {
  flex: 1;
  min-width: 0;
}

.hot-item-title {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hot-item-meta {
  display: flex;
  gap: 10px;
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}
</style>
