import { apiRequest } from './client'
import type { ApprovalDecisionRequest, ApprovalQueueItem, ApprovalRequestType, ApprovalReview, ItemWorkflowResponse, PageResponse, PlanApprovalDecisionResponse } from '../types/workflow'

export const approvalsApi = {
  pending(page = 0, size = 10, requestType?: ApprovalRequestType): Promise<PageResponse<ApprovalQueueItem>> {
    const params = new URLSearchParams({ page: String(page), size: String(size), sort: 'submittedAt,asc' })
    if (requestType) params.set('requestType', requestType)
    return apiRequest(`/api/approvals/pending?${params}`)
  },
  review(id: number): Promise<ApprovalReview> { return apiRequest(`/api/approvals/${id}`) },
  decide(id: number, body: ApprovalDecisionRequest): Promise<PlanApprovalDecisionResponse | ItemWorkflowResponse> {
    return apiRequest(`/api/approvals/${id}/decision`, { method: 'POST', body })
  },
}
