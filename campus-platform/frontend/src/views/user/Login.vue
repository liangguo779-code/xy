<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="brand">
        <h1>登录</h1>
        <p>使用你的校园平台账号</p>
      </div>

      <!-- 表单区：floating-label 借助 active（有值）与 focused（聚焦）两个类让 label 上浮 -->
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

        <!-- 图形验证码：连续登录失败后由后端要求展示（428），防暴力破解 -->
        <div v-if="showCaptcha" class="captcha-row">
          <el-input v-model="form.captchaAnswer" size="large" placeholder="计算结果" class="captcha-input" />
          <img v-if="captchaImage" :src="captchaImage" class="captcha-img" alt="验证码" title="点击刷新"
               @click="loadCaptcha" />
          <el-button size="large" @click="loadCaptcha">刷新</el-button>
        </div>

        <div class="form-links">
          <el-checkbox v-model="form.rememberMe">记住我（30 天免登录）</el-checkbox>
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
/** 登录页：邮箱 + 密码登录，成功后由 user store 缓存 token 并跳转首页；
 *  连续失败后后端要求图形验证码（业务码 428），本页按需展示并随表单提交 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getCaptcha } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
// username 字段实际填写邮箱，与后端登录接口的参数约定一致
const form = ref({ username: '', password: '', captchaAnswer: '', rememberMe: false })
// focused 仅用于驱动浮动标签样式，不参与表单校验
const usernameFocused = ref(false)
const passwordFocused = ref(false)
// 图形验证码状态：showCaptcha 由后端 428 触发，captchaId/image 随登录请求提交
const showCaptcha = ref(false)
const captchaId = ref('')
const captchaImage = ref('')

/** 校验规则：只做必填检查，邮箱格式与账密正确性由后端判定 */
const rules = {
  username: [{ required: true, message: '请输入邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/** 拉取图形验证码并展示 */
async function loadCaptcha() {
  try {
    const res = await getCaptcha()
    captchaId.value = res.data.captchaId
    captchaImage.value = `data:image/png;base64,${res.data.imageBase64}`
    form.value.captchaAnswer = ''
  } catch { /* 拦截器已提示 */ }
}

/** 提交登录并缓存 token，成功后跳转首页 */
// 失败提示由请求拦截器统一弹出；428 表示需完成图形验证码，弹出验证码区重试
async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    const payload = {
      username: form.value.username,
      password: form.value.password,
      rememberMe: form.value.rememberMe
    }
    if (showCaptcha.value && captchaId.value) {
      payload.captchaId = captchaId.value
      payload.captchaAnswer = form.value.captchaAnswer
    }
    await userStore.login(payload)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    if (e?.code === 428) {
      showCaptcha.value = true
      loadCaptcha()
    }
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

.captcha-row {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.captcha-input {
  flex: 1;
}

.captcha-img {
  height: 40px;
  cursor: pointer;
  border-radius: 4px;
  border: 1px solid #e5e6eb;
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
