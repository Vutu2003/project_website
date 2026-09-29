import { apiRequest } from './client'
import type { ItemWorkflowResponse, Provider, VendorDraft, VendorProposalRequest } from '../types/workflow'

export const providersApi = {
  list(): Promise<Provider[]> { return apiRequest('/api/providers') },
  route(itemId: number, version: number, coverageId: number): Promise<ItemWorkflowResponse> {
    return apiRequest(`/api/plan-items/${itemId}/route`, { method: 'POST', body: { version, coverageId } })
  },
  draft(itemId: number): Promise<VendorDraft> {
    return apiRequest(`/api/plan-items/${itemId}/vendor-proposals/draft`)
  },
  createDraft(itemId: number, body: VendorProposalRequest): Promise<ItemWorkflowResponse> {
    return apiRequest(`/api/plan-items/${itemId}/vendor-proposals`, { method: 'POST', body })
  },
  submitDraft(requestId: number, body: VendorProposalRequest): Promise<ItemWorkflowResponse> {
    return apiRequest(`/api/vendor-proposals/${requestId}/submit`, { method: 'POST', body })
  },
}
