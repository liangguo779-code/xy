<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>系统配置</span>
          <el-button type="primary" size="small" @click="handleAdd">新增配置</el-button>
        </div>
      </template>
      <!-- 配置列表，后端一次性返回全部，无分页 -->
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

    <!-- 新增/编辑共用弹窗：编辑时配置键只读，键即配置主键 -->
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
/**
 * 系统配置管理：键值配置的查看、新增与编辑，编辑时锁定配置键
 * 本页直接 fetch /api/admin/config，未走 api 封装，需手动携带 Bearer token
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const list = ref([])
const showForm = ref(false)
const editing = ref(false)
const form = ref({ key: '', value: '', description: '' })

/** 手动拼接的鉴权头，fetch 未走统一请求封装，token 需自行携带 */
const headers = { Authorization: `Bearer ${localStorage.getItem('token')}` }

/** 拉取全部配置，后端返回 data.data 为数组且不分页 */
async function loadData() {
  const res = await fetch('/api/admin/config', { headers })
  const data = await res.json()
  list.value = data.data || []
}

/** 新增：清空表单后打开弹窗，与编辑共用同一表单 */
function handleAdd() {
  editing.value = false
  form.value = { key: '', value: '', description: '' }
  showForm.value = true
}

/** 编辑：行字段 configKey/configValue 映射到表单 key/value */
function handleEdit(row) {
  editing.value = true
  form.value = { key: row.configKey, value: row.configValue, description: row.description }
  showForm.value = true
}

/** 保存：新增与编辑同为 PUT，后端按 key 是否存在执行创建或更新 */
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
