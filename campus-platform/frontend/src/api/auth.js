import request from '@/utils/request'

export function login(data) {
  return request.post('/api/auth/login', data)
}

export function register(data) {
  return request.post('/api/auth/register', data)
}

export function getUserInfo() {
  return request.get('/api/auth/me')
}

export function logout() {
  return request.post('/api/auth/logout')
}

export function getCaptcha() {
  return request.get('/api/auth/captcha')
}

export function sendEmailCode(data) {
  return request.post('/api/auth/send-email-code', data)
}

export function verifyEmailCode(data) {
  return request.post('/api/auth/verify-email-code', data)
}

export function sendResetCode(data) {
  return request.post('/api/auth/send-code', data)
}

export function resetPassword(data) {
  return request.post('/api/auth/reset-password', data)
}
