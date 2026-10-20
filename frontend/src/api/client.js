import axios from 'axios'

const client = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 自动注入租户 ID 和标准 Authorization Header (Bearer <token>)
client.interceptors.request.use(config => {
  const token = localStorage.getItem('agentforge_token')
  const tenantId = localStorage.getItem('agentforge_tenant_id') || '1'

  if (!config.headers) {
    config.headers = {}
  }

  if (token) {
    config.headers['Authorization'] = token.startsWith('Bearer ') ? token : `Bearer ${token}`
  }
  config.headers['X-Tenant-Id'] = tenantId

  return config
}, error => {
  return Promise.reject(error)
})

// 响应统一拦截与 401 凭证过期清理
client.interceptors.response.use(response => {
  const res = response.data
  if (res.code !== 200) {
    console.error('API Error:', res.message)
    return Promise.reject(new Error(res.message || 'Error'))
  }
  return res.data
}, error => {
  if (error.response && error.response.status === 401) {
    console.warn('登录已过期或未授权，清除 Token 并重定向至登录页')
    localStorage.removeItem('agentforge_token')
    localStorage.removeItem('agentforge_tenant_id')
    localStorage.removeItem('agentforge_username')
    if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
  }
  const errorMsg = error.response?.data?.message || error.response?.data?.msg || error.message || '网络异常'
  console.error('API Error:', errorMsg)
  const err = new Error(errorMsg)
  err.response = error.response
  return Promise.reject(err)
})

export default client
