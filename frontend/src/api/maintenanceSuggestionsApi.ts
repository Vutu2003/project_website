import { apiRequest } from './client'
import type { MaintenanceSuggestion, PageResponse } from '../types/workflow'
export interface EquipmentFilters { search?: string; activity?: 'all' | 'active' | 'inactive'; referenceDate?: string }
export const maintenanceSuggestionsApi = {
 list(page = 0, size = 10, filters: EquipmentFilters = {}): Promise<PageResponse<MaintenanceSuggestion>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (filters.search?.trim()) query.set('search', filters.search.trim())
  if (filters.activity === 'all') query.set('includeInactive', 'true')
  if (filters.activity === 'inactive') query.set('active', 'false')
  if (filters.referenceDate) query.set('referenceDate', filters.referenceDate)
  return apiRequest(`/api/maintenance-suggestions?${query}`)
 },
}
