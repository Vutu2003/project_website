import type { AssignmentRoute, PlanItemStatus, PlanStatus } from './workflow'
import type { ReportStatus } from './report'

export type AcceptanceResult = 'PASS' | 'FAIL'
export type AcceptanceType = 'TECHNICAL_ACCEPTANCE' | 'HANDOVER_ACCEPTANCE'

export interface ProgressEvidence {
  id: number
  eventAt: string
  workNote: string
  damageNote: string | null
  recordedByUserId: number
}
export interface AcceptanceEvidence {
  id: number
  type: AcceptanceType
  result: AcceptanceResult
  observedAt: string
  conclusion: string
  recordedByUserId: number
  departmentSignerId: number | null
  departmentConfirmedAt: string | null
  vtytSignerId: number | null
  vtytConfirmedAt: string | null
}
export interface ExecutionAttempt {
  executionId: number
  attemptNo: number
  actualProviderId: number
  actualProviderName: string
  startedAt: string
  endedAt: string | null
  resultNote: string | null
  progress: ProgressEvidence[]
  technicalAcceptance: AcceptanceEvidence | null
  handoverAcceptance: AcceptanceEvidence | null
}
export interface StateEvent {
  id: number
  oldState: string | null
  newState: string
  action: string
  reason: string | null
  at: string
  actorUserId: number
}
export interface ReportReference { id: number; status: ReportStatus; reportDate: string; finalizedAt: string | null }
export interface ExecutionCampaign {
  planId: number
  planTitle: string
  planStatus: PlanStatus
  periodStart: string
  periodEnd: string
  itemId: number
  departmentIdAtPlan: number
  itemStatus: PlanItemStatus
  assignmentRoute: AssignmentRoute | null
  assignedProviderId: number | null
  coverageId: number | null
  attempts: ExecutionAttempt[]
  itemHistory: StateEvent[]
  planHistory: StateEvent[]
  report: ReportReference | null
}
export interface EquipmentExecutionHistory {
  equipmentId: number
  equipmentCode: string
  equipmentName: string
  currentDepartmentId: number | null
  campaigns: ExecutionCampaign[]
}
export interface ExecutionWorkflowResponse {
  executionId: number
  itemId: number
  attemptNo: number
  providerId: number
  itemStatus: PlanItemStatus
  itemVersion: number
  planStatus: PlanStatus
  planVersion: number
}
export interface ProgressResponse { progressId: number; executionId: number; eventAt: string }
export interface AcceptanceWorkflowResponse {
  acceptanceId: number
  executionId: number
  type: AcceptanceType
  result: AcceptanceResult
  itemStatus: PlanItemStatus
  itemVersion: number
  planStatus: PlanStatus
  planVersion: number
}
export interface AcceptanceCommand {
  version: number
  result: AcceptanceResult
  conclusion: string
  repairRequired: boolean
}

export type MaintenanceProgressStatus = 'IN_PROGRESS' | 'WORK_DONE' | 'DAMAGE_DETECTED'
