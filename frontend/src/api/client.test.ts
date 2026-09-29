import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, sessionExpiredEvent } from './client'
import { authStorage } from '../auth/authStorage'
import { ApiError, NetworkError } from './types'

afterEach(() => {
  vi.unstubAllGlobals()
  window.sessionStorage.clear()
})

describe('API client and session storage', () => {
  it('stores the token for this browser tab and attaches Bearer centrally', async () => {
    authStorage.set('test-token')
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 1 }), { status: 200 }))
    vi.stubGlobal('fetch', fetcher)
    await expect(apiRequest<{ id: number }>('/api/auth/me')).resolves.toEqual({ id: 1 })
    const request = fetcher.mock.calls[0][1] as RequestInit
    expect(new Headers(request.headers).get('Authorization')).toBe('Bearer test-token')
  })

  it('normalizes a backend validation error without raw JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 400, code: 'VALIDATION_ERROR', message: 'Request validation failed',
      fieldErrors: [{ field: 'size', message: 'must be less than or equal to 100' }],
    }), { status: 400 })))
    await expect(apiRequest('/api/equipment')).rejects.toMatchObject({
      status: 400, code: 'VALIDATION_ERROR',
      fieldErrors: [{ field: 'size', message: 'must be less than or equal to 100' }],
    } satisfies Partial<ApiError>)
  })

  it('clears an expired session on authenticated 401 without retrying', async () => {
    authStorage.set('expired-token')
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 401, code: 'INVALID_TOKEN', message: 'Invalid bearer token', fieldErrors: [],
    }), { status: 401 }))
    vi.stubGlobal('fetch', fetcher)
    let expired = 0
    const listener = () => expired++
    window.addEventListener(sessionExpiredEvent, listener)
    try {
      await expect(apiRequest('/api/auth/me')).rejects.toBeInstanceOf(ApiError)
      expect(authStorage.get()).toBeNull()
      expect(expired).toBe(1)
      expect(fetcher).toHaveBeenCalledTimes(1)
    } finally { window.removeEventListener(sessionExpiredEvent, listener) }
  })

  it('turns a failed connection into a readable network error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(apiRequest('/api/auth/me')).rejects.toBeInstanceOf(NetworkError)
  })
})
