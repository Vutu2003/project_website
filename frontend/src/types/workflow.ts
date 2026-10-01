export const planStatuses = ['DRAFT', 'SUBMITTED', 'REVISION_REQUIRED', 'APPROVED', 'IN_PROGRESS', 'AWAITING_REPORT', 'REPORTED', 'CLOSED'] as const
export type PlanStatus = (typeof planStatuses)[number]
export const itemStatuses = ['PLANNED', 'UNDER_CONTRACT', 'PENDING_PROPOSAL', 'WAITING_VENDOR_APPROVAL', 'ASSIGNED_EXTERNAL', 'IN_MAINTENANCE', 'AWAITING_TECHNICAL_ACCEPTANCE', 'AWAITING_HANDOVER', 'COMPLETED', 'REWORK_REQUIRED', 'REPAIR_REQUIRED'] as const
export type PlanItemStatus = (typeof itemStatuses)[number]
export type ApprovalRequestType = 'PLAN_APPROVAL' | 'VENDOR_SELECTION'
export type ApprovalRequestStatus = 'DRAFT' | 'PENDING' | 'DECIDED'
export type ApprovalOutcome = 'APPROVE' | 'REVISION_REQUIRED'
export type CoverageClassification = 'UNKNOWN' | 'FREE' | 'NOT_FREE'
export type AssignmentRoute = 'UNDER_CONTRACT' | 'EXTERNAL_APPROVED'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export interface Plan {
  id: number
  title: string
  periodStart: string
  periodEnd: string
  status: PlanStatus
  createdAt: string
  createdByUserId: number
  createdByName: string
  version: number
}

export interface PlanItem {
  id: number
  planId: number
  equipmentId: number
  equipmentCode: string
  equipmentName: string
  departmentIdAtPlan: number
  departmentNameAtPlan: string
  plannedDate: string | null
  status: PlanItemStatus
  assignedProviderId: number | null
  assignedProviderName: string | null
  assignmentRoute: AssignmentRoute | null
  version: number
}

export interface PlanItemInput { equipmentId: number; plannedDate: string | null }
export interface CreatePlanRequest { title: string; periodStart: string; periodEnd: string; items: PlanItemInput[] }
export interface EditPlanRequest extends CreatePlanRequest { version: number }
export interface PlanCommandResponse { id: number; status: PlanStatus; version: number; approvalRequestId: number | null }

export interface Equipment {
  id: number
  equipmentCode: string
  name: string
  model: string | null
  serialNumber: string | null
  active: boolean
  departmentId: number | null
  departmentCode: string | null
  departmentName: string | null
}
export interface Department { id: number; code: string; name: string; active: boolean }
export interface Provider { id: number; code: string; name: string; contactDetails?: string | null; active: boolean }

export interface ApprovalQueueItem {
  id: number
  requestType: ApprovalRequestType
  status: ApprovalRequestStatus
  submittedAt: string | null
  createdByUserId: number
  createdByName: string
  planId: number | null
  planTitle: string | null
  planItemId: number | null
  equipmentCode: string | null
  proposedProviderId: number | null
  proposedProviderName: string | null
}
export interface ApprovalReview {
  id: number
  requestType: ApprovalRequestType
  status: ApprovalRequestStatus
  submittedAt: string | null
  createdByName: string
  planId: number | null
  planTitle: string | null
  planStatus: PlanStatus | null
  planVersion: number | null
  planItemId: number | null
  itemStatus: PlanItemStatus | null
  itemVersion: number | null
  equipmentCode: string | null
  equipmentName: string | null
  coverageId: number | null
  coverageClassification: CoverageClassification | null
  coverageBasis: string | null
  proposedProviderId: number | null
  proposedProviderName: string | null
  rationale: string | null
  warrantyImpactNote: string | null
}
export interface ApprovalDecisionRequest { version: number; outcome: ApprovalOutcome; comment: string | null }
export interface PlanApprovalDecisionResponse {
  requestId: number; actionId: number; planId: number; outcome: ApprovalOutcome
  planStatus: PlanStatus; planVersion: number
}

export interface CoverageEvidence {
  id: number
  equipmentId: number
  classification: CoverageClassification
  providerId: number | null
  providerName: string | null
  providerActive: boolean | null
  contractReference: string | null
  coverageScope: string | null
  effectiveFrom: string | null
  effectiveTo: string | null
  verifiedByName: string | null
  verifiedAt: string | null
  basisNote: string | null
}
export interface ItemWorkflowResponse {
  itemId: number
  status: PlanItemStatus
  version: number
  assignmentRoute: AssignmentRoute | null
  providerId: number | null
  coverageId: number | null
  approvalRequestId: number | null
  approvalStatus: ApprovalRequestStatus | null
  approvalActionId: number | null
  outcome: ApprovalOutcome | null
}
export interface VendorDraft {
  id: number
  itemId: number
  itemVersion: number
  providerId: number | null
  providerName: string | null
  rationale: string | null
  warrantyImpactNote: string | null
}
export interface VendorProposalRequest {
  version: number
  providerId: number | null
  rationale: string | null
  warrantyImpactNote: string | null
}
