import { apiRequest } from './client'
import type { CreatePlanRequest, EditPlanRequest, PageResponse, Plan, PlanCommandResponse, PlanItem, PlanStatus } from '../types/workflow'

export const plansApi = {
  list(page = 0, size = 10, status?: PlanStatus): Promise<PageResponse<Plan>> {
    const params = new URLSearchParams({ page: String(page), size: String(size), sort: 'createdAt,desc' })
    if (status) params.set('status', status)
    return apiRequest(`/api/plans?${params}`)
  },
  detail(id: number): Promise<Plan> { return apiRequest(`/api/plans/${id}`) },
  items(id: number, page = 0, size = 20): Promise<PageResponse<PlanItem>> {
    const params = new URLSearchParams({ page: String(page), size: String(size), sort: 'id,asc' })
    return apiRequest(`/api/plans/${id}/items?${params}`)
  },
  create(body: CreatePlanRequest): Promise<PlanCommandResponse> {
    return apiRequest('/api/plans', { method: 'POST', body })
  },
  edit(id: number, body: EditPlanRequest): Promise<PlanCommandResponse> {
    return apiRequest(`/api/plans/${id}`, { method: 'PATCH', body })
  },
  submit(id: number, version: number): Promise<PlanCommandResponse> {
    return apiRequest(`/api/plans/${id}/submit`, { method: 'POST', body: { version } })
  },
}
