<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <span>商品管理</span>
          <el-button type="primary" :loading="reindexing" @click="handleReindex">同步搜索索引</el-button>
        </div>
      </template>
      <el-table :data="list" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="price" label="价格" width="80">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="['success','','danger','warning'][row.status]" size="small">
              {{ ['在售','下架','已售','待审'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="发布时间" width="160" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button v-if="row.status === 3" text type="success" size="small" @click="handleApprove(row.id)">通过</el-button>
            <el-button v-if="row.status === 3" text type="danger" size="small" @click="handleReject(row.id)">拒绝</el-button>
            <el-button v-if="row.status === 0" text type="warning" size="small" @click="handleForceOff(row.id)">下架</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top: 16px" :current-page="page" :page-size="20"
                     :total="total" @current-change="p => { page = p; loadData() }" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAdminGoods, approveGoods, rejectGoods, forceOffGoods, reindexGoods } from '@/api/admin'
import { ElMessage } from 'element-plus'

const list = ref([])
const page = ref(1)
const total = ref(0)
const reindexing = ref(false)

async function loadData() {
  const res = await getAdminGoods({ page: page.value, size: 20 })
  list.value = res.data?.records || []
  total.value = res.data?.total || 0
}

async function handleApprove(id) { await approveGoods(id); ElMessage.success('已通过'); loadData() }
async function handleReject(id) { await rejectGoods(id); ElMessage.success('已拒绝'); loadData() }
async function handleForceOff(id) { await forceOffGoods(id); ElMessage.success('已下架'); loadData() }

async function handleReindex() {
  reindexing.value = true
  try {
    await reindexGoods()
    ElMessage.success('搜索索引同步完成')
  } catch {
    ElMessage.error('同步失败，请检查 ES 是否运行')
  } finally {
    reindexing.value = false
  }
}

onMounted(loadData)
</script>
