<template>
  <div class="forum-list">
    <div class="page-header">
      <h2>社区论坛</h2>
      <div class="actions">
        <el-input v-model="keyword" placeholder="搜索帖子..." style="width: 300px; margin-right: 12px"
                  @keyup.enter="handleSearch" clearable />
        <el-button type="primary" @click="showPublish = true">
          <el-icon><EditPen /></el-icon>发帖
        </el-button>
      </div>
    </div>

    <div class="post-list">
      <el-card v-for="post in posts" :key="post.id" class="post-card"
               @click="router.push(`/forum/${post.id}`)">
        <div class="post-header">
          <el-tag v-if="post.isTop" type="danger" size="small">置顶</el-tag>
          <el-tag size="small">{{ post.category || '综合' }}</el-tag>
          <h3 class="post-title">{{ post.title }}</h3>
        </div>
        <p class="post-content">{{ post.content?.substring(0, 120) }}...</p>
        <div v-if="getImageList(post.images).length" class="post-images">
          <el-image v-for="(img, i) in getImageList(post.images).slice(0, 3)" :key="i"
                    :src="img" fit="cover" style="width: 80px; height: 80px; border-radius: 6px; margin-right: 8px" />
        </div>
        <div class="post-meta">
          <span>{{ post.viewCount }} 浏览</span>
          <span>{{ post.likeCount }} 点赞</span>
          <span>{{ post.commentCount }} 评论</span>
          <span class="time">{{ post.createTime }}</span>
        </div>
      </el-card>
    </div>

    <el-empty v-if="posts.length === 0" description="暂无帖子" />

    <!-- 发帖弹窗 -->
    <el-dialog v-model="showPublish" title="发帖" width="600" :close-on-click-modal="false">
      <el-form :model="publishForm" label-width="70px">
        <el-form-item label="标题" required>
          <el-input v-model="publishForm.title" placeholder="帖子标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="publishForm.category" placeholder="选择分类" style="width: 100%">
            <el-option label="综合" value="综合" />
            <el-option label="学习" value="学习" />
            <el-option label="生活" value="生活" />
            <el-option label="失物招领" value="失物招领" />
            <el-option label="吐槽" value="吐槽" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="publishForm.content" type="textarea" :rows="6"
                    placeholder="分享你的想法..." maxlength="5000" show-word-limit />
        </el-form-item>
        <el-form-item label="图片">
          <el-upload action="/api/upload/image" :headers="uploadHeaders"
                     list-type="picture-card" :limit="9"
                     :on-success="handleUploadSuccess" :on-remove="handleUploadRemove">
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPublish = false">取消</el-button>
        <el-button type="primary" @click="handlePublish" :loading="publishing">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getPostList, searchPosts, createPost } from '@/api/forum'

const router = useRouter()
const posts = ref([])
const keyword = ref('')
const showPublish = ref(false)
const publishing = ref(false)

const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token')}` }

const publishForm = reactive({
  title: '',
  content: '',
  category: '综合',
  images: []
})

function getImageList(images) {
  if (!images) return []
  try {
    return typeof images === 'string' ? JSON.parse(images) : images
  } catch { return [] }
}

async function loadPosts() {
  const res = await getPostList({ page: 1, size: 20 })
  posts.value = res.data?.records || []
}

async function handleSearch() {
  if (!keyword.value.trim()) {
    loadPosts()
    return
  }
  const res = await searchPosts({ keyword: keyword.value, page: 1, size: 20 })
  posts.value = res.data?.records || []
}

function handleUploadSuccess(res) {
  if (res.code === 200) {
    publishForm.images.push(res.data.url)
  }
}

function handleUploadRemove(file) {
  const idx = publishForm.images.indexOf(file.url || file.response?.data?.url)
  if (idx > -1) publishForm.images.splice(idx, 1)
}

async function handlePublish() {
  if (!publishForm.title.trim() || !publishForm.content.trim()) {
    ElMessage.warning('请填写标题和内容')
    return
  }
  publishing.value = true
  try {
    await createPost({
      title: publishForm.title,
      content: publishForm.content,
      category: publishForm.category,
      images: JSON.stringify(publishForm.images)
    })
    ElMessage.success('发帖成功')
    showPublish.value = false
    Object.assign(publishForm, { title: '', content: '', category: '综合', images: [] })
    loadPosts()
  } finally {
    publishing.value = false
  }
}

onMounted(loadPosts)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.actions {
  display: flex;
  align-items: center;
}

.post-card {
  margin-bottom: 12px;
  cursor: pointer;
}

.post-card:hover {
  border-color: #409eff;
}

.post-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.post-title {
  margin: 0;
  font-size: 16px;
}

.post-content {
  color: #606266;
  font-size: 14px;
  margin: 8px 0;
  line-height: 1.6;
}

.post-images {
  margin: 8px 0;
}

.post-meta {
  display: flex;
  gap: 16px;
  color: #909399;
  font-size: 12px;
}

.post-meta .time {
  margin-left: auto;
}
</style>
