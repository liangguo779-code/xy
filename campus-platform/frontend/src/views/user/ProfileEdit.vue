<template>
  <div class="profile-edit">
    <h2>个人信息</h2>

    <el-card>
      <el-form :model="form" label-width="80px" style="max-width: 500px">
        <el-form-item label="头像">
          <el-upload
            class="avatar-uploader"
            action="/api/upload/image"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="handleAvatarSuccess"
          >
            <div class="avatar-wrapper">
              <el-avatar :size="80" :src="form.avatar" class="avatar-img">
                {{ form.nickname?.charAt(0) || 'U' }}
              </el-avatar>
              <div class="avatar-overlay">
                <el-icon :size="20"><Camera /></el-icon>
                <span>更换头像</span>
              </div>
            </div>
          </el-upload>
        </el-form-item>

        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>

        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>

        <el-form-item label="宿舍地址">
          <el-input v-model="form.dormitory" placeholder="如：1号楼305" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSave" :loading="saving">保存修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-top: 20px">
      <template #header>修改密码</template>
      <el-form :model="pwdForm" :rules="pwdRules" ref="pwdFormRef" label-width="80px" style="max-width: 500px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleChangePwd" :loading="changingPwd">修改密码</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-top: 20px; border-color: #f56c6c">
      <template #header><span style="color: #f56c6c">危险操作</span></template>
      <p style="color: #909399; margin-bottom: 16px">注销账号后，您的数据将被清除且无法恢复。</p>
      <el-popconfirm title="确定要注销账号吗？此操作不可逆！" @confirm="handleDeleteAccount">
        <template #reference>
          <el-button type="danger">注销账号</el-button>
        </template>
      </el-popconfirm>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUserInfo } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { Camera } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const saving = ref(false)
const changingPwd = ref(false)
const pwdFormRef = ref()

const form = ref({
  nickname: '',
  avatar: '',
  phone: '',
  dormitory: ''
})

const pwdForm = ref({
  oldPassword: '',
  newPassword: ''
})

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度6-20位', trigger: 'blur' }
  ]
}

const uploadHeaders = {
  Authorization: `Bearer ${localStorage.getItem('token')}`
}

onMounted(async () => {
  const res = await getUserInfo()
  const user = res.data
  form.value = {
    nickname: user.nickname || '',
    avatar: user.avatar || '',
    phone: user.phone || '',
    dormitory: user.dormitory || ''
  }
})

function handleAvatarSuccess(res) {
  if (res.code === 200) {
    form.value.avatar = res.data.url
    ElMessage.success('头像上传成功')
  }
}

async function handleSave() {
  saving.value = true
  try {
    const res = await fetch('/api/user/profile', {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: JSON.stringify(form.value)
    })
    const data = await res.json()
    if (data.code === 200) {
      ElMessage.success('保存成功')
      userStore.fetchUserInfo()
    } else {
      ElMessage.error(data.msg)
    }
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function handleChangePwd() {
  await pwdFormRef.value.validate()
  changingPwd.value = true
  try {
    const res = await fetch('/api/user/password', {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: JSON.stringify(pwdForm.value)
    })
    const data = await res.json()
    if (data.code === 200) {
      ElMessage.success('密码修改成功，请重新登录')
      userStore.logout()
      router.push('/login')
    } else {
      ElMessage.error(data.msg)
    }
  } catch (e) {
    ElMessage.error('修改失败')
  } finally {
    changingPwd.value = false
  }
}

async function handleDeleteAccount() {
  try {
    const res = await fetch('/api/user/account', {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    const data = await res.json()
    if (data.code === 200) {
      ElMessage.success('账号已注销')
      userStore.logout()
      router.push('/login')
    } else {
      ElMessage.error(data.msg)
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}
</script>

<style scoped>
.avatar-uploader {
  text-align: center;
}

.avatar-wrapper {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
}

.avatar-img {
  width: 100%;
  height: 100%;
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: white;
  opacity: 0;
  transition: opacity 0.3s;
  font-size: 12px;
  gap: 4px;
}

.avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}
</style>
