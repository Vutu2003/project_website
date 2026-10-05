import { apiRequest } from './client'
import type { ReportResponse, SaveReportRequest, ReportEvidence, ReportDelivery, ReceivedReport } from '../types/report'

export const reportsApi = {
  delivery(planId:number):Promise<ReportDelivery>{return apiRequest(`/api/plans/${planId}/report/delivery`)},
  send(planId:number,version:number):Promise<ReportDelivery>{return apiRequest(`/api/plans/${planId}/report/send`,{method:'POST',body:{version}})},
  received(page=0,size=10):Promise<import('../types/workflow').PageResponse<ReceivedReport>>{return apiRequest(`/api/received-reports?page=${page}&size=${size}`)},
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
