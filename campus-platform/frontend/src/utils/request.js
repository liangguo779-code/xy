import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

/** 全局 axios 实例：统一携带 token、解析后端 { code, msg, data } 包装并弹出错误提示 */
const request = axios.create({
  baseURL: '',
  timeout: 10000
})

// 请求拦截器 - 自动携带 token
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器 - 统一错误处理
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.msg || '请求失败')
      // token 过期
      if (res.code === 401) {
        localStorage.removeItem('token')
        router.push('/login')
      }
      // 把业务码挂到 Error 上，调用方可按 code 分支处理（如登录 428 弹图形验证码）
      const err = new Error(res.msg)
      err.code = res.code
      return Promise.reject(err)
    }
    // 直接返回包体而非 response，业务代码因此用 res.data 取数据
    return res
  },
  error => {
    // HTTP 层 401（如令牌过期被拒绝）与业务码 401 一样清登录态并跳登录页
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    ElMessage.error(error.response?.data?.msg || error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
