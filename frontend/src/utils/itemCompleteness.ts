import type { CoverageEvidence, PlanItemInput } from '../types/workflow'
import { freeCoverageReason } from './planningDecision'
interface DraftItem extends PlanItemInput { coverages?: CoverageEvidence[] }
export function itemCompleteness(item: DraftItem, date: string): string | null {
 if (!item.classification) return 'Chưa chọn hình thức bảo trì'
 if (item.classification === 'FREE') {
  const coverage = item.coverages?.find(c => c.id === item.coverageId)
  return coverage ? freeCoverageReason(coverage, item.equipmentId, item.plannedDate || date) : 'Chưa chọn hợp đồng hợp lệ'
 }
 if (!item.proposedProviderId) return 'Chưa chọn đơn vị đề xuất'
 if (!item.rationale?.trim()) return 'Chưa nhập căn cứ chọn đơn vị'
 return null
}
