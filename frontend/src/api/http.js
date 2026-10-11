import axios from 'axios'

/** 统一 HTTP 客户端：附加 JWT、统一错误提示、401 自动登出 */
const http = axios.create({
  baseURL: '/api',
  timeout: 60000
})

// 401 统一广播给应用外壳处理（登出 + 回到登录页）
function broadcastUnauthorized(message) {
  window.dispatchEvent(new CustomEvent('wylie:unauthorized', { detail: message }))
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('wylie_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const status = error.response?.status
    const payload = error.response?.data
    const message = payload?.message || error.message || '请求失败'
    const normalized = { status, code: payload?.code || 'NETWORK_ERROR', message }
    if (status === 401) {
      broadcastUnauthorized(message)
    }
    return Promise.reject(normalized)
  }
)

export default http