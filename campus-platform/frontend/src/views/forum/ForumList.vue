<template>
  <div class="forum-list">
    <!-- 页头：关键字搜索与发帖入口 -->
    <div class="page-header">
      <h2>社区论坛</h2>
      <div class="actions">
        <el-input v-model="keyword" placeholder="搜索帖子..." style="width: 300px; margin-right: 12px"
                  @keyup.enter="handleSearch" clearable @clear="handleSearch">
          <template #append>
            <el-button @click="handleSearch"><el-icon><Search /></el-icon>&nbsp;搜索</el-button>
          </template>
        </el-input>
        <el-select v-model="sortBy" style="width: 110px; margin-right: 12px" @change="handleSearch">
          <el-option label="最新" value="latest" />
          <el-option label="最热" value="hot" />
        </el-select>
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期"
                        style="margin-right: 12px" @change="handleSearch" clearable />
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

    <!-- 主内容 + 热门榜单侧栏 -->
    <div class="content-wrap">
      <div class="content-main">
        <!-- 帖子卡片列表：点击卡片跳转详情页 -->
        <div class="post-list" v-loading="loading">
          <el-card v-for="post in posts" :key="post.id" class="post-card"
                   @click="router.push(`/forum/${post.id}`)">
            <div class="post-header">
              <el-tag v-if="post.isTop" type="danger" size="small">置顶</el-tag>
              <el-tag size="small">{{ post.category || '综合' }}</el-tag>
              <!-- 搜索命中带 <em> 高亮标记：经 highlightHtml 转义后仅保留 em 标签渲染 -->
              <h3 class="post-title" v-html="highlightHtml(post.title)"></h3>
            </div>
            <p class="post-content" v-html="highlightHtml(post.content, 120) + '...'"></p>
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
      </div>

      <!-- 热门榜单：加载失败静默降级（无数据时不渲染），不影响主列表 -->
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
/**
 * 社区论坛列表页：全部/我的帖子切换、分类与关键字筛选、分页浏览，以及发帖（含最多 9 张图片上传）
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, EditPen, Search } from '@element-plus/icons-vue'
import { getPostList, getMyPosts, createPost, getHotPosts } from '@/api/forum'
import { highlightHtml } from '@/utils/highlight'

const router = useRouter()
const posts = ref([])
// 热门榜单 top10：失败静默降级为空数组，侧栏自动隐藏
const hotPosts = ref([])
const keyword = ref('')
const loading = ref(false)
const showPublish = ref(false)
const publishing = ref(false)
const currentPage = ref(1)
const pageSize = 20
const total = ref(0)
const activeCategory = ref('')
const showMine = ref(false)
// 排序：latest-最新（默认） hot-热度（点赞/评论/浏览加权）
const sortBy = ref('latest')
// 创建时间范围：['yyyy-MM-dd', 'yyyy-MM-dd']，清空后为 null
const dateRange = ref(null)

const categoryOptions = ['全部', '综合', '学习', '生活', '失物招领', '吐槽']

// 图片上传接口要求 Authorization 头，组件初始化时读取一次 token
const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token')}` }

/** 发帖表单：images 先收集上传成功的 URL，提交时序列化为 JSON 字符串入库 */
const publishForm = reactive({
  title: '',
  content: '',
  category: '综合',
  images: []
})

/** 解析帖子图片字段：后端存 JSON 字符串，防御性兼容数组，解析失败按无图处理 */
function getImageList(images) {
  if (!images) return []
  try {
    return typeof images === 'string' ? JSON.parse(images) : images
  } catch { return [] }
}

/** 加载帖子分页列表：我的帖子与全部帖子走不同接口，分类/关键字过滤仅全部列表支持 */
async function loadPosts() {
  loading.value = true
  try {
    const params = { page: currentPage.value, size: pageSize }
    let res
    if (showMine.value) {
      res = await getMyPosts(params)
    } else {
      // '全部' 等于不过滤，不向后端传 category 即查全部分类
      if (activeCategory.value && activeCategory.value !== '全部') {
        params.category = activeCategory.value
      }
      // 关键字需去首尾空格，为空时不传参，避免后端按空串过滤
      if (keyword.value.trim()) {
        params.keyword = keyword.value.trim()
      }
      // 排序与时间范围筛选（仅全部帖子支持）
      if (sortBy.value) {
        params.sortBy = sortBy.value
      }
      if (dateRange.value && dateRange.value.length === 2) {
        params.startTime = dateRange.value[0]
        params.endTime = dateRange.value[1]
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

/** 加载热门榜单：失败静默降级（不弹错误提示），不打扰主列表 */
async function loadHotPosts() {
  try {
    const res = await getHotPosts(10)
    hotPosts.value = res.data || []
  } catch (e) { /* 静默降级：侧栏不渲染即可 */ }
}

/** 切换分类：'全部' 映射为空值不过滤，并回到第一页重新加载 */
function selectCategory(cat) {
  activeCategory.value = cat === '全部' ? '' : cat
  currentPage.value = 1
  loadPosts()
}

/** 搜索：筛选条件变化后回到第一页，避免停留在超出总页数的空页 */
function handleSearch() {
  currentPage.value = 1
  loadPosts()
}

/** 分页回调：Element Plus 页码从 1 开始，与后端 page 参数约定一致 */
function handlePageChange(page) {
  currentPage.value = page
  loadPosts()
}

/** 上传成功回调：后端约定 code 200 时 data.url 为图片访问地址 */
function handleUploadSuccess(res) {
  if (res.code === 200) {
    publishForm.images.push(res.data.url)
  }
}

/** 删除图片：兼容刚上传（response 带 url）与回显（file.url）两种 file 结构 */
function handleUploadRemove(file) {
  const url = file.response?.data?.url || file.url
  const idx = publishForm.images.indexOf(url)
  if (idx > -1) publishForm.images.splice(idx, 1)
}

/** 提交发帖：images 序列化为 JSON 字符串，与后端 TEXT 字段存储约定一致；成功后重置表单并回第一页刷新 */
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

onMounted(() => {
  loadPosts()
  loadHotPosts()
})
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

/* ES 搜索高亮：v-html 渲染的 em 标签（scoped 下需 :deep） */
.post-title :deep(em),
.post-content :deep(em) {
  color: #f56c6c;
  font-style: normal;
  font-weight: 600;
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

.content-wrap {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.content-main {
  flex: 1;
  min-width: 0;
}

.hot-panel {
  width: 280px;
  flex-shrink: 0;
  position: sticky;
  top: 16px;
}

.hot-card {
  border-radius: 12px;
}

.hot-title {
  font-size: 15px;
  font-weight: 600;
  color: #1D2129;
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
  background: #E8EEFE;
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
  color: #86909C;
  background: #F2F3F5;
}

.hot-rank.rank-1 {
  color: #fff;
  background: #F56C6C;
}

.hot-rank.rank-2 {
  color: #fff;
  background: #E6A23C;
}

.hot-rank.rank-3 {
  color: #fff;
  background: #5B8FF9;
}

.hot-info {
  flex: 1;
  min-width: 0;
}

.hot-item-title {
  font-size: 13px;
  color: #1D2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hot-item-meta {
  display: flex;
  gap: 10px;
  margin-top: 2px;
  font-size: 12px;
  color: #86909C;
}
</style>
