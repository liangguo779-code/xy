<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="brand">
        <h1>登录</h1>
        <p>使用你的校园平台账号</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @submit.prevent="handleLogin">
        <div class="floating-label" :class="{ active: form.username, focused: usernameFocused }">
          <label>邮箱</label>
          <el-input
            v-model="form.username" size="large" autocomplete="username"
            @focus="usernameFocused = true" @blur="usernameFocused = false"
          />
        </div>

        <div class="floating-label" :class="{ active: form.password, focused: passwordFocused }">
          <label>密码</label>
          <el-input
            v-model="form.password" type="password" size="large" show-password autocomplete="current-password"
            @focus="passwordFocused = true" @blur="passwordFocused = false"
          />
        </div>

        <div class="form-links">
          <router-link to="/forgot-password">忘记密码？</router-link>
        </div>

        <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
      </el-form>

      <div class="bottom-link">
        还没有账号？<router-link to="/register">创建账号</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const form = ref({ username: '', password: '' })
const usernameFocused = ref(false)
const passwordFocused = ref(false)

const rules = {
  username: [{ required: true, message: '请输入邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    await userStore.login(form.value)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    // handled
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f8f9fa;
  padding: 20px;
}

.auth-card {
  width: 440px;
  background: #fff;
  border-radius: 28px;
  padding: 48px 40px 36px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04), 0 8px 32px rgba(0,0,0,0.08);
}

.brand {
  text-align: center;
  margin-bottom: 36px;
}

.brand h1 {
  margin: 0 0 8px;
  font-size: 26px;
  font-weight: 600;
  color: #1a1a2e;
  letter-spacing: -0.5px;
}

.brand p {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
}

.floating-label {
  position: relative;
  margin-bottom: 20px;
}

.floating-label label {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 14px;
  color: #9ca3af;
  pointer-events: none;
  transition: all 0.2s ease;
  background: #fff;
  padding: 0 4px;
  z-index: 1;
}

.floating-label.active label,
.floating-label.focused label {
  top: 0;
  font-size: 12px;
  color: #4285f4;
}

.floating-label :deep(.el-input__wrapper) {
  border-radius: 12px;
  padding: 4px 16px;
  box-shadow: 0 0 0 1.5px #e5e7eb;
  transition: box-shadow 0.2s;
}

.floating-label :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1.5px #d1d5db;
}

.floating-label :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #4285f4;
}

.floating-label :deep(.el-input__inner) {
  font-size: 14px;
  height: 22px;
}

.form-links {
  text-align: right;
  margin-bottom: 24px;
}

.form-links a {
  font-size: 13px;
  color: #4285f4;
  text-decoration: none;
  font-weight: 500;
}

.form-links a:hover {
  text-decoration: underline;
}

.submit-btn {
  width: 100%;
  height: 48px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 12px;
  background: linear-gradient(135deg, #4285f4, #6366f1);
  border: none;
  letter-spacing: 1px;
  transition: all 0.2s;
}

.submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(66, 133, 244, 0.35);
}

.bottom-link {
  text-align: center;
  margin-top: 24px;
  font-size: 13px;
  color: #6b7280;
}

.bottom-link a {
  color: #4285f4;
  font-weight: 500;
  text-decoration: none;
}

.bottom-link a:hover {
  text-decoration: underline;
}
</style>
