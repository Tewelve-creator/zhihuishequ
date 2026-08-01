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
  // 商超多商户：商户端请求带上 X-Shop-Id，后端强制按店铺隔离
  if (user.shopId) {
    config.headers['X-Shop-Id'] = user.shopId
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data && typeof data === 'object' && 'code' in data && data.code !== 0 && data.code !== 200) {
      if (data.code === 40303 || data.error === 'FEATURE_NOT_AVAILABLE') {
        ElMessage.warning(data.msg || data.message || '当前套餐未开放该功能')
      } else {
        ElMessage.error(data.msg || data.message || '请求失败')
      }
      return Promise.reject(data)
    }
    // 统一解包 R.data
    if (data && typeof data === 'object' && 'data' in data) {
      return data.data
    }
    return data
  },
  (err) => {
    const user = useUserStore()
    const demo = !!user.token && user.token.startsWith('token-')
    const status = err.response?.status
    const body = err.response?.data
    const msg = body?.msg || body?.message || err.message || '网络异常'
    // 演示 token 调真实接口会 401，不能整页踢出登录
    if (status === 401 && !demo) {
      ElMessage.error(msg)
      user.logout()
      window.location.href = '/login'
      return Promise.reject(err)
    }
    if (body?.error === 'FEATURE_NOT_AVAILABLE' || body?.code === 40303) {
      ElMessage.warning(msg || '当前套餐未开放该功能')
      return Promise.reject(err)
    }
    if (!demo) {
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  },
)

export default request
