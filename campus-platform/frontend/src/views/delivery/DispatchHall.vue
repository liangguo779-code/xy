<template>
  <div class="dispatch-hall">
    <div class="page-header">
      <h2>派单大厅</h2>
      <el-radio-group v-model="tab" @change="loadData">
        <el-radio-button label="pending">待接单</el-radio-button>
        <el-radio-button label="mine">我的工单</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 待接单列表 -->
    <div v-if="tab === 'pending'">
      <div v-if="pendingList.length === 0">
        <el-empty description="暂无待接工单" />
      </div>
      <div v-for="item in pendingList" :key="item.id" class="order-card">
        <el-card shadow="hover">
          <div class="order-info">
            <div class="order-row">
              <span class="label">工单号</span>
              <span>{{ item.orderNo || item.id }}</span>
            </div>
            <div class="order-row">
              <span class="label">取货地址</span>
              <span>{{ item.sellerAddr || '待确认' }}</span>
            </div>
            <div class="order-row">
              <span class="label">送达地址</span>
              <span>{{ item.buyerAddr }}</span>
            </div>
            <div class="order-row">
              <span class="label">创建时间</span>
              <span>{{ item.createTime }}</span>
            </div>
          </div>
          <div class="order-actions">
            <el-button type="primary" @click="handleAccept(item.id)">抢单</el-button>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 我的工单 -->
    <div v-if="tab === 'mine'">
      <div v-if="myList.length === 0">
        <el-empty description="暂无工单" />
      </div>
      <div v-for="item in myList" :key="item.id" class="order-card">
        <el-card shadow="hover">
          <div class="order-info">
            <div class="order-row">
              <span class="label">工单号</span>
              <span>{{ item.orderNo || item.id }}</span>
            </div>
            <div class="order-row">
              <span class="label">状态</span>
              <el-tag :type="statusType(item.status)">{{ item.statusDesc }}</el-tag>
            </div>
            <div class="order-row">
              <span class="label">取货地址</span>
              <span>{{ item.sellerAddr || '待确认' }}</span>
            </div>
            <div class="order-row">
              <span class="label">送达地址</span>
              <span>{{ item.buyerAddr }}</span>
            </div>
          </div>
          <div class="order-actions">
            <el-button v-if="item.status === 1" type="warning" @click="handlePickup(item.id)">
              取货
            </el-button>
            <el-button v-if="item.status === 2" type="success" @click="handleDeliver(item.id)">
              送达
            </el-button>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPendingOrders, getMyDeliveries, acceptOrder, pickupGoods, deliverGoods } from '@/api/delivery'

const tab = ref('pending')
const pendingList = ref([])
const myList = ref([])

const statusType = (s) => ({ 0: 'info', 1: 'warning', 2: 'primary', 3: 'success' }[s] || 'info')

async function loadData() {
  if (tab.value === 'pending') {
    const res = await getPendingOrders()
    pendingList.value = res.data || []
  } else {
    const res = await getMyDeliveries()
    myList.value = res.data || []
  }
}

async function handleAccept(id) {
  await acceptOrder(id)
  ElMessage.success('抢单成功！')
  loadData()
}

async function handlePickup(id) {
  await pickupGoods(id, '')
  ElMessage.success('已确认取货')
  loadData()
}

async function handleDeliver(id) {
  await deliverGoods(id, '')
  ElMessage.success('已确认送达')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.order-card {
  margin-bottom: 12px;
}

.order-info {
  margin-bottom: 12px;
}

.order-row {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 14px;
}

.order-row .label {
  color: #909399;
  min-width: 70px;
}

.order-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
