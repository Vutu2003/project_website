import { apiRequest } from './client'
import type { MaintenanceSuggestion, PageResponse } from '../types/workflow'
export const maintenanceSuggestionsApi = {
 list(page = 0, size = 10): Promise<PageResponse<MaintenanceSuggestion>> { return apiRequest(`/api/maintenance-suggestions?page=${page}&size=${size}`) },
}
