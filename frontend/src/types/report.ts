import type { PlanStatus } from './workflow'

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
