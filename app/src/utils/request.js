/**
 * 网关请求封装
 * Base: H5 走 /api 代理；App/小程序直连网关
 */
const GATEWAY = 'http://localhost:8080'

function resolveBase() {
  // #ifdef H5
  return '/api'
  // #endif
  // #ifndef H5
  return GATEWAY
  // #endif
}

export function getBaseUrl() {
  return resolveBase()
}

/**
 * 登录会话中的租户 ID（优先 store / 登录写入的 storage，忽略展示用社区切换）
 */
export function resolveLoginTenantId() {
  let tid = uni.getStorageSync('tenantId')
  // 首页切换社区可能误改 storage，以登录快照为准
  const loginTid = uni.getStorageSync('loginTenantId')
  if (loginTid !== '' && loginTid !== null && loginTid !== undefined) {
    tid = loginTid
    if (String(uni.getStorageSync('tenantId')) !== String(loginTid)) {
      uni.setStorageSync('tenantId', loginTid)
    }
  }
  return tid === '' || tid === null || tid === undefined ? null : tid
}

export function request(options = {}) {
  const {
    url,
    method = 'GET',
    data,
    header = {},
    auth = true,
    showError = true
  } = options

  const token = uni.getStorageSync('token') || ''
  const tenantId = resolveLoginTenantId()
  const userId = uni.getStorageSync('userId')
  const role = uni.getStorageSync('role')
  const headers = {
    'Content-Type': 'application/json',
    ...header
  }
  if (auth && token) {
    headers.Authorization = `Bearer ${token}`
  }
  // 强制携带登录租户，与 Token 一致，避免「禁止跨社区访问」
  if (tenantId !== '' && tenantId !== null && tenantId !== undefined) {
    headers['X-Tenant-Id'] = String(tenantId)
  }
  if (userId !== '' && userId !== null && userId !== undefined) {
    headers['X-User-Id'] = String(userId)
  }
  if (role) {
    headers['X-User-Role'] = String(role)
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: `${resolveBase()}${url}`,
      method,
      data,
      header: headers,
      success: (res) => {
        const body = res.data
        // HTTP 4xx/5xx（含代理失败返回的 HTML）一律当失败，避免把非数组塞进页面
        if (res.statusCode >= 400) {
          const msg =
            (body && typeof body === 'object' && body.msg) ||
            (res.statusCode === 502 || res.statusCode === 503
              ? '后端服务未启动或网关异常'
              : `请求失败(${res.statusCode})`)
          if (showError) {
            uni.showToast({ title: msg, icon: 'none', duration: 2500 })
          }
          reject(typeof body === 'object' && body ? body : { code: res.statusCode, msg })
          return
        }
        if (!body || typeof body !== 'object') {
          if (showError) {
            uni.showToast({ title: '接口返回异常', icon: 'none' })
          }
          reject({ code: -1, msg: '接口返回异常' })
          return
        }
        if (res.statusCode === 401 || body.code === 401) {
          uni.removeStorageSync('token')
          uni.showToast({ title: '请重新登录', icon: 'none' })
          setTimeout(() => {
            uni.reLaunch({ url: '/pages/login/login' })
          }, 500)
          reject(body)
          return
        }
        if (body.code !== 0 && body.code !== undefined) {
          if (showError) {
            uni.showToast({ title: body.msg || '请求失败', icon: 'none', duration: 2500 })
          }
          reject(body)
          return
        }
        resolve(body.data !== undefined ? body.data : body)
      },
      fail: (err) => {
        if (showError) {
          uni.showToast({ title: '网络异常，请检查网关', icon: 'none' })
        }
        reject(err)
      }
    })
  })
}

export function uploadFile(filePath) {
  const token = uni.getStorageSync('token') || ''
  const tenantId = resolveLoginTenantId()
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${resolveBase()}/file/upload`,
      filePath,
      name: 'file',
      header: {
        Authorization: `Bearer ${token}`,
        'X-Tenant-Id': tenantId != null ? String(tenantId) : ''
      },
      success: (res) => {
        try {
          const body = JSON.parse(res.data)
          if (body.code !== 0) {
            uni.showToast({ title: body.msg || '上传失败', icon: 'none' })
            reject(body)
            return
          }
          // 后端可能返回字符串 url 或对象
          const data = body.data
          resolve(typeof data === 'string' ? data : (data?.url || data))
        } catch (e) {
          reject(e)
        }
      },
      fail: reject
    })
  })
}
