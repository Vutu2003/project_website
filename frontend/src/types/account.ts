import type { Role } from './auth'

export interface AccountSummary {
  id: number
  username: string
  role: Role
  departmentId: number | null
  departmentCode: string | null
  departmentName: string | null
  active: boolean
}
export type AccountDetail = AccountSummary
export interface CreateAccountRequest {
  username: string; password: string; role: Role; departmentId: number | null; active: boolean
}
export interface UpdateAccountRequest { role: Role; departmentId: number | null }
export interface ResetPasswordRequest { newPassword: string }
export interface AccountFilters { search?: string; role?: Role; departmentId?: number; active?: boolean }
