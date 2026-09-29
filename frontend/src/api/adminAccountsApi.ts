import { apiRequest } from './client'
import type { AccountDetail, AccountFilters, AccountSummary, CreateAccountRequest, ResetPasswordRequest, UpdateAccountRequest } from '../types/account'
import type { PageResponse } from '../types/workflow'

const base = '/api/admin/accounts'
export const adminAccountsApi = {
  list(page = 0, size = 10, filters: AccountFilters = {}): Promise<PageResponse<AccountSummary>> {
    const params = new URLSearchParams({ page: String(page), size: String(size), sort: 'username,asc' })
    if (filters.search?.trim()) params.set('search', filters.search.trim())
    if (filters.role) params.set('role', filters.role)
    if (filters.departmentId !== undefined) params.set('departmentId', String(filters.departmentId))
    if (filters.active !== undefined) params.set('active', String(filters.active))
    return apiRequest(`${base}?${params}`)
  },
  detail(id: number): Promise<AccountDetail> { return apiRequest(`${base}/${id}`) },
  create(body: CreateAccountRequest): Promise<AccountDetail> { return apiRequest(base, { method: 'POST', body }) },
  edit(id: number, body: UpdateAccountRequest): Promise<AccountDetail> { return apiRequest(`${base}/${id}`, { method: 'PATCH', body }) },
  activate(id: number): Promise<AccountDetail> { return apiRequest(`${base}/${id}/activate`, { method: 'POST' }) },
  deactivate(id: number): Promise<AccountDetail> { return apiRequest(`${base}/${id}/deactivate`, { method: 'POST' }) },
  resetPassword(id: number, body: ResetPasswordRequest): Promise<AccountDetail> { return apiRequest(`${base}/${id}/reset-password`, { method: 'POST', body }) },
}
