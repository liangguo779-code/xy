import request from '@/utils/request'

/** 账号密码登录，成功返回 { token, user }（user store 负责持久化） */
export function login(data) {
  return request.post('/api/auth/login', data)
}

/** 注册账号 */
export function register(data) {
  return request.post('/api/auth/register', data)
}

/** 获取当前登录用户信息 */
export function getUserInfo() {
  return request.get('/api/auth/me')
}

/** 退出登录，后端注销令牌 */
export function logout() {
  return request.post('/api/auth/logout')
}

/** 获取图形验证码 */
export function getCaptcha() {
  return request.get('/api/auth/captcha')
}

/** 发送邮箱验证码（注册验证邮箱） */
export function sendEmailCode(data) {
  return request.post('/api/auth/send-email-code', data)
}

/** 校验邮箱验证码 */
export function verifyEmailCode(data) {
  return request.post('/api/auth/verify-email-code', data)
}

/** 发送重置密码验证码（走独立的 /send-code 接口） */
export function sendResetCode(data) {
  return request.post('/api/auth/send-code', data)
}

/** 重置密码（验证码 + 新密码） */
export function resetPassword(data) {
  return request.post('/api/auth/reset-password', data)
}
