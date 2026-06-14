<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>论坛管理</span>
          <el-input v-model="keyword" placeholder="搜索帖子..." style="width: 250px"
                    @keyup.enter="loadData" clearable @clear="loadData" />
        </div>
      </template>

      <el-radio-group v-model="statusFilter" @change="loadData" style="margin-bottom: 12px">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="1">正常</el-radio-button>
        <el-radio-button label="0">已隐藏</el-radio-button>
      </el-radio-group>

      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="userId" label="作者ID" width="80" />
        <el-table-column prop="category" label="分类" width="80" />
        <el-table-column label="统计" width="180">
          <template #default="{ row }">
            {{ row.viewCount }}浏览 · {{ row.likeCount }}赞 · {{ row.commentCount }}评
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '已隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="置顶" width="60">
          <template #default="{ row }">
            <el-tag v-if="row.isTop" type="danger" size="small">置顶</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="发布时间" width="160" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="handleToggleTop(row)">
              {{ row.isTop ? '取消置顶' : '置顶' }}
            </el-button>
            <el-button v-if="row.status === 1" text type="warning" size="small" @click="handleHide(row.id)">隐藏</el-button>
            <el-button v-if="row.status === 0" text type="success" size="small" @click="handleRestore(row.id)">恢复</el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const list = ref([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')

async function loadData() {
  loading.value = true
  try {
    const params = { page: page.value, size: 20 }
    if (statusFilter.value !== '') params.status = statusFilter.value
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const res = await request.get('/api/admin/forum/posts', { params })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function handleToggleTop(row) {
  await request.put(`/api/admin/forum/posts/${row.id}/top`)
  ElMessage.success(row.isTop ? '已取消置顶' : '已置顶')
  loadData()
}

async function handleHide(id) {
  await request.put(`/api/admin/forum/posts/${id}/hide`)
  ElMessage.success('已隐藏')
  loadData()
}

async function handleRestore(id) {
  await request.put(`/api/admin/forum/posts/${id}/restore`)
  ElMessage.success('已恢复')
  loadData()
}

async function handleDelete(id) {
  await ElMessageBox.confirm('确定要永久删除这篇帖子吗？', '确认删除', { type: 'warning' })
  await request.delete(`/api/admin/forum/posts/${id}`)
  ElMessage.success('已删除')
  loadData()
}

onMounted(loadData)
</script>
