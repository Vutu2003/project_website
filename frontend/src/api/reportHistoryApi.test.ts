import { afterEach, describe, expect, it, vi } from 'vitest'
import { authStorage } from '../auth/authStorage'
import { reportsApi } from './reportsApi'
import { historyApi } from './historyApi'

const response = () => new Response(JSON.stringify({}), { status: 200, headers: { 'Content-Type': 'application/json' } })
afterEach(() => { vi.unstubAllGlobals(); sessionStorage.clear() })

describe('UC11/UC12 HTTP contract', () => {
  it('uses the report lifecycle endpoints and only supplied narrative plus plan version', async () => {
    authStorage.set('primary')
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(response()))
    vi.stubGlobal('fetch', fetcher)
    const body = { version: 7, reportNumber: 'BM03-01', workDone: 'Đã kiểm tra', achieved: null, notAchieved: null,
      causes: null, nextWork: null, resolutions: null, recommendations: null }
    await reportsApi.get(23)
    await reportsApi.create(23, body)
    await reportsApi.edit(23, { ...body, version: 8 })
    await reportsApi.finalize(23, 9)
    const calls = fetcher.mock.calls as [string, RequestInit][]
    expect(calls.map(([url, init]) => `${init.method ?? 'GET'} ${new URL(url).pathname}`)).toEqual([
      'GET /api/plans/23/report', 'POST /api/plans/23/report', 'PUT /api/plans/23/report',
      'POST /api/plans/23/report/finalize',
    ])
    expect(JSON.parse(calls[1][1].body as string)).toEqual(body)
    expect(JSON.parse(calls[2][1].body as string).version).toBe(8)
    expect(JSON.parse(calls[3][1].body as string)).toEqual({ version: 9 })
    for (const [, init] of calls.slice(1)) {
      const sent = JSON.parse(init.body as string)
      expect(sent).not.toHaveProperty('actorId')
      expect(sent).not.toHaveProperty('status')
      expect(sent).not.toHaveProperty('completedCount')
      expect(sent).not.toHaveProperty('repairRequiredCount')
    }
  })
  it('uses one typed history GET with no mutation request', async () => {
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(response()))
    vi.stubGlobal('fetch', fetcher)
    await historyApi.get(42)
    expect(fetcher).toHaveBeenCalledTimes(1)
    const [url, init] = fetcher.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/api/equipment/42/maintenance-history')
    expect(init.method ?? 'GET').toBe('GET')
  })
})
