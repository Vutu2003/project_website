import { apiRequest } from './client'
import type { AuthenticatedUser, LoginRequest, LoginResponse } from '../types/auth'

export const authApi = {
  login(input: LoginRequest): Promise<LoginResponse> {
    return apiRequest<LoginResponse>('/api/auth/login', { method: 'POST', body: input, auth: false })
  },
  me(token?: string): Promise<AuthenticatedUser> {
    return apiRequest<AuthenticatedUser>('/api/auth/me', { token })
  },
}
