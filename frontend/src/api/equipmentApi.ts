import { apiRequest } from './client'
import type { CoverageEvidence, Equipment, PageResponse } from '../types/workflow'

export const equipmentApi = {
  list(page = 0, size = 10, active: boolean | undefined = true, search?: string): Promise<PageResponse<Equipment>> {
    const params = new URLSearchParams({ page: String(page), size: String(size), sort: 'equipmentCode,asc' })
    if (active !== undefined) params.set('active', String(active))
    if (search?.trim()) params.set('search', search.trim())
    return apiRequest(`/api/equipment?${params}`)
  },
  coverages(id: number): Promise<CoverageEvidence[]> {
    return apiRequest(`/api/equipment/${id}/coverages`)
  },
}
