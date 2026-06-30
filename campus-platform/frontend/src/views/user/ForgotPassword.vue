<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="brand">
        <h1>找回密码</h1>
        <p>通过邮箱验证码重置你的密码</p>
      </div>

      <!-- Step 0: 邮箱 + 验证码 -->
      <el-form v-if="step === 0" ref="step1Ref" :model="form" :rules="step1Rules" label-width="0">
        <div class="floating-label" :class="{ active: form.email, focused: emailFocused }">
          <label>邮箱</label>
          <el-input
            v-model="form.email" size="large" autocomplete="email"
            @focus="emailFocused = true" @blur="emailFocused = false"
          />
        </div>

        <div class="captcha-row">
          <div class="captcha-img-wrap" @click="refreshCaptcha">
            <img v-if="captchaImage" :src="captchaImage" class="captcha-img" alt="验证码" />
            <span v-else class="captcha-loading">加载中...</span>
          </div>
          <el-input v-model="form.captchaAnswer" placeholder="计算结果" size="large" maxlength="4" />
        </div>

        <div class="floating-label" :class="{ active: form.code, focused: codeFocused }">
          <label>邮箱验证码</label>
          <div class="code-row">
            <el-input
              v-model="form.code" size="large" maxlength="6" autocomplete="one-time-code"
              @focus="codeFocused = true" @blur="codeFocused = false"
            />
            <el-button class="send-code-btn" size="large" :disabled="countdown > 0" :loading="sendingCode" @click="handleSendCode">
              {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </el-button>
          </div>
        </div>

        <el-button type="primary" size="large" class="submit-btn" @click="handleNext">
          下一步
        </el-button>
      </el-form>

      <!-- Step 1: 设置新密码 -->
      <el-form v-if="step === 1" ref="step2Ref" :model="form" :rules="step2Rules" label-width="0">
        <div class="floating-label" :class="{ active: form.newPassword, focused: newPwdFocused }">
          <label>新密码</label>
          <el-input
            v-model="form.newPassword" type="password" size="large" show-password autocomplete="new-password"
            @focus="newPwdFocused = true" @blur="newPwdFocused = false"
          />
        </div>

        <div class="floating-label" :class="{ active: form.confirmPassword, focused: confirmPwdFocused }">
          <label>确认新密码</label>
          <el-input
            v-model="form.confirmPassword" type="password" size="large" show-password autocomplete="new-password"
            @focus="confirmPwdFocused = true" @blur="confirmPwdFocused = false"
          />
        </div>

        <el-button type="primary" size="large" class="submit-btn" :loading="submitting" @click="handleReset">
          重置密码
        </el-button>
      </el-form>

      <!-- Step 2: 完成 -->
      <div v-if="step === 2" class="success-state">
        <div class="success-icon">
          <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
            <circle cx="32" cy="32" r="32" fill="#e8f5e9"/>
            <path d="M20 33l8 8 16-16" stroke="#34a853" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <h2>密码重置成功</h2>
        <p>请使用新密码登录你的账号</p>
        <el-button type="primary" size="large" class="submit-btn" @click="router.push('/login')">
          去登录
        </el-button>
      </div>

      <div v-if="step < 2" class="bottom-link">
        <router-link to="/login">返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha, sendResetCode, resetPassword } from '@/api/auth'

const router = useRouter()
const step = ref(0)
const countdown = ref(0)
const sendingCode = ref(false)
const submitting = ref(false)
const step1Ref = ref()
const step2Ref = ref()

const emailFocused = ref(false)
const codeFocused = ref(false)
const newPwdFocused = ref(false)
const confirmPwdFocused = ref(false)

const captchaId = ref('')
const captchaImage = ref('')

const form = ref({
  email: '',
  code: '',
  captchaAnswer: '',
  newPassword: '',
  confirmPassword: ''
})

const step1Rules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

const step2Rules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度6-20位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.value.newPassword) {
          callback(new Error('两次密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function refreshCaptcha() {
  try {
    const res = await getCaptcha()
    captchaId.value = res.data.captchaId
    captchaImage.value = res.data.image
    form.value.captchaAnswer = ''
  } catch (e) {
    // handled
  }
}

async function handleSendCode() {
  try { await step1Ref.value.validateField('email') } catch { return }
  if (!form.value.captchaAnswer) {
    ElMessage.warning('请先完成人机验证')
    return
  }
  sendingCode.value = true
  try {
    await sendResetCode({
      email: form.value.email,
      captchaId: captchaId.value,
      captchaAnswer: form.value.captchaAnswer,
    })
    ElMessage.success('验证码已发送到邮箱')
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    refreshCaptcha()
  } finally {
    sendingCode.value = false
  }
}

async function handleNext() {
  await step1Ref.value.validate()
  step.value = 1
}

async function handleReset() {
  await step2Ref.value.validate()
  submitting.value = true
  try {
    await resetPassword({
      email: form.value.email,
      code: form.value.code,
      newPassword: form.value.newPassword,
    })
    step.value = 2
  } catch (e) {
    // handled
  } finally {
    submitting.value = false
  }
}

onMounted(refreshCaptcha)
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
  gap: 12px;
  margin-bottom: 20px;
  align-items: center;
}

.captcha-img-wrap {
  cursor: pointer;
  border-radius: 10px;
  overflow: hidden;
  border: 1.5px solid #e5e7eb;
  flex-shrink: 0;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.captcha-img {
  height: 42px;
  display: block;
}

.captcha-loading {
  padding: 0 12px;
  font-size: 12px;
  color: #9ca3af;
}

.captcha-row :deep(.el-input__wrapper) {
  border-radius: 12px;
  padding: 4px 16px;
  box-shadow: 0 0 0 1.5px #e5e7eb;
}

.captcha-row :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #4285f4;
}

.code-row {
  display: flex;
  gap: 8px;
}

.code-row .el-input {
  flex: 1;
}

.send-code-btn {
  min-width: 110px;
  height: 100%;
  border-radius: 12px;
  font-weight: 500;
  border: 1.5px solid #4285f4;
  color: #4285f4;
  background: #fff;
  transition: all 0.2s;
  flex-shrink: 0;
}

.send-code-btn:hover:not(:disabled) {
  background: #f0f6ff;
}

.send-code-btn:disabled {
  border-color: #d1d5db;
  color: #9ca3af;
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
  margin-top: 8px;
  transition: all 0.2s;
}

.submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(66, 133, 244, 0.35);
}

.success-state {
  text-align: center;
  padding: 20px 0;
}

.success-icon {
  margin-bottom: 20px;
}

.success-state h2 {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
  color: #1a1a2e;
}

.success-state p {
  margin: 0 0 28px;
  font-size: 14px;
  color: #6b7280;
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
