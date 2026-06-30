<template>
  <div class="goods-page">
    <!-- 顶部搜索栏 -->
    <div class="search-header">
      <el-input v-model="filters.keyword" placeholder="搜索你想要的宝贝..." size="large"
                clearable @keyup.enter="loadGoods" @clear="loadGoods">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <!-- 分类导航 -->
    <div class="category-nav">
      <div class="category-scroll">
        <div v-for="cat in categories" :key="cat.id"
             :class="['cat-item', { active: filters.categoryId === cat.id }]"
             @click="selectCategory(cat.id)">
          <div class="cat-icon">{{ cat.name.charAt(0) }}</div>
          <span>{{ cat.name }}</span>
        </div>
      </div>
    </div>

    <!-- 特色专区 -->
    <div class="special-zones">
      <div class="zone-card" v-for="zone in specialZones" :key="zone.name"
           @click="selectSpecialZone(zone)" :style="{ background: zone.bg }">
        <div class="zone-icon">{{ zone.icon }}</div>
        <div class="zone-info">
          <div class="zone-name">{{ zone.name }}</div>
          <div class="zone-desc">{{ zone.desc }}</div>
        </div>
      </div>
    </div>

    <!-- 为你推荐 -->
    <div v-if="recommendList.length" class="recommend-section">
      <div class="section-title">为你推荐</div>
      <div class="recommend-scroll">
        <div v-for="item in recommendList" :key="item.id" class="recommend-card"
             @click="router.push(`/goods/${item.id}`)">
          <el-image :src="getFirstImage(item)" fit="cover" class="recommend-img">
            <template #error><div class="img-placeholder-sm">图</div></template>
          </el-image>
          <div class="recommend-title">{{ item.title }}</div>
          <div class="recommend-price">¥{{ item.price }}</div>
        </div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-tabs">
        <span :class="{ active: filters.sortBy === '' }" @click="setSort('')">推荐</span>
        <span :class="{ active: filters.sortBy === 'newest' }" @click="setSort('newest')">最新</span>
        <span :class="{ active: filters.sortBy === 'price_asc' }" @click="setSort('price_asc')">价格↑</span>
        <span :class="{ active: filters.sortBy === 'price_desc' }" @click="setSort('price_desc')">价格↓</span>
        <span :class="{ active: filters.sortBy === 'hottest' }" @click="setSort('hottest')">热门</span>
      </div>
      <div class="filter-right">
        <el-dropdown trigger="click" @command="setType">
          <span class="filter-trigger">
            {{ filters.type === 1 ? '求购' : filters.type === 0 ? '出售' : '类型' }} <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :command="null">全部</el-dropdown-item>
              <el-dropdown-item :command="0">出售</el-dropdown-item>
              <el-dropdown-item :command="1">求购</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown trigger="click" @command="setCondition">
          <span class="filter-trigger">
            {{ filters.condition || '成色' }} <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="">全部</el-dropdown-item>
              <el-dropdown-item command="全新">全新</el-dropdown-item>
              <el-dropdown-item command="几乎全新">几乎全新</el-dropdown-item>
              <el-dropdown-item command="良好">良好</el-dropdown-item>
              <el-dropdown-item command="一般">一般</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown trigger="click" @command="setPriceRange">
          <span class="filter-trigger">
            {{ priceLabel }} <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="">不限</el-dropdown-item>
              <el-dropdown-item command="0-50">50以下</el-dropdown-item>
              <el-dropdown-item command="50-200">50-200</el-dropdown-item>
              <el-dropdown-item command="200-500">200-500</el-dropdown-item>
              <el-dropdown-item command="500-99999">500以上</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 瀑布流商品列表 -->
    <div class="waterfall" v-loading="loading">
      <div class="waterfall-col" v-for="col in 4" :key="col">
        <div v-for="item in getColumnItems(col - 1)" :key="item.id"
             class="goods-card" @click="goDetail(item.id)">
          <div class="card-image">
            <el-image :src="getFirstImage(item)" fit="cover" style="width: 100%">
              <template #error>
                <div class="img-placeholder"><el-icon :size="32"><Picture /></el-icon></div>
              </template>
            </el-image>
            <el-tag v-if="item.type === 1" class="want-tag" type="warning" size="small">求购</el-tag>
            <el-tag v-if="item.condition" class="condition-tag" size="small">{{ item.condition }}</el-tag>
          </div>
          <div class="card-body">
            <div class="card-title">{{ item.title }}</div>
            <div class="card-price">
              <span class="price">¥{{ item.price }}</span>
              <span class="original" v-if="item.originalPrice">¥{{ item.originalPrice }}</span>
            </div>
            <div class="card-meta">
              <div class="seller">
                <el-avatar :size="18">{{ item.userId?.toString().slice(-1) }}</el-avatar>
                <span class="seller-name">用户</span>
              </div>
              <span class="want-count">{{ item.wantCount || 0 }}人想要</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && goodsList.length === 0" description="暂无商品" />

    <!-- 悬浮发布按钮 -->
    <div class="fab" @click="showPublish = true">
      <el-icon :size="18"><Plus /></el-icon>
      <span class="fab-text">发布闲置</span>
    </div>

    <!-- 发布商品弹窗 -->
    <el-dialog v-model="showPublish" title="发布商品" width="520" :close-on-click-modal="false">
      <el-form :model="publishForm" label-width="70px">
        <el-form-item label="图片">
          <el-upload action="/api/upload/image" :headers="uploadHeaders"
                     list-type="picture-card" :limit="9" :on-success="handleUploadSuccess"
                     :on-remove="handleUploadRemove">
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="publishForm.title" placeholder="宝贝标题" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="publishForm.description" type="textarea" :rows="3"
                    placeholder="描述宝贝的成色、购买时间、使用情况等" maxlength="500" show-word-limit />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="售价" required>
              <el-input-number v-model="publishForm.price" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原价">
              <el-input-number v-model="publishForm.originalPrice" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="publishForm.categoryId" placeholder="选择分类" style="width: 100%">
                <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="成色">
              <el-select v-model="publishForm.condition" placeholder="选择成色" style="width: 100%">
                <el-option label="全新" value="全新" />
                <el-option label="几乎全新" value="几乎全新" />
                <el-option label="良好" value="良好" />
                <el-option label="一般" value="一般" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="视频">
          <el-upload action="/api/upload/video" :headers="uploadHeaders"
                     :limit="1" accept="video/*" :on-success="handleVideoSuccess"
                     :on-remove="() => publishForm.videoUrl = ''">
            <el-button size="small">上传视频</el-button>
            <template #tip><div class="el-upload__tip">mp4/mov格式，最大50MB</div></template>
          </el-upload>
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="publishForm.type">
            <el-radio :value="0">出售</el-radio>
            <el-radio :value="1">求购</el-radio>
          </el-radio-group>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getGoodsList, getRecommendGoods, createGoods } from '@/api/goods'
