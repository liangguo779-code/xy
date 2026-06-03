<template>
  <div class="forgot-container">
    <el-card class="forgot-card">
      <template #header>
        <h2 class="title">密码找回</h2>
      </template>

      <el-steps :active="step" finish-status="success" style="margin-bottom: 24px">
        <el-step title="验证手机号" />
        <el-step title="重置密码" />
        <el-step title="完成" />
      </el-steps>

      <!-- Step 1: 输入手机号+验证码 -->
      <div v-if="step === 0">
        <el-form :model="form" :rules="step1Rules" ref="step1Ref" label-width="0">
          <el-form-item prop="phone">
            <el-input v-model="form.phone" placeholder="请输入注册手机号" size="large"
                      :prefix-icon="Iphone" />
          </el-form-item>
          <el-form-item prop="code">
            <div class="code-row">
              <el-input v-model="form.code" placeholder="验证码" size="large" />
              <el-button size="large" :disabled="countdown > 0" @click="handleSendCode">
                {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" style="width: 100%" @click="handleVerifyCode">
              下一步
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 2: 设置新密码 -->
      <div v-if="step === 1">
        <el-form :model="form" :rules="step2Rules" ref="step2Ref" label-width="0">
          <el-form-item prop="newPassword">
            <el-input v-model="form.newPassword" type="password" placeholder="新密码 (6-20位)"
                      size="large" show-password :prefix-icon="Lock" />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" placeholder="确认新密码"
                      size="large" show-password :prefix-icon="Lock" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" style="width: 100%"
                       :loading="submitting" @click="handleReset">
              重置密码
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 3: 完成 -->
      <div v-if="step === 2" style="text-align: center; padding: 40px 0">
        <el-icon :size="64" color="#67c23a"><CircleCheckFilled /></el-icon>
        <h3>密码重置成功</h3>
        <p style="color: #909399">请使用新密码登录</p>
        <el-button type="primary" @click="router.push('/login')">去登录</el-button>
      </div>

      <div style="text-align: center; margin-top: 16px">
        <router-link to="/login">返回登录</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Iphone, Lock, CircleCheckFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const step = ref(0)
const countdown = ref(0)
const submitting = ref(false)
const step1Ref = ref()
const step2Ref = ref()

const form = ref({
  phone: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

const step1Rules = {
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
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

async function handleSendCode() {
  if (!form.value.phone) {
    ElMessage.warning('请输入手机号')
    return
  }
  try {
    await fetch(`/api/auth/send-code?phone=${form.value.phone}`, { method: 'POST' })
    ElMessage.success('验证码已发送')
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    ElMessage.error('发送失败')
  }
}

async function handleVerifyCode() {
  await step1Ref.value.validate()
  step.value = 1
}

async function handleReset() {
  await step2Ref.value.validate()
  submitting.value = true
  try {
    const res = await fetch('/api/auth/reset-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        phone: form.value.phone,
        code: form.value.code,
        newPassword: form.value.newPassword
      })
    })
    const data = await res.json()
    if (data.code === 200) {
      step.value = 2
    } else {
      ElMessage.error(data.msg)
    }
  } catch (e) {
    ElMessage.error('重置失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.forgot-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.forgot-card {
  width: 460px;
}

.title {
  text-align: center;
  margin: 0;
}

.code-row {
  display: flex;
  gap: 12px;
  width: 100%;
}

.code-row .el-input {
  flex: 1;
}
</style>
