import { Navigate } from 'react-router'
import type { ReactNode } from 'react'
import type { Role } from '../types/auth'
import { useAuth } from './useAuth'

export function RoleGuard({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user } = useAuth()
  return user && roles.includes(user.role) ? children : <Navigate to="/unauthorized" replace />
}
