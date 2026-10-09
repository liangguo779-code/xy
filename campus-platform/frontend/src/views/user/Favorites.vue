<template>
  <div>
    <h2>我的收藏</h2>
    <!-- 收藏商品网格，点击卡片跳转商品详情 -->
    <div class="fav-grid" v-if="list.length">
      <div v-for="item in list" :key="item.id" class="fav-card" @click="router.push(`/goods/${item.id}`)">
        <el-image :src="getFirstImage(item)" fit="cover" style="width: 100%; height: 160px; border-radius: 8px">
          <template #error><div class="img-ph">暂无图片</div></template>
        </el-image>
        <div class="fav-title">{{ item.title }}</div>
        <div class="fav-price">¥{{ item.price }}</div>
      </div>
    </div>
    <el-empty v-else description="还没有收藏商品" />
  </div>
</template>

<script setup>
/** 我的收藏页：拉取当前用户收藏的商品并以卡片网格展示，点击进入商品详情 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyFavorites } from '@/api/favorite'

const router = useRouter()
const list = ref([])

/** 取商品首图；images 字段兼容 JSON 字符串与数组两种存法，解析失败或无图时返回空串走占位图 */
function getFirstImage(item) {
  try {
    const imgs = typeof item.images === 'string' ? JSON.parse(item.images) : item.images
    return imgs?.[0] || ''
  } catch { return '' }
}

onMounted(async () => {
  const res = await getMyFavorites()
  list.value = res.data || []
})
</script>

<style scoped>
.fav-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 12px; }
.fav-card { cursor: pointer; }
.fav-title { font-size: 13px; margin-top: 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fav-price { color: #f56c6c; font-weight: 700; margin-top: 4px; }
.img-ph { width: 100%; height: 160px; background: #f5f7fa; display: flex; align-items: center; justify-content: center; color: #c0c4cc; border-radius: 8px; }
</style>
