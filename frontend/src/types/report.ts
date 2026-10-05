import type { ExecutionAttempt } from './execution'
import type { PlanItemStatus, PlanStatus } from './workflow'

export type ReportStatus = 'DRAFT' | 'FINAL'
export interface ReportNarrative {
  reportNumber: string | null
  workDone: string | null
  achieved: string | null
  notAchieved: string | null
  causes: string | null
  nextWork: string | null
  resolutions: string | null
  recommendations: string | null
}
export interface SaveReportRequest extends ReportNarrative { version: number }
export interface ReportResponse extends ReportNarrative {
  id: number
  planId: number
  status: ReportStatus
  planStatus: PlanStatus
  planVersion: number
  reportDate: string
  finalizedAt: string | null
  completedCount: number
  repairRequiredCount: number
}

export interface ReportEvidence {
  planId: number
  items: { itemId: number; equipmentCode: string; equipmentName: string; departmentName: string;
    status: PlanItemStatus; providerName: string | null; attempts: ExecutionAttempt[] }[]
}

export interface ReportDelivery {reportId:number;canSend:boolean;departments:{id:number;name:string}[];deliveries:{id:number;department_id:number|null;recipient:string;sent_at:string;sent_by:string}[]}
export interface ReceivedReport {id:number;title:string;period_start:string;period_end:string;status:string;equipment_count:number;sent_at:string;sent_by:string}
