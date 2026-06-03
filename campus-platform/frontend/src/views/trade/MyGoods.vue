<template>
  <div class="my-goods">
    <div class="page-header">
      <h2>我的商品</h2>
      <el-button type="primary" @click="router.push('/goods')">
        <el-icon><Plus /></el-icon> 发布新商品
      </el-button>
    </div>

    <!-- 状态筛选 -->
    <el-radio-group v-model="statusFilter" @change="loadGoods" style="margin-bottom: 16px">
      <el-radio-button label="">全部</el-radio-button>
      <el-radio-button label="0">在售</el-radio-button>
      <el-radio-button label="1">已下架</el-radio-button>
      <el-radio-button label="2">已售出</el-radio-button>
    </el-radio-group>

    <!-- 商品列表 -->
    <div v-if="goodsList.length === 0">
      <el-empty description="暂无商品" />
    </div>

    <div v-for="item in goodsList" :key="item.id" class="goods-item">
      <el-card shadow="hover">
        <div class="goods-content">
          <el-image :src="getFirstImage(item)" fit="cover"
                    style="width: 100px; height: 100px; border-radius: 8px; flex-shrink: 0">
            <template #error>
              <div class="img-placeholder">图</div>
            </template>
          </el-image>
          <div class="goods-info">
            <div class="goods-title">{{ item.title }}</div>
            <div class="goods-meta">
              <span class="price">¥{{ item.price }}</span>
              <el-tag :type="statusType(item.status)" size="small">{{ statusText(item.status) }}</el-tag>
              <span class="count">{{ item.viewCount }}浏览 · {{ item.wantCount }}想要</span>
            </div>
            <div class="goods-time">发布于 {{ item.createTime }}</div>
            <div v-if="item.offReason" class="off-reason">
              <el-tag type="info" size="small">下架原因：{{ item.offReason }}</el-tag>
            </div>
          </div>
          <div class="goods-actions">
            <el-button v-if="item.status === 0" size="small" @click="openEdit(item)">编辑</el-button>
            <el-button v-if="item.status === 0" type="warning" size="small" @click="openOffShelf(item)">下架</el-button>
            <el-button v-if="item.status === 1" type="success" size="small" @click="handleReShelf(item)">重新上架</el-button>
            <el-button size="small" @click="router.push(`/goods/${item.id}`)">查看</el-button>
          </div>
        </div>
      </el-card>
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

    <!-- 下架弹窗 -->
    <el-dialog v-model="showOffShelf" title="下架商品" width="420">
      <p style="margin-bottom: 16px">确定要下架「{{ offShelfItem?.title }}」吗？</p>
      <el-form label-width="80px">
        <el-form-item label="下架原因">
          <el-radio-group v-model="offShelfReason">
            <el-radio value="已卖出">已卖出</el-radio>
            <el-radio value="不想卖了">不想卖了</el-radio>
            <el-radio value="信息有误">信息有误</el-radio>
            <el-radio value="其他">其他</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="offShelfReason === '其他'" label="补充说明">
          <el-input v-model="offShelfRemark" placeholder="请输入下架原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showOffShelf = false">取消</el-button>
        <el-button type="danger" @click="handleOffShelf">确认下架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()
const goodsList = ref([])
const statusFilter = ref('')
const showEdit = ref(false)
const showOffShelf = ref(false)
const editForm = ref({})
const offShelfItem = ref(null)
const offShelfReason = ref('已卖出')
const offShelfRemark = ref('')

const statusText = (s) => ({ 0: '在售', 1: '已下架', 2: '已售出' }[s] || '未知')
const statusType = (s) => ({ 0: 'success', 1: 'info', 2: 'warning' }[s] || '')

function getFirstImage(item) {
  try {
    const imgs = typeof item.images === 'string' ? JSON.parse(item.images) : item.images
    return imgs?.[0] || ''
  } catch { return '' }
}

async function loadGoods() {
  const params = { page: 1, size: 100 }
  if (statusFilter.value !== '') params.status = statusFilter.value
  const res = await request.get('/api/goods/my', { params })
  goodsList.value = res.data?.records || res.data || []
}

function openEdit(item) {
  editForm.value = {
    id: item.id,
    title: item.title,
    description: item.description,
    price: item.price,
    condition: item.condition || '良好'
  }
  showEdit.value = true
}

async function handleSaveEdit() {
  await request.put(`/api/goods/${editForm.value.id}`, editForm.value)
  ElMessage.success('保存成功')
  showEdit.value = false
  loadGoods()
}

function openOffShelf(item) {
  offShelfItem.value = item
  offShelfReason.value = '已卖出'
  offShelfRemark.value = ''
  showOffShelf.value = true
}

async function handleOffShelf() {
  const reason = offShelfReason.value === '其他' ? offShelfRemark.value : offShelfReason.value
  await request.delete(`/api/goods/${offShelfItem.value.id}`, { params: { reason } })
  ElMessage.success('已下架')
  showOffShelf.value = false
  loadGoods()
}

async function handleReShelf(item) {
  await request.put(`/api/goods/${item.id}`, { status: 0 })
  ElMessage.success('已重新上架')
  loadGoods()
}

onMounted(loadGoods)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.goods-item {
  margin-bottom: 12px;
}

.goods-content {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.goods-info {
  flex: 1;
  min-width: 0;
}

.goods-title {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 8px;
}

.goods-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.price {
  font-size: 18px;
  font-weight: 700;
  color: #f56c6c;
}

.count {
  font-size: 12px;
  color: #909399;
}

.goods-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-bottom: 4px;
}

.off-reason {
  margin-top: 4px;
}

.goods-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
}

.img-placeholder {
  width: 100px;
  height: 100px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  border-radius: 8px;
}
</style>
