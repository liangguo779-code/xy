<template>
  <div class="login-container">
    <!-- 装饰圆点 -->
    <div class="decoration">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
      <div class="circle circle-4"></div>
    </div>

    <el-card class="login-card">
      <div class="card-header">
        <div class="brand-icon">🎓</div>
        <h2 class="title">欢迎回来</h2>
        <p class="subtitle">登录校园生态平台</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" size="large" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
        <div class="footer">
          <div>还没有账号？<router-link to="/register">立即注册</router-link></div>
          <div style="margin-top: 8px"><router-link to="/forgot-password">忘记密码？</router-link></div>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const form = ref({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
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
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
}

.decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.15;
  animation: float 8s ease-in-out infinite;
}

.circle-1 {
  width: 200px;
  height: 200px;
  background: #fff;
  top: 10%;
  left: 10%;
  animation-delay: 0s;
}

.circle-2 {
  width: 120px;
  height: 120px;
  background: #5B8FF9;
  top: 60%;
  right: 15%;
  animation-delay: -2s;
}

.circle-3 {
  width: 80px;
  height: 80px;
  background: #F5A623;
  bottom: 20%;
  left: 20%;
  animation-delay: -4s;
}

.circle-4 {
  width: 150px;
  height: 150px;
  background: #fff;
  top: 20%;
  right: 20%;
  animation-delay: -6s;
}

@keyframes float {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  33% { transform: translateY(-20px) rotate(5deg); }
  66% { transform: translateY(10px) rotate(-3deg); }
}

.login-card {
  width: 420px;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  position: relative;
  z-index: 1;
  overflow: visible;
}

.login-card :deep(.el-card__body) {
  padding: 32px;
}

.card-header {
  text-align: center;
  margin-bottom: 28px;
}

.brand-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.title {
  margin: 0 0 4px;
  font-size: 24px;
  font-weight: 700;
  color: #1D2129;
}

.subtitle {
  margin: 0;
  font-size: 14px;
  color: #86909C;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 10px;
  background: linear-gradient(135deg, #5B8FF9, #6366F1);
  border: none;
}

.login-btn:hover {
  opacity: 0.9;
}

.footer {
  text-align: center;
  color: #86909C;
  font-size: 13px;
}

.footer a {
  color: #5B8FF9;
  font-weight: 500;
}
</style>
