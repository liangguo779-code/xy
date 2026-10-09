<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <span>商品管理</span>
          <el-button type="primary" :loading="reindexing" @click="handleReindex">同步搜索索引</el-button>
        </div>
      </template>
      <!-- 商品列表：状态 0-在售 1-下架 2-已售 3-待审（后端兼表已预订） -->
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
        <!-- 操作列：仅待审(3)可审核，仅在售(0)可强制下架 -->
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
/**
 * 商品审核与管理：分页列表，支持审核通过/拒绝、强制下架及搜索索引同步
 * 商品状态 0-在售 1-下架 2-已售 3-待审，仅 3 显示审核按钮，仅 0 显示下架按钮
 */
import { ref, onMounted } from 'vue'
import { getAdminGoods, approveGoods, rejectGoods, forceOffGoods, reindexGoods } from '@/api/admin'
import { ElMessage } from 'element-plus'

const list = ref([])
const page = ref(1)
const total = ref(0)
const reindexing = ref(false)

/** 加载商品分页列表，固定每页 20 条 */
async function loadData() {
  const res = await getAdminGoods({ page: page.value, size: 20 })
  list.value = res.data?.records || []
  total.value = res.data?.total || 0
}

/** 审核通过，后端限定仅状态 3 可通过，且会拦截有进行中订单的已预订商品 */
async function handleApprove(id) { await approveGoods(id); ElMessage.success('已通过'); loadData() }
/** 拒绝审核，后端将商品置为下架并记录拒绝原因 */
async function handleReject(id) { await rejectGoods(id); ElMessage.success('已拒绝'); loadData() }
/** 强制下架在售商品，后端同步把它从搜索索引移除 */
async function handleForceOff(id) { await forceOffGoods(id); ElMessage.success('已下架'); loadData() }

/** 全量重建 ES 商品索引，耗时较久故加 loading，失败常见于 ES 未启动 */
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