import { getCategories } from '@/api/category'

const router = useRouter()
const loading = ref(false)
const goodsList = ref([])
const recommendList = ref([])
const categories = ref([])
const showPublish = ref(false)
const publishing = ref(false)

const specialZones = [
  { name: '教材专区', icon: '📚', desc: '开学必备教材', bg: 'linear-gradient(135deg, #a8edea, #fed6e3)', categoryName: '教材' },
  { name: '毕业季', icon: '🎓', desc: '毕业生好物转让', bg: 'linear-gradient(135deg, #ffecd2, #fcb69f)', categoryName: '' },
  { name: '数码专区', icon: '📱', desc: '手机电脑配件', bg: 'linear-gradient(135deg, #a1c4fd, #c2e9fb)', categoryName: '数码' }
]

const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token')}` }

const filters = reactive({
  keyword: '',
  categoryId: null,
  type: null,
  minPrice: null,
  maxPrice: null,
  condition: '',
  sortBy: ''
})

const publishForm = reactive({
  title: '',
  description: '',
  price: 0,
  originalPrice: null,
  categoryId: null,
  condition: '良好',
  type: 0,
  images: [],
  videoUrl: ''
})

const priceLabel = computed(() => {
  if (!filters.minPrice && !filters.maxPrice) return '价格'
  if (filters.maxPrice >= 99999) return `${filters.minPrice}以上`
  return `${filters.minPrice || 0}-${filters.maxPrice}`
})

