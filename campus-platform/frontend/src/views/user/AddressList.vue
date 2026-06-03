<template>
  <div class="address-list">
    <div class="page-header">
      <h2>收货地址</h2>
      <el-button type="primary" @click="openForm()">
        <el-icon><Plus /></el-icon>新增地址
      </el-button>
    </div>

    <div v-if="addresses.length === 0">
      <el-empty description="暂无收货地址，请添加" />
    </div>

    <div v-for="addr in addresses" :key="addr.id" class="address-card">
      <el-card shadow="hover">
        <div class="address-info">
          <div class="top">
            <span class="name">{{ addr.contactName }}</span>
            <span class="phone">{{ addr.phone }}</span>
            <el-tag v-if="addr.isDefault" type="danger" size="small">默认</el-tag>
          </div>
          <div class="detail">
            {{ addr.building }} {{ addr.detail }}
          </div>
        </div>
        <div class="actions">
          <el-button text type="primary" @click="openForm(addr)">编辑</el-button>
          <el-button text type="primary" @click="handleSetDefault(addr)"
                     v-if="!addr.isDefault">设为默认</el-button>
          <el-popconfirm title="确定删除此地址？" @confirm="handleDelete(addr.id)">
            <template #reference>
              <el-button text type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </div>
      </el-card>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="showForm" :title="editingId ? '编辑地址' : '新增地址'" width="480">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="form.contactName" placeholder="请输入联系人姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="楼栋" prop="building">
          <el-input v-model="form.building" placeholder="如：1号宿舍楼" />
        </el-form-item>
        <el-form-item label="详细地址" prop="detail">
          <el-input v-model="form.detail" placeholder="如：305室" />
        </el-form-item>
        <el-form-item label="默认地址">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAddresses, createAddress, updateAddress, deleteAddress
} from '@/api/address'

const addresses = ref([])
const showForm = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref()

const form = ref({
  contactName: '',
  phone: '',
  building: '',
  detail: '',
  isDefault: 0
})

const rules = {
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  building: [{ required: true, message: '请输入楼栋', trigger: 'blur' }],
  detail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
}

async function loadAddresses() {
  const res = await getAddresses()
  addresses.value = res.data || []
}

function openForm(addr) {
  if (addr) {
    editingId.value = addr.id
    form.value = { ...addr }
  } else {
    editingId.value = null
    form.value = { contactName: '', phone: '', building: '', detail: '', isDefault: 0 }
  }
  showForm.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (editingId.value) {
      await updateAddress({ ...form.value, id: editingId.value })
      ElMessage.success('修改成功')
    } else {
      await createAddress(form.value)
      ElMessage.success('添加成功')
    }
    showForm.value = false
    loadAddresses()
  } catch (e) {
    // handled
  } finally {
    submitting.value = false
  }
}

async function handleSetDefault(addr) {
  await updateAddress({ ...addr, isDefault: 1 })
  ElMessage.success('已设为默认')
  loadAddresses()
}

async function handleDelete(id) {
  await deleteAddress(id)
  ElMessage.success('已删除')
  loadAddresses()
}

onMounted(loadAddresses)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.address-card {
  margin-bottom: 12px;
}

.address-card .el-card {
  display: flex;
}

.address-info {
  flex: 1;
}

.top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.name {
  font-weight: 500;
  font-size: 15px;
}

.phone {
  color: #606266;
}

.detail {
  color: #909399;
  font-size: 14px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
