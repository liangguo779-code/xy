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
      <el-icon :size="24"><Plus /></el-icon>
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
import { getGoodsList, createGoods } from '@/api/goods'
import { getCategories } from '@/api/category'

const router = useRouter()
const loading = ref(false)
const goodsList = ref([])
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
  const idx = publishForm.images.indexOf(file.url || file.response?.data?.url)
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

onMounted(async () => {
  const catRes = await getCategories()
  categories.value = catRes.data || []
  loadGoods()
})
</script>

<style scoped>
.search-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  padding: 12px 0;
}

.special-zones {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  overflow-x: auto;
}

.zone-card {
  flex-shrink: 0;
  width: 160px;
  padding: 12px;
  border-radius: 12px;
  cursor: pointer;
  display: flex;
  gap: 10px;
  align-items: center;
  transition: transform 0.2s;
}

.zone-card:hover { transform: scale(1.03); }
.zone-icon { font-size: 24px; }
.zone-name { font-size: 14px; font-weight: 600; color: #303133; }
.zone-desc { font-size: 11px; color: #606266; margin-top: 2px; }

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
  gap: 4px;
  cursor: pointer;
  color: #606266;
  font-size: 12px;
  min-width: 48px;
  transition: color 0.2s;
}

.cat-item.active { color: #409eff; }

.cat-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 500;
}

.cat-item.active .cat-icon { background: #ecf5ff; color: #409eff; }

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  margin-bottom: 8px;
}

.filter-tabs {
  display: flex;
  gap: 16px;
  font-size: 14px;
  color: #909399;
}

.filter-tabs span {
  cursor: pointer;
  padding-bottom: 4px;
  transition: all 0.2s;
}

.filter-tabs span.active {
  color: #303133;
  font-weight: 500;
  border-bottom: 2px solid #409eff;
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
  color: #606266;
  cursor: pointer;
}

/* 瀑布流 */
.waterfall {
  display: flex;
  gap: 10px;
}

.waterfall-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.goods-card {
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.goods-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
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
}

.img-placeholder {
  width: 100%;
  height: 180px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
}

.want-tag { position: absolute; top: 8px; left: 8px; }
.condition-tag { position: absolute; bottom: 8px; right: 8px; background: rgba(0,0,0,0.5); border: none; color: #fff; }

.card-body { padding: 8px 10px; }

.card-title {
  font-size: 13px;
  color: #303133;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 4px;
}

.card-price {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 4px;
}

.price { font-size: 16px; font-weight: 700; color: #f56c6c; }
.original { font-size: 11px; color: #c0c4cc; text-decoration: line-through; }

.card-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.seller { display: flex; align-items: center; gap: 4px; }
.seller-name { font-size: 12px; color: #909399; }
.want-count { font-size: 11px; color: #c0c4cc; }

.fab {
  position: fixed;
  right: 24px;
  bottom: 80px;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff6b35, #ff4757);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(255,71,87,0.4);
  transition: transform 0.2s;
  z-index: 100;
}

.fab:hover { transform: scale(1.1); }
</style>