function getFirstImage(item) {
  try {
    const imgs = typeof item.images === 'string' ? JSON.parse(item.images) : item.images
    return imgs?.[0] || ''
  } catch { return '' }
}

function getColumnItems(colIndex) {
  return goodsList.value.filter((_, i) => i % 4 === colIndex)
}

function selectCategory(id) {
  filters.categoryId = filters.categoryId === id ? null : id
  loadGoods()
}

function selectSpecialZone(zone) {
  // 通过分类名找到对应分类ID
  const cat = categories.value.find(c => c.name === zone.categoryName)
  if (cat) {
    filters.categoryId = cat.id
  }
  filters.keyword = zone.categoryName ? '' : zone.name
  loadGoods()
}

function setSort(sort) {
  filters.sortBy = sort
  loadGoods()
}

function setType(val) {
  filters.type = val
  loadGoods()
}

function setCondition(val) {
  filters.condition = val
  loadGoods()
}

function setPriceRange(val) {
  if (!val) {
    filters.minPrice = null
    filters.maxPrice = null
  } else {
    const [min, max] = val.split('-').map(Number)
    filters.minPrice = min
    filters.maxPrice = max
  }
  loadGoods()
}

async function loadGoods() {
  loading.value = true
  try {
    const params = { page: 1, size: 50, ...filters }
    Object.keys(params).forEach(k => { if (params[k] === '' || params[k] === null) delete params[k] })
    const res = await getGoodsList(params)
    goodsList.value = res.data?.records || []
  } finally {
    loading.value = false
  }
}

function handleUploadSuccess(res) {
  if (res.code === 200) {
    publishForm.images.push(res.data.url)
  }
}

function handleVideoSuccess(res) {
  if (res.code === 200) {
    publishForm.videoUrl = res.data.url
  }
}

function handleUploadRemove(file) {
  const url = file.response?.data?.url || file.url
  const idx = publishForm.images.indexOf(url)
  if (idx > -1) publishForm.images.splice(idx, 1)
}

async function handlePublish() {
  if (!publishForm.title || !publishForm.price) {
    ElMessage.warning('请填写标题和价格')
    return
  }
  publishing.value = true
  try {
    const cat = categories.value.find(c => c.id === publishForm.categoryId)
    await createGoods({
      ...publishForm,
      category: cat?.name || '',
      images: JSON.stringify(publishForm.images)
    })
    ElMessage.success('发布成功')
    showPublish.value = false
    Object.assign(publishForm, { title: '', description: '', price: 0, originalPrice: null, categoryId: null, condition: '良好', type: 0, images: [] })
    loadGoods()
  } finally {
    publishing.value = false
  }
}

function goDetail(id) { router.push(`/goods/${id}`) }

async function loadRecommend() {
  try {
    const res = await getRecommendGoods({ page: 1, size: 8 })
    recommendList.value = res.data?.records || []
  } catch { /* ignore */ }
}

onMounted(async () => {
  const catRes = await getCategories()
  categories.value = catRes.data || []
  loadGoods()
  loadRecommend()
})
</script>

<style scoped>
.search-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
  padding: 12px 0;
}

.special-zones {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  overflow-x: auto;
  padding: 2px;
}

.zone-card {
  flex-shrink: 0;
  width: 170px;
  padding: 14px;
  border-radius: 14px;
  cursor: pointer;
  display: flex;
  gap: 10px;
  align-items: center;
  transition: transform 0.25s, box-shadow 0.25s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.zone-card:hover {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1);
}

