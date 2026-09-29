import { authStorage } from '../auth/authStorage'
import { ApiError, NetworkError } from './types'
import type { ErrorResponse, FieldErrorResponse } from './types'

const baseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
export const sessionExpiredEvent = 'medical-maintenance:session-expired'

function isFieldError(value: unknown): value is FieldErrorResponse {
  return typeof value === 'object' && value !== null &&
    'field' in value && typeof value.field === 'string' &&
    'message' in value && typeof value.message === 'string'
}

function isErrorResponse(value: unknown): value is ErrorResponse {
  return typeof value === 'object' && value !== null &&
    'status' in value && typeof value.status === 'number' &&
    'code' in value && typeof value.code === 'string' &&
    'message' in value && typeof value.message === 'string'
}

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  token?: string | null
  auth?: boolean
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, token, auth = true, headers: suppliedHeaders, ...rest } = options
  const bearer = auth ? (token ?? authStorage.get()) : null
  const headers = new Headers(suppliedHeaders)
  if (body !== undefined) headers.set('Content-Type', 'application/json')
  if (bearer) headers.set('Authorization', `Bearer ${bearer}`)

  let response: Response
  try {
    response = await fetch(`${baseUrl}${path}`, {
      ...rest,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new NetworkError()
  }

  if (response.status === 401 && auth && bearer) {
    authStorage.clear()
    window.dispatchEvent(new Event(sessionExpiredEvent))
  }
  if (!response.ok) {
    let payload: unknown
    try { payload = await response.json() } catch { /* Keep a safe fallback below. */ }
    if (isErrorResponse(payload)) {
      throw new ApiError(
        response.status,
        payload.code,
        payload.message,
        Array.isArray(payload.fieldErrors) ? payload.fieldErrors.filter(isFieldError) : [],
      )
    }
    throw new ApiError(response.status, 'HTTP_ERROR', `Yêu cầu không thành công (${response.status}).`)
  }
  return await response.json() as T
}
