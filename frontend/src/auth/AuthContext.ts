import { createContext } from 'react'
import type { AuthenticatedUser, LoginRequest } from '../types/auth'

export type AuthStatus = 'initializing' | 'authenticated' | 'anonymous' | 'unavailable'

export interface AuthContextValue {
  status: AuthStatus
  user: AuthenticatedUser | null
  accessToken: string | null
  message: string | null
  isAuthenticated: boolean
  login: (input: LoginRequest) => Promise<void>
  logout: () => void
  retryRestore: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
