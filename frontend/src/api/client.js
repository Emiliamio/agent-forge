import axios from 'axios'

const client = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器：自动注入租户 ID 和 Sa-Token
client.interceptors.request.use(config => {
  const token = localStorage.getItem('agentforge_token')
  const tenantId = localStorage.getItem('agentforge_tenant_id') || '1'

  if (token) {
    config.headers['satoken'] = token
  }
  config.headers['X-Tenant-Id'] = tenantId

  return config
}, error => {
  return Promise.reject(error)
})

// 响应拦截器：统一处理错误提示
client.interceptors.response.use(response => {
  const res = response.data
  if (res.code !== 200) {
    console.error('API Error:', res.message)
    return Promise.reject(new Error(res.message || 'Error'))
  }
  return res.data
}, error => {
  console.error('Network Error:', error.message)
  return Promise.reject(error)
})

export default client
