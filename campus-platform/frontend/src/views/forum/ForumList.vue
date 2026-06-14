<template>
  <div class="forum-list">
    <div class="page-header">
      <h2>社区论坛</h2>
      <div class="actions">
        <el-input v-model="keyword" placeholder="搜索帖子..." style="width: 300px; margin-right: 12px"
                  @keyup.enter="handleSearch" clearable @clear="handleSearch" />
        <el-button type="primary" @click="showPublish = true">
          <el-icon><EditPen /></el-icon>发帖
        </el-button>
      </div>
    </div>

    <!-- 帖子来源切换 -->
    <div class="source-tabs">
      <span :class="['source-tab', { active: !showMine }]" @click="showMine = false; currentPage = 1; loadPosts()">全部帖子</span>
      <span :class="['source-tab', { active: showMine }]" @click="showMine = true; currentPage = 1; loadPosts()">我的帖子</span>
    </div>

    <!-- 分类筛选 -->
    <div class="category-tabs" v-if="!showMine">
      <span v-for="cat in categoryOptions" :key="cat"
            :class="['cat-tab', { active: activeCategory === cat }]"
            @click="selectCategory(cat)">
        {{ cat }}
      </span>
    </div>

    <div class="post-list" v-loading="loading">
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

    <el-empty v-if="!loading && posts.length === 0" description="暂无帖子" />

    <!-- 分页 -->
    <div v-if="total > pageSize" class="pagination">
      <el-pagination background layout="prev, pager, next"
                     :current-page="currentPage" :page-size="pageSize" :total="total"
                     @current-change="handlePageChange" />
    </div>

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
import { Plus, EditPen } from '@element-plus/icons-vue'
import { getPostList, getMyPosts, createPost } from '@/api/forum'

const router = useRouter()
const posts = ref([])
const keyword = ref('')
const loading = ref(false)
const showPublish = ref(false)
const publishing = ref(false)
const currentPage = ref(1)
const pageSize = 20
const total = ref(0)
const activeCategory = ref('')
const showMine = ref(false)

const categoryOptions = ['全部', '综合', '学习', '生活', '失物招领', '吐槽']

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
  loading.value = true
  try {
    const params = { page: currentPage.value, size: pageSize }
    let res
    if (showMine.value) {
      res = await getMyPosts(params)
    } else {
      if (activeCategory.value && activeCategory.value !== '全部') {
        params.category = activeCategory.value
      }
      if (keyword.value.trim()) {
        params.keyword = keyword.value.trim()
      }
      res = await getPostList(params)
    }
    posts.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error('加载帖子失败')
  } finally {
    loading.value = false
  }
}

function selectCategory(cat) {
  activeCategory.value = cat === '全部' ? '' : cat
  currentPage.value = 1
  loadPosts()
}

function handleSearch() {
  currentPage.value = 1
  loadPosts()
}

function handlePageChange(page) {
  currentPage.value = page
  loadPosts()
}

function handleUploadSuccess(res) {
  if (res.code === 200) {
    publishForm.images.push(res.data.url)
  }
}

function handleUploadRemove(file) {
  const url = file.response?.data?.url || file.url
  const idx = publishForm.images.indexOf(url)
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
    currentPage.value = 1
    loadPosts()
  } catch (e) {
    ElMessage.error('发帖失败')
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

.source-tabs {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid #F2F3F5;
  padding-bottom: 8px;
}

.source-tab {
  padding: 6px 2px;
  font-size: 15px;
  color: #86909C;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.25s;
}

.source-tab:hover { color: #4E5969; }

.source-tab.active {
  color: #1D2129;
  font-weight: 600;
  border-bottom-color: #5B8FF9;
}

.category-tabs {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.cat-tab {
  padding: 6px 18px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  color: #4E5969;
  background: #F2F3F5;
  cursor: pointer;
  transition: all 0.25s;
}

.cat-tab:hover {
  color: #5B8FF9;
  background: #E8EEFE;
}

.cat-tab.active {
  color: #fff;
  background: linear-gradient(135deg, #5B8FF9, #6366F1);
  box-shadow: 0 2px 8px rgba(91, 143, 249, 0.3);
}

.post-card {
  margin-bottom: 12px;
  cursor: pointer;
  border-radius: 12px;
  transition: box-shadow 0.25s, transform 0.25s;
}

.post-card:hover {
  box-shadow: 0 6px 20px rgba(91, 143, 249, 0.12);
  transform: translateY(-2px);
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
  font-weight: 600;
  color: #1D2129;
}

.post-content {
  color: #4E5969;
  font-size: 14px;
  margin: 8px 0;
  line-height: 1.6;
}

.post-images {
  margin: 10px 0;
}

.post-meta {
  display: flex;
  gap: 16px;
  color: #86909C;
  font-size: 12px;
}

.post-meta .time {
  margin-left: auto;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
