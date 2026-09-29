import { afterEach, describe, expect, it, vi } from 'vitest'
import { authStorage } from '../auth/authStorage'
import { plansApi } from './plansApi'
import { approvalsApi } from './approvalsApi'
import { providersApi } from './providersApi'

afterEach(() => { vi.unstubAllGlobals(); sessionStorage.clear() })

describe('UC01–UC07 command wire shapes', () => {
  it('sends exact plan create, versioned submit and decision bodies without actor/status fields', async () => {
    authStorage.set('test-token')
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(new Response('{}', { status: 200 })))
    vi.stubGlobal('fetch', fetcher)
    await plansApi.create({ title: 'Test', periodStart: '2026-10-01', periodEnd: '2026-10-31', items: [{ equipmentId: 1, plannedDate: null }] })
    await plansApi.submit(9, 3)
    await approvalsApi.decide(17, { version: 4, outcome: 'REVISION_REQUIRED', comment: 'Cần sửa' })
    const calls = fetcher.mock.calls as [string, RequestInit][]
    expect(calls.map(([url, init]) => [new URL(url).pathname, init.method])).toEqual([
      ['/api/plans', 'POST'], ['/api/plans/9/submit', 'POST'], ['/api/approvals/17/decision', 'POST'],
    ])
    expect(JSON.parse(calls[1][1].body as string)).toEqual({ version: 3 })
    expect(JSON.parse(calls[2][1].body as string)).toEqual({ version: 4, outcome: 'REVISION_REQUIRED', comment: 'Cần sửa' })
    for (const [, init] of calls) {
      const body = JSON.parse(init.body as string)
      expect(body).not.toHaveProperty('actorId')
      expect(body).not.toHaveProperty('status')
      expect(new Headers(init.headers).get('Authorization')).toBe('Bearer test-token')
    }
  })

  it('requires an explicit coverageId in the routed request and keeps vendor draft separate', async () => {
    authStorage.set('test-token')
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(new Response('{}', { status: 200 })))
    vi.stubGlobal('fetch', fetcher)
    await providersApi.route(31, 2, 15)
    await providersApi.createDraft(31, { version: 3, providerId: null, rationale: null, warrantyImpactNote: null })
    const calls = fetcher.mock.calls as [string, RequestInit][]
    expect(JSON.parse(calls[0][1].body as string)).toEqual({ version: 2, coverageId: 15 })
    expect(new URL(calls[1][0]).pathname).toBe('/api/plan-items/31/vendor-proposals')
  })
})
