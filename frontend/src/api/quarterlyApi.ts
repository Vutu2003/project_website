import { apiRequest } from './client'
import type { PlanCommandResponse } from '../types/workflow'
export type Quarter = 'Q1' | 'Q2' | 'Q3' | 'Q4'
export const quarterLabels: Record<Quarter, string> = { Q1: 'Quý I', Q2: 'Quý II', Q3: 'Quý III', Q4: 'Quý IV' }
export interface QuarterlyEquipment { equipment_id: number; equipment_code: string; equipment_name: string; department_name: string; quarters: string; contract_status: string; classification: string; conflict?: boolean; provider_id?: number; provider_name?: string; contract_id?: number; contract_code?: string; start_date?: string; end_date?: string }
export interface QuarterPreview { year: number; quarter: Quarter; title: string; periodStart: string; periodEnd: string; referenceDate: string; equipment: QuarterlyEquipment[] }
export interface Proposal { equipmentId: number; proposedProviderId: number | null; rationale: string }
export const quarterlyApi = {
 preview: (year: number, quarter: Quarter): Promise<QuarterPreview> => apiRequest(`/api/maintenance-plans/preview?year=${year}&quarter=${quarter}`),
 create: (year: number, quarter: Quarter, proposals: Proposal[]): Promise<PlanCommandResponse> => apiRequest('/api/maintenance-plans', { method: 'POST', body: { year, quarter, proposals } }),
}
