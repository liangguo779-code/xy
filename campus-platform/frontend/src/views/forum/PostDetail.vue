<template>
  <div class="post-detail" v-if="post">
    <el-page-header @back="router.back()">
      <template #content>{{ post.title }}</template>
    </el-page-header>

    <el-card style="margin-top: 20px">
      <h1>{{ post.title }}</h1>
      <div class="meta">
        <el-tag size="small">{{ post.category || '综合' }}</el-tag>
        <span>{{ post.viewCount }} 浏览 · {{ post.likeCount }} 点赞 · {{ post.createTime }}</span>
      </div>
      <div class="content">{{ post.content }}</div>
      <div v-if="imageList.length" class="post-images">
        <el-image v-for="(img, i) in imageList" :key="i" :src="img"
                  fit="contain" style="max-width: 100%; max-height: 400px; margin: 8px 0; border-radius: 8px"
                  :preview-src-list="imageList" :initial-index="i" />
      </div>
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header>
        <span>评论 ({{ comments.length }})</span>
      </template>
      <div v-for="c in comments" :key="c.id" class="comment-item">
        <div class="comment-header">
          <span class="author">用户 #{{ c.userId }}</span>
          <span class="time">{{ c.createTime }}</span>
        </div>
        <p>{{ c.content }}</p>
      </div>
      <el-empty v-if="comments.length === 0" description="暂无评论" :image-size="60" />

      <el-divider />
      <el-input v-model="newComment" type="textarea" :rows="3" placeholder="写下你的评论..." />
      <el-button type="primary" style="margin-top: 12px" @click="handleComment" :loading="commenting">
        发表评论
      </el-button>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPostDetail, getComments, createComment } from '@/api/forum'

const route = useRoute()
const router = useRouter()
const post = ref(null)

const imageList = computed(() => {
  if (!post.value?.images) return []
  try {
    return typeof post.value.images === 'string' ? JSON.parse(post.value.images) : post.value.images
  } catch { return [] }
})
const comments = ref([])
const newComment = ref('')
const commenting = ref(false)

onMounted(async () => {
  const [postRes, commentsRes] = await Promise.all([
    getPostDetail(route.params.id),
    getComments(route.params.id, { page: 1, size: 50 })
  ])
  post.value = postRes.data
  comments.value = commentsRes.data?.records || []
})

async function handleComment() {
  if (!newComment.value.trim()) return
  commenting.value = true
  try {
    await createComment(route.params.id, { content: newComment.value })
    ElMessage.success('评论成功')
    newComment.value = ''
    const res = await getComments(route.params.id, { page: 1, size: 50 })
    comments.value = res.data?.records || []
  } catch (e) {
    // handled
  } finally {
    commenting.value = false
  }
}
</script>

<style scoped>
.meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #909399;
  margin: 12px 0 20px;
}

.content {
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
}

.comment-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.comment-header .author {
  font-weight: bold;
  color: #303133;
}

.comment-header .time {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
