import type { AcceptanceResult, AcceptanceType } from '../types/execution'
import type { ApprovalOutcome, ApprovalRequestStatus, ApprovalRequestType, AssignmentRoute, CoverageClassification, PlanItemStatus, PlanStatus } from '../types/workflow'

export const planStatusLabels: Record<PlanStatus, string> = {
  DRAFT: 'Nháp', SUBMITTED: 'Đã gửi duyệt', REVISION_REQUIRED: 'Yêu cầu chỉnh sửa',
  APPROVED: 'Đã phê duyệt', IN_PROGRESS: 'Đang thực hiện', AWAITING_REPORT: 'Chờ báo cáo',
  REPORTED: 'Đã báo cáo', CLOSED: 'Đã đóng',
}
export const itemStatusLabels: Record<PlanItemStatus, string> = {
  PLANNED: 'Dự kiến', UNDER_CONTRACT: 'Theo hợp đồng', PENDING_PROPOSAL: 'Chờ đề xuất đơn vị',
  WAITING_VENDOR_APPROVAL: 'Chờ phê duyệt đơn vị', ASSIGNED_EXTERNAL: 'Đã phân công đơn vị ngoài',
  IN_MAINTENANCE: 'Đang bảo trì', AWAITING_TECHNICAL_ACCEPTANCE: 'Chờ nghiệm thu kỹ thuật',
  AWAITING_HANDOVER: 'Chờ bàn giao', COMPLETED: 'Hoàn tất',
  REWORK_REQUIRED: 'Yêu cầu thực hiện lại', REPAIR_REQUIRED: 'Chuyển sửa chữa',
}
export const approvalTypeLabels: Record<ApprovalRequestType, string> = {
  PLAN_APPROVAL: 'Duyệt kế hoạch', VENDOR_SELECTION: 'Duyệt đơn vị bảo trì',
}
export const approvalStatusLabels: Record<ApprovalRequestStatus, string> = {
  DRAFT: 'Nháp', PENDING: 'Chờ quyết định', DECIDED: 'Đã quyết định',
}
export const approvalOutcomeLabels: Record<ApprovalOutcome, string> = {
  APPROVE: 'Phê duyệt', REVISION_REQUIRED: 'Yêu cầu chỉnh sửa',
}
export const coverageLabels: Record<CoverageClassification, string> = {
  UNKNOWN: 'Chưa xác định', FREE: 'Theo hợp đồng', NOT_FREE: 'Ngoài hợp đồng',
}
export const assignmentRouteLabels: Record<AssignmentRoute, string> = {
  UNDER_CONTRACT: 'Theo hợp đồng', EXTERNAL_APPROVED: 'Đơn vị ngoài đã duyệt',
}
export const acceptanceResultLabels: Record<AcceptanceResult, string> = { PASS: 'Đạt', FAIL: 'Không đạt' }
export const acceptanceTypeLabels: Record<AcceptanceType, string> = { TECHNICAL_ACCEPTANCE: 'Nghiệm thu kỹ thuật', HANDOVER_ACCEPTANCE: 'Bàn giao' }
export function businessDate(value: string | null | undefined): string {
  if (!value) return '—'
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  return match ? `${match[3]}/${match[2]}/${match[1]}` : value
}
export function dateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('vi-VN')
}
