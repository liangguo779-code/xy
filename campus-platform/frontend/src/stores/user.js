import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, getUserInfo, logout as logoutApi } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(null)

  async function login(form) {
    const res = await loginApi(form)
    token.value = res.data.token
    userInfo.value = res.data.user
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('userId', res.data.user.id)
    return res.data
  }

  async function fetchUserInfo() {
    const res = await getUserInfo()
    userInfo.value = res.data
    localStorage.setItem('userId', res.data.id)
    return res.data
  }

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
