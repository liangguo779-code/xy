<template>
  <div class="detail-page" v-if="goods">
    <!-- 顶部轮播图 -->
    <div class="media-section">
      <div class="back-btn" @click="router.back()">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </div>
      <el-carousel v-if="imageList.length" height="360px" :autoplay="false" indicator-position="none">
        <el-carousel-item v-for="(img, i) in imageList" :key="i">
          <el-image :src="img" fit="contain" style="width: 100%; height: 360px; background: #000" />
        </el-carousel-item>
      </el-carousel>
      <div v-else class="no-media">暂无图片</div>
      <!-- 图片计数 -->
      <div class="media-count" v-if="imageList.length">{{ currentIndex + 1 }}/{{ imageList.length }}</div>
    </div>

    <!-- 价格区 -->
    <div class="price-section">
      <div class="price-main">
        <span class="symbol">¥</span>
        <span class="amount">{{ goods.price }}</span>
        <span class="original" v-if="goods.originalPrice">¥{{ goods.originalPrice }}</span>
      </div>
      <div class="price-tags">
        <el-tag v-if="goods.condition" size="small" type="info">{{ goods.condition }}</el-tag>
        <el-tag v-if="goods.type === 1" size="small" type="warning">求购</el-tag>
        <el-tag v-if="goods.status === 2" size="small" type="danger">已售出</el-tag>
      </div>
      <div class="stats">
        <span>{{ goods.viewCount || 0 }}浏览</span>
        <span>{{ goods.wantCount || 0 }}想要</span>
        <span>{{ goods.likeCount || 0 }}收藏</span>
      </div>
    </div>

    <!-- 标题与描述 -->
    <div class="info-section">
      <h1 class="title">{{ goods.title }}</h1>
      <p class="description">{{ goods.description }}</p>
      <div class="meta-row">
        <span>分类：{{ goods.category || '其他' }}</span>
        <span>发布于 {{ formatTime(goods.createTime) }}</span>
      </div>
    </div>

    <!-- 卖家信息 -->
    <div class="seller-section">
      <div class="seller-info">
        <el-avatar :size="40">{{ goods.userId?.toString().slice(-1) }}</el-avatar>
        <div class="seller-detail">
          <div class="seller-name">卖家 #{{ goods.userId }}</div>
          <div class="seller-tag">校园认证用户</div>
        </div>
      </div>
      <div class="seller-actions">
        <el-button v-if="!isFollowing" type="primary" @click="handleFollow">
          <el-icon><Plus /></el-icon> 关注
        </el-button>
        <el-button v-else @click="handleFollow">
          已关注
        </el-button>
      </div>
    </div>

    <!-- 评价模块 -->
    <div class="review-section">
      <div class="section-header">
        <span>卖家评价</span>
        <div class="rating-summary" v-if="sellerRating">
          <el-rate :model-value="sellerRating" disabled show-score text-color="#ff9900" />
          <span class="review-count">({{ sellerReviewCount }}条评价)</span>
        </div>
      </div>
      <div v-if="sellerReviews.length > 0" class="review-list">
        <div v-for="r in sellerReviews" :key="r.id" class="review-item">
          <div class="review-header">
            <el-avatar :size="28">U</el-avatar>
            <span class="reviewer">用户 #{{ r.reviewerId }}</span>
            <el-rate :model-value="r.rating" disabled size="small" />
            <span class="review-time">{{ formatTime(r.createTime) }}</span>
          </div>
          <div class="review-content">{{ r.content }}</div>
          <div v-if="parseTags(r.tags).length" class="review-tags">
            <el-tag v-for="t in parseTags(r.tags)" :key="t" size="small" type="info">{{ t }}</el-tag>
          </div>
        </div>
      </div>
      <div v-else class="review-placeholder">
        <el-empty description="暂无评价" :image-size="40" />
      </div>
    </div>

    <!-- 底部吸底操作栏 -->
    <div class="bottom-bar">
      <div class="bar-left">
        <div class="bar-item" @click="handleFavorite">
          <el-icon :size="20" :color="isFavorited ? '#f56c6c' : '#606266'">
            <StarFilled v-if="isFavorited" /><Star v-else />
          </el-icon>
          <span>收藏</span>
        </div>
        <div class="bar-item" @click="handleShare">
          <el-icon :size="20"><Share /></el-icon>
          <span>分享</span>
        </div>
      </div>
      <div class="bar-right">
        <el-button v-if="!isOwner" size="large" @click="handleWant" :loading="wanting">
          <el-icon><ChatDotRound /></el-icon> 私聊沟通
        </el-button>
        <el-button v-if="!isOwner" type="danger" size="large" @click="handleWant" :loading="wanting">
          我想要
        </el-button>
        <el-button v-if="isOwner && goods.status === 0" size="large" @click="showEdit = true">
          编辑
        </el-button>
        <el-button v-if="isOwner && goods.status === 0" type="danger" plain size="large" @click="showOffShelf = true">
          下架
        </el-button>
        <el-tag v-if="isOwner && goods.status === 1" type="info" size="large">已下架</el-tag>
        <el-tag v-if="isOwner && goods.status === 2" type="success" size="large">已售出</el-tag>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="showEdit" title="编辑商品" width="520">
      <el-form :model="editForm" label-width="70px">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="售价">
          <el-input-number v-model="editForm.price" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="成色">
          <el-select v-model="editForm.condition">
            <el-option label="全新" value="全新" />
            <el-option label="几乎全新" value="几乎全新" />
            <el-option label="良好" value="良好" />
            <el-option label="一般" value="一般" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 下架确认弹窗 -->
    <el-dialog v-model="showOffShelf" title="下架商品" width="400">
      <p>确定要下架此商品吗？下架后将不再显示在商品列表中。</p>
      <div style="margin-top: 12px">
        <el-radio-group v-model="offShelfReason">
          <el-radio value="sold">已卖出</el-radio>
          <el-radio value="cancel">不想卖了</el-radio>
          <el-radio value="other">其他原因</el-radio>
        </el-radio-group>
      </div>
      <template #footer>
        <el-button @click="showOffShelf = false">取消</el-button>
        <el-button type="danger" @click="handleDelist">确认下架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getGoodsDetail, markAsSold, deleteGoods } from '@/api/goods'
