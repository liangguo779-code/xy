<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>用户管理</span>
          <el-input v-model="keyword" placeholder="搜索用户" style="width: 200px"
                    clearable @keyup.enter="loadData" />
        </div>
      </template>
      <el-table :data="list" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 1 ? 'danger' : row.role === 2 ? 'warning' : ''" size="small">
              {{ row.role === 1 ? '管理员' : row.role === 2 ? '交付员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button text :type="row.status === 1 ? 'danger' : 'success'" size="small"
                       @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
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
import { getUsers, updateUserStatus } from '@/api/admin'
import { ElMessage } from 'element-plus'

const list = ref([])
const keyword = ref('')
const page = ref(1)
const total = ref(0)

async function loadData() {
  const res = await getUsers({ keyword: keyword.value, page: page.value, size: 20 })
  list.value = res.data?.records || []
  total.value = res.data?.total || 0
}

async function toggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  await updateUserStatus(row.id, newStatus)
  ElMessage.success('操作成功')
  loadData()
}

onMounted(loadData)
</script>
