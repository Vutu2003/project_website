import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { authApi } from '../api/authApi'
import { sessionExpiredEvent } from '../api/client'
import { ApiError } from '../api/types'
import { authStorage } from './authStorage'
import type { AuthenticatedUser, LoginRequest } from '../types/auth'
import { AuthContext } from './AuthContext'
import type { AuthStatus } from './AuthContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('initializing')
  const [user, setUser] = useState<AuthenticatedUser | null>(null)
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const clear = useCallback((reason: string | null = null) => {
    authStorage.clear()
    setAccessToken(null)
    setUser(null)
    setStatus('anonymous')
    setMessage(reason)
  }, [])

  const retryRestore = useCallback(async () => {
    const token = authStorage.get()
    if (!token) {
      clear()
      return
    }
    setStatus('initializing')
    try {
      const currentUser = await authApi.me(token)
      setAccessToken(token)
      setUser(currentUser)
      setMessage(null)
      setStatus('authenticated')
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        clear('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
      } else {
        setStatus('unavailable')
        setMessage('Không thể xác minh phiên đăng nhập. Vui lòng kiểm tra kết nối và thử lại.')
      }
    }
  }, [clear])

  useEffect(() => {
    void retryRestore()
    const expire = () => clear('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
    window.addEventListener(sessionExpiredEvent, expire)
    return () => window.removeEventListener(sessionExpiredEvent, expire)
  }, [clear, retryRestore])

  const login = useCallback(async (input: LoginRequest) => {
    const result = await authApi.login(input)
    if (result.tokenType !== 'Bearer' || !result.accessToken) {
      throw new Error('Máy chủ trả về phiên đăng nhập không hợp lệ.')
    }
    const currentUser = await authApi.me(result.accessToken)
    authStorage.set(result.accessToken)
    setAccessToken(result.accessToken)
    setUser(currentUser)
    setMessage(null)
    setStatus('authenticated')
  }, [])

  const logout = useCallback(() => clear(), [clear])

  return (
    <AuthContext.Provider value={{ status, user, accessToken, message, isAuthenticated: status === 'authenticated', login, logout, retryRestore }}>
      {children}
    </AuthContext.Provider>
  )
}
