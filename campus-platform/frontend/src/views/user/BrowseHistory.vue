<template>
  <div class="browse-history">
    <el-page-header @back="router.back()">
      <template #content>浏览历史</template>
    </el-page-header>

    <!-- 浏览记录商品网格，点击卡片跳转商品详情 -->
    <div class="goods-grid" v-loading="loading">
      <div v-for="item in list" :key="item.id" class="goods-card"
           @click="router.push(`/goods/${item.goodsId}`)">
        <el-image :src="getFirstImage(item)" fit="cover" class="goods-img">
          <template #error><div class="img-placeholder">图</div></template>
        </el-image>
        <div class="goods-body">
          <div class="goods-title">{{ item.title }}</div>
          <div class="goods-price">¥{{ item.price }}</div>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="暂无浏览记录" />

    <!-- 总数超过一页才显示分页 -->
    <div v-if="total > pageSize" class="pagination">
      <el-pagination background layout="prev, pager, next"
                     :current-page="page" :page-size="pageSize" :total="total"
                     @current-change="p => { page = p; loadData() }" />
    </div>
  </div>
</template>

<script setup>
/** 浏览历史页：分页展示当前用户的商品浏览记录，点击卡片进入商品详情 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = 20
const total = ref(0)

/** 取商品首图；images 字段兼容 JSON 字符串与数组两种存法，解析失败或无图时返回空串走占位图 */
function getFirstImage(item) {
  try {
    const imgs = typeof item.images === 'string' ? JSON.parse(item.images) : item.images
    return imgs?.[0] || ''
  } catch { return '' }
}

/** 分页拉取浏览记录，后端返回 records/total 分页结构 */
async function loadData() {
  loading.value = true
  try {
    const res = await request.get('/api/goods/browse-history', { params: { page: page.value, size: pageSize } })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}

onMounted(loadData)
</script>

<style scoped>
.browse-history {
  padding-bottom: 20px;
}

.goods-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
  margin-top: 20px;
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
  margin-top: 20px;
}
</style>
