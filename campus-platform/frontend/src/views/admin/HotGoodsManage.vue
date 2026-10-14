<template>
  <div>
    <!-- 热卖商品管理：管理员发布（免审核即上架）、库存管理与热卖订单监控 -->
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <span>热卖商品管理</span>
          <el-button type="primary" @click="openPublish">
            <el-icon><Plus /></el-icon>&nbsp;发布热卖商品
          </el-button>
        </div>
      </template>

      <el-alert type="info" :closable="false" style="margin-bottom: 12px"
                title="热卖商品仅管理员发布，发布即上架免审核。用户下单后扣库存，60 秒内未确认自动释放；确认后进入派单大厅。" />

      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column prop="price" label="售价" width="90">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column label="可售库存" width="90">
          <template #default="{ row }">
            <span :style="{ color: row.stock <= 0 ? '#F56C6C' : '#1D2129', fontWeight: 600 }">{{ row.stock }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sales" label="已售" width="70" />
        <el-table-column label="限购" width="70">
          <template #default="{ row }">{{ row.purchaseLimit > 0 ? row.purchaseLimit + '件' : '不限' }}</template>
        </el-table-column>
        <el-table-column prop="location" label="发货点" min-width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small">
              {{ row.status === 0 ? '在售' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="发布时间" width="160" />
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openStock(row)">调库存</el-button>
            <el-button text type="info" size="small" @click="openOverview(row)">库存概览</el-button>
            <el-button v-if="row.status === 0" text type="danger" size="small"
                       @click="handleOff(row)">下架</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top: 16px" :current-page="page" :page-size="20"
                     :total="total" @current-change="p => { page = p; loadGoods() }" />
    </el-card>

    <!-- 热卖订单监控：默认看全部，可筛 status=10 观察待确认与超时释放 -->
    <el-card style="margin-top: 16px">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <span>热卖订单监控</span>
          <el-radio-group v-model="orderStatus" @change="loadOrders" size="small">
            <el-radio-button :value="undefined">全部</el-radio-button>
            <el-radio-button :value="10">待确认</el-radio-button>
            <el-radio-button :value="11">处理中</el-radio-button>
            <el-radio-button :value="6">待派单</el-radio-button>
            <el-radio-button :value="3">已完成</el-radio-button>
            <el-radio-button :value="4">已取消</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <el-table :data="orders" stripe v-loading="ordersLoading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderNo" label="订单号" min-width="180" />
        <el-table-column prop="goodsId" label="商品ID" width="80" />
        <el-table-column prop="buyerId" label="买家ID" width="80" />
        <el-table-column prop="quantity" label="数量" width="70" />
        <el-table-column prop="goodsAmount" label="金额" width="90">
          <template #default="{ row }">¥{{ row.goodsAmount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="orderStatusType(row.status)" size="small">{{ orderStatusDesc(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认截止" width="160">
          <template #default="{ row }">{{ row.expireTime || '—' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
      </el-table>
      <el-pagination style="margin-top: 16px" :current-page="orderPage" :page-size="20"
                     :total="orderTotal" @current-change="p => { orderPage = p; loadOrders() }" />
    </el-card>

    <!-- 发布热卖商品弹窗 -->
    <el-dialog v-model="showPublish" title="发布热卖商品" width="560" :close-on-click-modal="false">
      <el-form ref="publishRef" :model="publishForm" :rules="publishRules" label-width="90px">
        <el-form-item label="图片" prop="images">
          <el-upload action="/api/upload/image" :headers="uploadHeaders"
                     list-type="picture-card" :limit="9" :on-success="handleUploadSuccess"
                     :on-remove="handleUploadRemove">
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="publishForm.title" placeholder="热卖商品标题" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="publishForm.description" type="textarea" :rows="3"
                    placeholder="商品描述" maxlength="500" show-word-limit />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="售价" prop="price">
              <el-input-number v-model="publishForm.price" :min="0.01" :precision="2" style="width: 100%" />
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
              <el-select v-model="publishForm.condition" style="width: 100%">
                <el-option label="全新" value="全新" />
                <el-option label="几乎全新" value="几乎全新" />
                <el-option label="良好" value="良好" />
                <el-option label="一般" value="一般" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="发货点" prop="location">
          <el-input v-model="publishForm.location" placeholder="配送取货地点，如：一食堂东门" maxlength="100" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="初始库存" prop="stock">
              <el-input-number v-model="publishForm.stock" :min="0" :precision="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="限购数量">
              <el-input-number v-model="publishForm.purchaseLimit" :min="0" :precision="0" style="width: 100%" />
              <div class="form-tip">0 表示不限购</div>
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
      </el-form>
      <template #footer>
        <el-button @click="showPublish = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="handlePublish">发布</el-button>
      </template>
    </el-dialog>

    <!-- 调整库存弹窗：正数补货、负数盘亏 -->
    <el-dialog v-model="showStock" title="调整库存" width="360">
      <p style="margin-bottom: 12px">
        「{{ stockTarget?.title }}」当前可售 {{ stockTarget?.stock }}
      </p>
      <el-input-number v-model="stockDelta" :precision="0" style="width: 100%"
                       placeholder="正数补货 / 负数盘亏" />
      <template #footer>
        <el-button @click="showStock = false">取消</el-button>
        <el-button type="primary" :loading="stockSaving" @click="handleAdjustStock">确定</el-button>
      </template>
    </el-dialog>

    <!-- 库存概览弹窗：DB/Redis 对账观测 -->
    <el-dialog v-model="showOverview" title="库存概览" width="400">
      <el-descriptions v-if="overview" :column="1" border>
        <el-descriptions-item label="DB 可售库存">{{ overview.dbStock }}</el-descriptions-item>
        <el-descriptions-item label="Redis 可售库存">
          {{ overview.redisStock === null ? 'key 缺失（对账任务会补建）' : overview.redisStock }}
        </el-descriptions-item>
        <el-descriptions-item label="已售">{{ overview.sales }}</el-descriptions-item>
        <el-descriptions-item label="待确认订单数">{{ overview.pendingConfirmOrders }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="showOverview = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 热卖商品管理：管理员发布热卖商品（免审核即上架）、调整库存/下架、库存对账概览、热卖订单监控
 * 发布即写 Redis 库存计数，用户下单由 Lua 脚本原子扣减
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  publishHotGoods, adjustHotStock, offHotGoods, getHotGoods, getHotOrders, getHotTickets, getHotStockOverview
} from '@/api/admin'
import { getCategories } from '@/api/category'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const orders = ref([])
const ordersLoading = ref(false)
const orderPage = ref(1)
const orderTotal = ref(0)
const orderStatus = ref(undefined)
const categories = ref([])

const showPublish = ref(false)
const publishing = ref(false)
const publishRef = ref(null)
const showStock = ref(false)
const stockSaving = ref(false)
const stockTarget = ref(null)
const stockDelta = ref(0)
const showOverview = ref(false)
const overview = ref(null)

/** 上传组件鉴权头，与商品发布同约定 */
const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token')}` }

/** 发布表单：images 提交前序列化为 JSON 串；purchaseLimit 0 表示不限购 */
const publishForm = reactive({
  title: '',
  description: '',
  price: 0,
  originalPrice: null,
  categoryId: null,
  condition: '全新',
  location: '',
  stock: 0,
  purchaseLimit: 0,
  images: [],
  videoUrl: ''
})

const publishRules = {
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  price: [{ required: true, message: '请填写售价', trigger: 'blur' }],
  location: [{ required: true, message: '请填写发货点', trigger: 'blur' }],
  stock: [{ required: true, message: '请填写初始库存', trigger: 'blur' }]
}

/** 热卖订单状态文案（与后端 OrderStatus 一致） */
const orderStatusDesc = (s) => ({
  0: '待卖家确认', 1: '待核销', 3: '已完成', 4: '已取消',
  5: '协商跑腿费中', 6: '待派单', 7: '已派单', 8: '配送中',
  9: '待确认收货', 10: '待用户确认', 11: '处理中'
}[s] || '未知')

const orderStatusType = (s) => {
  if (s === 3 || s === 9) return 'success'
  if (s === 4) return 'danger'
  if (s === 10 || s === 5) return 'warning'
  return 'info'
}

/** 加载热卖商品列表（后端已把可售库存覆盖为 Redis 计数） */
async function loadGoods() {
  loading.value = true
  try {
    const res = await getHotGoods({ page: page.value, size: 20 })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

/** 票据 VO → 与 DB 订单行同构（合并展示用） */
function normalizeTicket(t) {
  return {
    id: null,
    orderNo: t.orderNo,
    goodsId: t.goodsId,
    buyerId: t.buyerId,
    quantity: t.qty,
    goodsAmount: t.goodsAmount,
    status: t.status,
    expireTime: t.expireAt ? new Date(t.expireAt).toLocaleString() : '—',
    createTime: t.createdAt ? new Date(t.createdAt).toLocaleString() : '—'
  }
}

/** 加载热卖订单监控列表（合并 Redis 票据：订单尚未落库的在途单，票据排前） */
async function loadOrders() {
  ordersLoading.value = true
  try {
    const params = { page: orderPage.value, size: 20 }
    if (orderStatus.value !== undefined) params.status = orderStatus.value
    const res = await getHotOrders(params)
    let rows = res.data?.records || []

    // 票据合并：仅第一页、且状态筛选为空或命中票据阶段时展示
    if (orderPage.value === 1
        && (orderStatus.value === undefined || orderStatus.value === 10 || orderStatus.value === 11)) {
      try {
        const ticketRes = await getHotTickets()
        let tickets = (ticketRes.data || []).map(normalizeTicket)
        if (orderStatus.value === 10) tickets = tickets.filter(t => t.status === 10)
        if (orderStatus.value === 11) tickets = tickets.filter(t => t.status === 11)
        rows = [...tickets, ...rows]
      } catch { /* 票据接口失败不影响 DB 订单列表 */ }
    }
    orders.value = rows
    orderTotal.value = res.data?.total || 0
  } finally {
    ordersLoading.value = false
  }
}

/** 打开发布弹窗并重置表单 */
function openPublish() {
  Object.assign(publishForm, {
    title: '', description: '', price: 0, originalPrice: null, categoryId: null,
    condition: '全新', location: '', stock: 0, purchaseLimit: 0, images: [], videoUrl: ''
  })
  showPublish.value = true
}

/** 图片上传成功：收集 URL，提交时序列化为 JSON */
function handleUploadSuccess(res) {
  if (res.code === 200) publishForm.images.push(res.data.url)
}

function handleUploadRemove(file) {
  const url = file.response?.data?.url || file.url
  const idx = publishForm.images.indexOf(url)
  if (idx > -1) publishForm.images.splice(idx, 1)
}

function handleVideoSuccess(res) {
  if (res.code === 200) publishForm.videoUrl = res.data.url
}

/** 提交发布：images 序列化为 JSON 串、category 冗余存分类名 */
async function handlePublish() {
  const valid = await publishRef.value.validate().catch(() => false)
  if (!valid) return
  publishing.value = true
  try {
    const cat = categories.value.find(c => c.id === publishForm.categoryId)
    await publishHotGoods({
      ...publishForm,
      category: cat?.name || '',
      purchaseLimit: publishForm.purchaseLimit > 0 ? publishForm.purchaseLimit : null,
      images: JSON.stringify(publishForm.images)
    })
    ElMessage.success('发布成功，已上架')
    showPublish.value = false
    loadGoods()
  } finally {
    publishing.value = false
  }
}

/** 打开调库存弹窗 */
function openStock(row) {
  stockTarget.value = row
  stockDelta.value = 0
  showStock.value = true
}

/** 调整库存：正数补货/负数盘亏，后端 DB+Redis 同步 */
async function handleAdjustStock() {
  if (!stockDelta.value) {
    ElMessage.warning('请输入调整数量')
    return
  }
  stockSaving.value = true
  try {
    await adjustHotStock(stockTarget.value.id, stockDelta.value)
    ElMessage.success('库存已更新')
    showStock.value = false
    loadGoods()
  } finally {
    stockSaving.value = false
  }
}

/** 打开库存概览（DB/Redis 对账） */
async function openOverview(row) {
  const res = await getHotStockOverview(row.id)
  overview.value = res.data
  showOverview.value = true
}

/** 下架热卖商品：拒新单，在途订单继续履约 */
async function handleOff(row) {
  await ElMessageBox.confirm(`确定下架「${row.title}」？下架后不再接受新订单，在途订单继续履约。`, '下架确认', { type: 'warning' })
  await offHotGoods(row.id, '管理员下架')
  ElMessage.success('已下架')
  loadGoods()
}

onMounted(async () => {
  loadGoods()
  loadOrders()
  try {
    const res = await getCategories()
    categories.value = res.data || []
  } catch { /* 分类加载失败不阻断主流程 */ }
})
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
</style>
