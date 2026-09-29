import { apiRequest } from './client'
import type { EquipmentExecutionHistory } from '../types/execution'

/** The same scoped UC12 response used by the Phase 4.3 execution page. */
export const historyApi = {
  get(equipmentId: number): Promise<EquipmentExecutionHistory> {
    return apiRequest(`/api/equipment/${equipmentId}/maintenance-history`)
  },
}