.zone-icon { font-size: 28px; }
.zone-name { font-size: 14px; font-weight: 600; color: #1D2129; }
.zone-desc { font-size: 11px; color: #86909C; margin-top: 2px; }

.recommend-section {
  margin-bottom: 16px;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #1D2129;
  margin-bottom: 12px;
}

.recommend-scroll {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding: 2px;
  -webkit-overflow-scrolling: touch;
}

.recommend-card {
  flex-shrink: 0;
  width: 140px;
  cursor: pointer;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: transform 0.25s, box-shadow 0.25s;
}

.recommend-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(250, 173, 20, 0.15);
}

.recommend-img {
  width: 140px;
  height: 140px;
}

.img-placeholder-sm {
  width: 140px;
  height: 140px;
  background: #F2F3F5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #C9CDD4;
}

.recommend-title {
  padding: 6px 8px 2px;
  font-size: 12px;
  color: #1D2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend-price {
  padding: 0 8px 8px;
  font-size: 14px;
  font-weight: 700;
  color: #FAAD14;
}

.category-nav {
  overflow-x: auto;
  padding: 8px 0 12px;
  -webkit-overflow-scrolling: touch;
}

.category-scroll {
  display: flex;
  gap: 16px;
  min-width: max-content;
}

.cat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #4E5969;
  font-size: 12px;
  min-width: 52px;
  transition: all 0.25s;
}

.cat-item:hover { color: #FAAD14; }
.cat-item.active { color: #D48806; }

.cat-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: #FFF7E6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 500;
  transition: all 0.25s;
}

.cat-item.active .cat-icon {
  background: linear-gradient(135deg, #FAAD14, #D48806);
  color: #fff;
  box-shadow: 0 4px 12px rgba(250, 173, 20, 0.3);
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  margin-bottom: 8px;
}

.filter-tabs {
  display: flex;
  gap: 4px;
  font-size: 14px;
  color: #86909C;
  background: #F2F3F5;
  border-radius: 8px;
  padding: 3px;
}

.filter-tabs span {
  cursor: pointer;
  padding: 6px 14px;
  border-radius: 6px;
  transition: all 0.2s;
}

.filter-tabs span.active {
  color: #D48806;
  font-weight: 600;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.filter-right {
  display: flex;
  gap: 12px;
}

.filter-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #4E5969;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.2s;
}

.filter-trigger:hover {
  background: #F2F3F5;
}

/* 瀑布流 */
.waterfall {
  display: flex;
  gap: 12px;
}

.waterfall-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.goods-card {
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: transform 0.25s, box-shadow 0.25s;
}

.goods-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(250, 173, 20, 0.15);
}

.card-image {
  position: relative;
  overflow: hidden;
  aspect-ratio: 1 / 1;
}

.card-image .el-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}

.goods-card:hover .card-image .el-image {
  transform: scale(1.05);
}

.img-placeholder {
  width: 100%;
  height: 180px;
  background: #F2F3F5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #C9CDD4;
}

.want-tag { position: absolute; top: 8px; left: 8px; border-radius: 999px; }
.condition-tag {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  border: none;
  color: #fff;
  border-radius: 999px;
  font-size: 11px;
}

.card-body { padding: 10px 12px; }

.card-title {
  font-size: 14px;
  color: #1D2129;
  font-weight: 500;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 6px;
}

.card-price {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 6px;
}

.price { font-size: 18px; font-weight: 700; color: #FAAD14; }
.original { font-size: 11px; color: #C9CDD4; text-decoration: line-through; }

.card-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.seller { display: flex; align-items: center; gap: 4px; }
.seller-name { font-size: 12px; color: #86909C; }
.want-count { font-size: 11px; color: #C9CDD4; }

.fab {
  position: fixed;
  right: 24px;
  bottom: 80px;
  height: 48px;
  padding: 0 20px;
  border-radius: 24px;
  background: linear-gradient(135deg, #FAAD14, #D48806);
  color: #fff;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(250, 173, 20, 0.4);
  transition: transform 0.25s, box-shadow 0.25s;
  z-index: 100;
  font-weight: 600;
}

.fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(250, 173, 20, 0.5);
}

.fab-text {
  font-size: 14px;
  letter-spacing: 0.5px;
}
</style>
