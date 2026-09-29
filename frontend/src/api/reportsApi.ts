import { apiRequest } from './client'
import type { ReportResponse, SaveReportRequest } from '../types/report'

export const reportsApi = {
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
