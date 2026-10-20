import { describe, it, expect } from 'vitest'
import { buildWidgetHeaders } from './agentforge-widget.js'

describe('AgentForge Widget Header Builder', () => {
  it('1. buildWidgetHeaders({}) 返回默认头包含 Content-Type: application/json', () => {
    const headers = buildWidgetHeaders({})
    expect(headers).toBeDefined()
    expect(headers['Content-Type']).toBe('application/json')
    expect(headers['X-Public-Token']).toBeUndefined()
    expect(headers['X-AgentForge-Public-Token']).toBeUndefined()
    expect(headers['X-Tenant-Id']).toBeUndefined()
  })

  it('2. buildWidgetHeaders({ publicToken: "tok_xxx" }) 注入 X-Public-Token 与兼容头 X-AgentForge-Public-Token', () => {
    const headers = buildWidgetHeaders({ publicToken: 'tok_xxx' })
    expect(headers['Content-Type']).toBe('application/json')
    expect(headers['X-Public-Token']).toBe('tok_xxx')
    expect(headers['X-AgentForge-Public-Token']).toBe('tok_xxx')
  })

  it('3. buildWidgetHeaders({ tenantId: 2 }) 注入 X-Tenant-Id: 2', () => {
    const headers = buildWidgetHeaders({ tenantId: 2 })
    expect(headers['Content-Type']).toBe('application/json')
    expect(headers['X-Tenant-Id']).toBe('2')
  })
})