import { startSession } from '@/api/chat'
import { ArrowLeft } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { addFavorite, removeFavorite, checkFavorite } from '@/api/favorite'
import { followUser, unfollowUser, checkFollow } from '@/api/follow'

const route = useRoute()
const router = useRouter()
const goods = ref(null)
const wanting = ref(false)
const isFavorited = ref(false)
const showEdit = ref(false)
const showOffShelf = ref(false)
const offShelfReason = ref('sold')
const editForm = ref({ title: '', description: '', price: 0, condition: '' })
const currentIndex = ref(0)
const sellerReviews = ref([])
const sellerRating = ref(0)
const sellerReviewCount = ref(0)

const isOwner = computed(() => {
  const userId = Number(localStorage.getItem('userId') || 0)
  return goods.value?.userId === userId
})

const imageList = computed(() => {
  try {
    if (!goods.value?.images) return []
    const imgs = typeof goods.value.images === 'string'
      ? JSON.parse(goods.value.images) : goods.value.images
    return imgs || []
  } catch { return [] }
})

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleDateString('zh-CN')
}

function parseTags(tags) {
  if (!tags) return []
  try {
    return typeof tags === 'string' ? JSON.parse(tags) : tags
  } catch { return [] }
}

async function fetchSellerReviews(userId) {
  try {
    const res = await request.get(`/api/reviews/user/${userId}`)
    sellerReviews.value = (res.data || []).slice(0, 5)
    sellerReviewCount.value = (res.data || []).length
    if (sellerReviewCount.value > 0) {
      const sum = sellerReviews.value.reduce((acc, r) => acc + r.rating, 0)
      sellerRating.value = sum / sellerReviews.value.length
    }
  } catch (e) { /* ignore */ }
}

async function handleWant() {
  wanting.value = true
  try {
    const res = await startSession(goods.value.id)
    router.push(`/chat/${res.data.id}`)
  } finally {
    wanting.value = false
  }
}

async function handleFavorite() {
  try {
    if (isFavorited.value) {
      await removeFavorite(goods.value.id)
      isFavorited.value = false
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(goods.value.id)
      isFavorited.value = true
      ElMessage.success('已收藏')
    }
  } catch (e) { /* handled */ }
}

const isFollowing = ref(false)

