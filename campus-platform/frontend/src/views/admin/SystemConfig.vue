<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>系统配置</span>
          <el-button type="primary" size="small" @click="handleAdd">新增配置</el-button>
        </div>
      </template>
      <el-table :data="list" stripe>
        <el-table-column prop="configKey" label="配置键" width="200" />
        <el-table-column prop="configValue" label="配置值" />
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="updateTime" label="更新时间" width="160" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showForm" :title="editing ? '编辑配置' : '新增配置'" width="420">
      <el-form :model="form" label-width="80px">
        <el-form-item label="配置键">
          <el-input v-model="form.key" :disabled="editing" />
        </el-form-item>
        <el-form-item label="配置值">
          <el-input v-model="form.value" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const list = ref([])
const showForm = ref(false)
const editing = ref(false)
const form = ref({ key: '', value: '', description: '' })

const headers = { Authorization: `Bearer ${localStorage.getItem('token')}` }

async function loadData() {
  const res = await fetch('/api/admin/config', { headers })
  const data = await res.json()
  list.value = data.data || []
}

function handleAdd() {
  editing.value = false
  form.value = { key: '', value: '', description: '' }
  showForm.value = true
}

function handleEdit(row) {
  editing.value = true
  form.value = { key: row.configKey, value: row.configValue, description: row.description }
  showForm.value = true
}

async function handleSave() {
  await fetch('/api/admin/config', {
    method: 'PUT',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify(form.value)
  })
  ElMessage.success('保存成功')
  showForm.value = false
  loadData()
}

onMounted(loadData)
</script>
