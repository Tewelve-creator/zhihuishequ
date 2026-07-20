import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const user = useUserStore()
  if (user.token) {
    config.headers.Authorization = `Bearer ${user.token}`
  }
  config.headers['X-Portal'] = user.portal
  if (user.tenantId) {
    config.headers['X-Tenant-Id'] = user.tenantId
  }
  if (user.userId) {
    config.headers['X-User-Id'] = user.userId
  }
  if (user.role) {
    config.headers['X-User-Role'] = user.role
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data && typeof data === 'object' && 'code' in data && data.code !== 0 && data.code !== 200) {
      ElMessage.error(data.msg || data.message || '请求失败')
      return Promise.reject(data)
    }
    // 统一解包 R.data
    if (data && typeof data === 'object' && 'data' in data) {
      return data.data
    }
    return data
  },
  (err) => {
    const msg = err.response?.data?.msg || err.response?.data?.message || err.message || '网络异常'
    ElMessage.error(msg)
    if (err.response?.status === 401) {
      const user = useUserStore()
      user.logout()
      window.location.href = '/login'
    }
    return Promise.reject(err)
  },
)

export default request