async function handleFollow() {
  try {
    if (isFollowing.value) {
      await unfollowUser(goods.value.userId)
      isFollowing.value = false
      ElMessage.success('已取消关注')
    } else {
      await followUser(goods.value.userId)
      isFollowing.value = true
      ElMessage.success('已关注')
    }
  } catch (e) { /* handled */ }
}

function handleShare() {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(window.location.href)
    ElMessage.success('链接已复制')
  }
}

async function handleDelist() {
  try {
    await deleteGoods(goods.value.id)
    ElMessage.success('商品已下架')
    showOffShelf.value = false
    goods.value.status = 1
  } catch (e) { /* handled */ }
}

async function handleSaveEdit() {
  try {
    await request.put(`/api/goods/${goods.value.id}`, editForm.value)
    ElMessage.success('保存成功')
    showEdit.value = false
    // 刷新商品信息
    const res = await getGoodsDetail(route.params.id)
    goods.value = res.data
  } catch (e) { /* handled */ }
}

onMounted(async () => {
  const res = await getGoodsDetail(route.params.id)
  goods.value = res.data
  // 初始化编辑表单
  editForm.value = {
    title: goods.value.title || '',
    description: goods.value.description || '',
    price: goods.value.price || 0,
    condition: goods.value.condition || '良好'
  }
  // 检查收藏和关注状态
  try {
    const [favRes, followRes] = await Promise.all([
      checkFavorite(goods.value.id),
      checkFollow(goods.value.userId)
    ])
    isFavorited.value = favRes.data || false
    isFollowing.value = followRes.data || false
  } catch (e) { /* ignore */ }
  // 获取卖家评价
  fetchSellerReviews(goods.value.userId)
})
</script>

<style scoped>
.detail-page {
  padding-bottom: 70px;
  background: #f5f5f5;
}

/* 轮播图 */
.media-section {
  position: relative;
  background: #000;
}

.back-btn {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 10;
  background: rgba(0,0,0,0.5);
  border-radius: 50%;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #fff;
  transition: background 0.2s;
}

.back-btn:hover {
  background: rgba(0,0,0,0.7);
}

.media-count {
  position: absolute;
  bottom: 12px;
  right: 12px;
  background: rgba(0,0,0,0.5);
  color: #fff;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 12px;
}

.no-media {
  height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  background: #f5f7fa;
}

/* 价格区 */
.price-section {
  background: #fff;
  padding: 16px;
}

.price-main {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 8px;
}

.symbol { font-size: 16px; color: #f56c6c; font-weight: 700; }
.amount { font-size: 32px; color: #f56c6c; font-weight: 700; }
.original { font-size: 14px; color: #c0c4cc; text-decoration: line-through; margin-left: 8px; }

.price-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}

.stats {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

/* 信息区 */
.info-section {
  background: #fff;
  padding: 16px;
  margin-top: 8px;
}

.title {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  margin: 0 0 12px;
}

.description {
  font-size: 14px;
  color: #606266;
  line-height: 1.8;
  margin: 0 0 12px;
  white-space: pre-wrap;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #c0c4cc;
}

/* 卖家区 */
.seller-section {
  background: #fff;
  padding: 16px;
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.seller-info {
  display: flex;
  gap: 12px;
  align-items: center;
}

.seller-name {
  font-weight: 500;
  margin-bottom: 2px;
}

.seller-tag {
  font-size: 12px;
  color: #909399;
}

/* 评价区 */
.review-section {
  background: #fff;
  padding: 16px;
  margin-top: 8px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 500;
  margin-bottom: 12px;
}

.review-placeholder {
  min-height: 80px;
}

.rating-summary {
  display: flex;
  align-items: center;
  gap: 8px;
}

.review-count {
  font-size: 13px;
  color: #909399;
}

.review-list {
  max-height: 400px;
  overflow-y: auto;
}

.review-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.review-item:last-child {
  border-bottom: none;
}

.review-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.reviewer {
  font-weight: 500;
  font-size: 13px;
  color: #303133;
}

.review-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-left: auto;
}

.review-content {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 8px;
}

.review-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* 底部栏 */
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  box-shadow: 0 -2px 8px rgba(0,0,0,0.06);
  z-index: 100;
}

.bar-left {
  display: flex;
  gap: 20px;
}

.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  color: #606266;
  cursor: pointer;
}

.bar-right {
  display: flex;
  gap: 8px;
}
</style>
