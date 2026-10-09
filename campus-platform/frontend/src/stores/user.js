import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, getUserInfo, logout as logoutApi } from '@/api/auth'

/** 用户全局状态：登录令牌与当前用户信息 */
export const useUserStore = defineStore('user', () => {
  // 登录令牌，初始化从 localStorage 恢复，刷新页面不丢失登录态
  const token = ref(localStorage.getItem('token') || '')
  // 当前用户信息，null 表示尚未拉取
  const userInfo = ref(null)

  /** 登录：保存 token 与 userId 到 localStorage（userId 供其他模块直接读取） */
  async function login(form) {
    const res = await loginApi(form)
    token.value = res.data.token
    userInfo.value = res.data.user
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('userId', res.data.user.id)
    return res.data
  }

  /** 拉取当前登录用户信息并同步缓存 userId */
  async function fetchUserInfo() {
    const res = await getUserInfo()
    userInfo.value = res.data
    localStorage.setItem('userId', res.data.id)
    return res.data
  }

  /** 登出：接口失败也照常清空本地登录态，避免卡在已登录状态 */
  async function logout() {
    try {
      await logoutApi()
    } catch (e) { /* ignore */ }
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
  }

  return { token, userInfo, login, fetchUserInfo, logout }
})
