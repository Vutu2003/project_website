import { apiRequest } from './client'
import type { ReportResponse, SaveReportRequest, ReportEvidence } from '../types/report'

export const reportsApi = {
  evidence(planId: number): Promise<ReportEvidence> {
    return apiRequest(`/api/plans/${planId}/report/evidence`)
  },
  get(planId: number): Promise<ReportResponse> {
    return apiRequest(`/api/plans/${planId}/report`)
  },
  create(planId: number, body: SaveReportRequest): Promise<ReportResponse> {
    return apiRequest(`/api/plans/${planId}/report`, { method: 'POST', body })
  },
  edit(planId: number, body: SaveReportRequest): Promise<ReportResponse> {
    return apiRequest(`/api/plans/${planId}/report`, { method: 'PUT', body })
  },
  finalize(planId: number, version: number): Promise<ReportResponse> {
    return apiRequest(`/api/plans/${planId}/report/finalize`, { method: 'POST', body: { version } })
  },
}
