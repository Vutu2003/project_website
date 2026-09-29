import { afterEach, describe, expect, it, vi } from 'vitest'
import { authStorage } from '../auth/authStorage'
import { executionsApi } from './executionsApi'
import { acceptancesApi } from './acceptancesApi'
import { orderedAttempts } from '../utils/attempts'
import type { ExecutionAttempt } from '../types/execution'

afterEach(() => { vi.unstubAllGlobals(); sessionStorage.clear() })

function mockJson(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('UC08–UC10 wire contract', () => {
  it('uses item/plan versions, actual execution IDs and append-only progress without actor/status fields', async () => {
    authStorage.set('primary')
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(mockJson({})))
    vi.stubGlobal('fetch', fetcher)
    await executionsApi.start(31, 3, 8)
    await executionsApi.progress(48, 'Kiểm tra', 'Hư hỏng')
    await executionsApi.finish(48, 4, 'Đã làm')
    await executionsApi.repair(49, 5, 'Cần sửa chữa')
    await acceptancesApi.technical(48, { version: 5, result: 'FAIL', conclusion: 'Cần làm lại', repairRequired: false })
    const calls = fetcher.mock.calls as [string, RequestInit][]
    expect(calls.map(([url]) => new URL(url).pathname)).toEqual([
      '/api/plan-items/31/executions', '/api/executions/48/progress', '/api/executions/48/complete-work',
      '/api/executions/49/repair-required', '/api/executions/48/technical-acceptance',
    ])
    expect(JSON.parse(calls[0][1].body as string)).toEqual({ version: 3, planVersion: 8 })
    expect(JSON.parse(calls[1][1].body as string)).toEqual({ workNote: 'Kiểm tra', damageNote: 'Hư hỏng' })
    for (const [, init] of calls) {
      const body = JSON.parse(init.body as string)
      expect(body).not.toHaveProperty('actorId')
      expect(body).not.toHaveProperty('status')
      expect(new Headers(init.headers).get('Authorization')).toBe('Bearer primary')
    }
  })

  it('keeps the primary token and sends temporary VTYT token only in the handover header', async () => {
    authStorage.set('primary-khoa')
    const fetcher = vi.fn()
      .mockResolvedValueOnce(mockJson({ accessToken: 'secondary-vtyt', tokenType: 'Bearer', user: { role: 'PHONG_VTYT' } }))
      .mockResolvedValueOnce(mockJson({ itemStatus: 'COMPLETED' }, 201))
    vi.stubGlobal('fetch', fetcher)
    await acceptancesApi.signedHandover(61, { version: 7, result: 'PASS', conclusion: 'Đạt', repairRequired: false }, 'vtyt', 'password')
    const calls = fetcher.mock.calls as [string, RequestInit][]
    expect(new URL(calls[0][0]).pathname).toBe('/api/auth/login')
    expect(new Headers(calls[0][1].headers).has('Authorization')).toBe(false)
    expect(new URL(calls[1][0]).pathname).toBe('/api/executions/61/handover')
    expect(new Headers(calls[1][1].headers).get('Authorization')).toBe('Bearer primary-khoa')
    expect(new Headers(calls[1][1].headers).get('X-VTYT-Authorization')).toBe('Bearer secondary-vtyt')
    expect(authStorage.get()).toBe('primary-khoa')
    expect([...Array(sessionStorage.length).keys()].map(index => sessionStorage.getItem(sessionStorage.key(index)!))).not.toContain('secondary-vtyt')
    expect(JSON.parse(calls[1][1].body as string)).toEqual({ version: 7, result: 'PASS', conclusion: 'Đạt', repairRequired: false })
  })

  it('rejects a non-VTYT signer before handover and keeps the department session', async () => {
    authStorage.set('primary-khoa')
    const fetcher = vi.fn().mockResolvedValue(mockJson({ accessToken: 'secondary-other', tokenType: 'Bearer', user: { role: 'ADMIN' } }))
    vi.stubGlobal('fetch', fetcher)
    await expect(acceptancesApi.signedHandover(61, { version: 7, result: 'PASS', conclusion: 'Đạt', repairRequired: false }, 'admin', 'password'))
      .rejects.toThrow('Phòng VTYT')
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(authStorage.get()).toBe('primary-khoa')
  })

  it('keeps the primary KHOA session when secondary credentials are invalid', async () => {
    authStorage.set('primary-khoa')
    const fetcher = vi.fn().mockResolvedValue(mockJson({ status: 401, code: 'INVALID_CREDENTIALS', message: 'Invalid credentials' }, 401))
    vi.stubGlobal('fetch', fetcher)
    await expect(acceptancesApi.signedHandover(61, { version: 7, result: 'PASS', conclusion: 'Đạt', repairRequired: false }, 'vtyt', 'wrong'))
      .rejects.toThrow('Invalid credentials')
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(authStorage.get()).toBe('primary-khoa')
  })

  it('sorts attempts by server-assigned attempt number without changing original evidence', () => {
    const a = { executionId: 9, attemptNo: 2 } as ExecutionAttempt
    const b = { executionId: 4, attemptNo: 1 } as ExecutionAttempt
    const source = [a, b]
    expect(orderedAttempts(source)).toEqual([b, a])
    expect(source).toEqual([a, b])
  })
})
