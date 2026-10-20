import { describe, it, expect, beforeEach, vi } from 'vitest'
import client from './client'

describe('API Client Interceptors', () => {
  const requestHandler = client.interceptors.request.handlers[0].fulfilled
  const responseErrorHandler = client.interceptors.response.handlers[0].rejected

  beforeEach(() => {
    localStorage.clear()
    delete window.location
    window.location = { href: '', pathname: '/dashboard' }
    vi.clearAllMocks()
  })

  it('1. 收到 401 时清除 localStorage 鉴权信息并重定向至 /login', async () => {
    localStorage.setItem('agentforge_token', 'test_token')
    localStorage.setItem('agentforge_tenant_id', '1')
    localStorage.setItem('agentforge_username', 'admin')

    const mockError = {
      response: {
        status: 401,
        data: { message: 'Unauthorized' }
      }
    }

    await expect(responseErrorHandler(mockError)).rejects.toThrow()
    expect(localStorage.getItem('agentforge_token')).toBeNull()
    expect(localStorage.getItem('agentforge_tenant_id')).toBeNull()
    expect(localStorage.getItem('agentforge_username')).toBeNull()
    expect(window.location.href).toBe('/login')
  })

  it('2. 收到 403 权限不足时 rejection 中携带原服务端消息', async () => {
    const mockError = {
      response: {
        status: 403,
        data: { msg: '权限不足' }
      }
    }

    try {
      await responseErrorHandler(mockError)
      expect.fail('应抛出异常')
    } catch (err) {
      expect(err.message).toBe('权限不足')
      expect(err.response.status).toBe(403)
    }
  })

  it('3. request 拦截器为出站请求注入 Bearer token 与 X-Tenant-Id 头', () => {
    localStorage.setItem('agentforge_token', 'tok_abc')
    localStorage.setItem('agentforge_tenant_id', '5')

    const config = { headers: {} }
    const result = requestHandler(config)

    expect(result.headers['Authorization']).toBe('Bearer tok_abc')
    expect(result.headers['X-Tenant-Id']).toBe('5')
  })
})
