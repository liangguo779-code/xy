<template>
  <div class="seller-profile" v-loading="loading">
    <el-page-header @back="router.back()">
      <template #content>卖家主页</template>
    </el-page-header>

    <!-- 卖家信息 -->
    <el-card style="margin-top: 20px">
      <div class="seller-header">
        <el-avatar :size="56" class="seller-avatar">
          {{ seller.nickname?.charAt(0) || 'U' }}
        </el-avatar>
        <div class="seller-info">
          <div class="seller-name">{{ seller.nickname || '用户' }}</div>
          <div class="seller-stats">
            <span v-if="stats.averageRating">
              <el-rate :model-value="stats.averageRating" disabled size="small" style="display: inline-flex" />
              {{ stats.averageRating.toFixed(1) }}
            </span>
            <span>{{ stats.reviewCount || 0 }} 条评价</span>
            <span>{{ stats.positiveRate || 0 }}% 好评</span>
          </div>
          <div class="seller-actions">
            <el-button v-if="!isSelf && !isFollowing" type="primary" size="small" @click="handleFollow">
              关注
            </el-button>
            <el-button v-if="!isSelf && isFollowing" size="small" @click="handleFollow">
              已关注
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 他的商品 -->
    <el-card style="margin-top: 12px">
      <template #header>TA 的商品 ({{ total }})</template>
      <div class="goods-grid">
        <div v-for="item in goodsList" :key="item.id" class="goods-card"
             @click="router.push(`/goods/${item.id}`)">
          <el-image :src="getFirstImage(item)" fit="cover" class="goods-img">
            <template #error><div class="img-placeholder">图</div></template>
          </el-image>
          <div class="goods-body">
            <div class="goods-title">{{ item.title }}</div>
            <div class="goods-price">¥{{ item.price }}</div>
          </div>
        </div>
      </div>
      <el-empty v-if="goodsList.length === 0" description="暂无商品" />
      <div v-if="total > pageSize" class="pagination">
        <el-pagination background layout="prev, pager, next"
                       :current-page="page" :page-size="pageSize" :total="total"
                       @current-change="p => { page = p; loadGoods() }" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { followUser, unfollowUser, checkFollow } from '@/api/follow'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const userId = Number(route.params.userId)
const loading = ref(false)
const seller = ref({})
const stats = ref({})
const goodsList = ref([])
const isFollowing = ref(false)
const page = ref(1)
const pageSize = 20
const total = ref(0)

const isSelf = computed(() => {
  const myId = Number(localStorage.getItem('userId') || 0)
  return userId === myId
})

function getFirstImage(item) {
  try {
    const imgs = typeof item.images === 'string' ? JSON.parse(item.images) : item.images
    return imgs?.[0] || ''
  } catch { return '' }
}

async function loadData() {
  loading.value = true
  try {
    const [userRes, statsRes] = await Promise.all([
      request.get(`/api/user/${userId}`),
      request.get(`/api/reviews/user/${userId}/stats`)
    ])
    seller.value = userRes.data || {}
    stats.value = statsRes.data || {}
    if (!isSelf.value) {
      const followRes = await checkFollow(userId)
      isFollowing.value = followRes.data || false
    }
    await loadGoods()
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function loadGoods() {
  const res = await request.get('/api/goods', { params: { userId, page: page.value, size: pageSize } })
  goodsList.value = res.data?.records || []
  total.value = res.data?.total || 0
}

async function handleFollow() {
  try {
    if (isFollowing.value) {
      await unfollowUser(userId)
      isFollowing.value = false
      ElMessage.success('已取消关注')
    } else {
      await followUser(userId)
      isFollowing.value = true
      ElMessage.success('已关注')
    }
  } catch { /* ignore */ }
}

onMounted(loadData)
</script>

<style scoped>
.seller-header {
  display: flex;
  gap: 16px;
  align-items: center;
}

.seller-avatar {
  background: linear-gradient(135deg, #5B8FF9, #6366F1);
  color: #fff;
  font-size: 24px;
  font-weight: 600;
}

.seller-info {
  flex: 1;
}

.seller-name {
  font-size: 18px;
  font-weight: 600;
  color: #1D2129;
  margin-bottom: 6px;
}

.seller-stats {
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: #86909C;
  margin-bottom: 8px;
}

.seller-actions {
  margin-top: 4px;
}

.goods-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}

.goods-card {
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: transform 0.25s, box-shadow 0.25s;
}

.goods-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(91, 143, 249, 0.12);
}

.goods-img {
  width: 100%;
  aspect-ratio: 1 / 1;
}

.img-placeholder {
  width: 100%;
  aspect-ratio: 1 / 1;
  background: #F2F3F5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #C9CDD4;
}

.goods-body {
  padding: 8px 10px;
}

.goods-title {
  font-size: 13px;
  color: #1D2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.goods-price {
  font-size: 16px;
  font-weight: 700;
  color: #F56C6C;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
