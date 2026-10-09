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
      <el-radio-button label="3">已预订</el-radio-button>
      <el-radio-button label="1">已下架</el-radio-button>
      <el-radio-button label="2">已售出</el-radio-button>
    </el-radio-group>

    <!-- 商品列表 -->
    <el-empty v-if="goodsList.length === 0" description="暂无商品" />

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
          <!-- 操作区：仅在售(0)可编辑/擦亮/标记售出/下架，已下架(1)可重新上架 -->
          <div class="goods-actions">
            <el-button v-if="item.status === 0" size="small" @click="openEdit(item)">编辑</el-button>
            <el-button v-if="item.status === 0" size="small" @click="handleRefresh(item)">擦亮</el-button>
            <el-button v-if="item.status === 0" type="success" size="small" @click="handleMarkSold(item)">标记已售</el-button>
            <el-button v-if="item.status === 0" type="warning" size="small" @click="openOffShelf(item)">下架</el-button>
            <el-button v-if="item.status === 1" type="success" size="small" @click="handleReShelf(item)">重新上架</el-button>
            <el-button size="small" @click="router.push(`/goods/${item.id}`)">查看</el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 分页 -->
    <div v-if="total > 20" style="display: flex; justify-content: center; margin-top: 20px">
      <el-pagination background layout="prev, pager, next"
                     :current-page="page" :page-size="20" :total="total"
                     @current-change="p => { page = p; loadGoods() }" />
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
/**
 * 我的商品管理页：按状态筛选自己发布的商品，支持编辑、擦亮、标记售出、下架与重新上架
 */
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

/** 商品状态：0-在售 1-已下架 2-已售出 3-已预订，statusType 同码映射标签色 */
const statusText = (s) => ({ 0: '在售', 1: '已下架', 2: '已售出', 3: '已预订' }[s] || '未知')
const statusType = (s) => ({ 0: 'success', 1: 'info', 2: 'warning', 3: 'primary' }[s] || '')

/** 取商品首图，后端 images 存 JSON 字符串但可能已被解析为数组；解析失败返回空串由占位图兜底 */
function getFirstImage(item) {
  try {
    const imgs = typeof item.images === 'string' ? JSON.parse(item.images) : item.images
    return imgs?.[0] || ''
  } catch { return '' }
}

/** 打开编辑弹窗并回填当前商品，成色缺省为良好 */
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

/** 保存编辑，PUT 整体提交可编辑字段后刷新列表 */
async function handleSaveEdit() {
  await request.put(`/api/goods/${editForm.value.id}`, editForm.value)
  ElMessage.success('保存成功')
  showEdit.value = false
  loadGoods()
}

/** 打开下架弹窗，原因默认已卖出并清空上次的补充说明 */
function openOffShelf(item) {
  offShelfItem.value = item
  offShelfReason.value = '已卖出'
  offShelfRemark.value = ''
  showOffShelf.value = true
}

/** 下架商品，原因作为下架原因存库并展示在列表中 */
async function handleOffShelf() {
  // 选其他时提交补充说明作为原因，其余提交预设原因文案
  const reason = offShelfReason.value === '其他' ? offShelfRemark.value : offShelfReason.value
  await request.delete(`/api/goods/${offShelfItem.value.id}`, { params: { reason } })
  ElMessage.success('已下架')
  showOffShelf.value = false
  loadGoods()
}

/** 重新上架，商品状态回到 0-在售 */
async function handleReShelf(item) {
  await request.put(`/api/goods/${item.id}/reshelf`)
  ElMessage.success('已重新上架')
  loadGoods()
}

/** 标记已售出，商品状态置为 2-已售出 */
async function handleMarkSold(item) {
  await request.put(`/api/goods/${item.id}/sold`)
  ElMessage.success('已标记为已售出')
  loadGoods()
}

/** 擦亮商品刷新发布时间以提升排名 */
async function handleRefresh(item) {
  try {
    await request.put(`/api/goods/${item.id}/refresh`)
    ElMessage.success('擦亮成功，商品排名已提升')
    loadGoods()
  } catch (e) {
    // 后端对擦亮频率有限制，属业务失败用 warning 提示而非报错
    ElMessage.warning(e.response?.data?.message || '擦亮失败')
  }
}

const page = ref(1)
const total = ref(0)

/** 分页加载我的商品列表 */
async function loadGoods() {
  const params = { page: page.value, size: 20 }
  // 筛选值为空串代表全部，此时不传 status 参数
  if (statusFilter.value !== '') params.status = statusFilter.value
  const res = await request.get('/api/goods/my', { params })
  goodsList.value = res.data?.records || res.data || []
  total.value = res.data?.total || 0
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
