import { apiRequest } from './client'
import { authApi } from './authApi'
import { UserInputError } from '../utils/UserInputError'
import type { AcceptanceCommand, AcceptanceWorkflowResponse } from '../types/execution'

export const acceptancesApi = {
  technical(executionId: number, body: AcceptanceCommand): Promise<AcceptanceWorkflowResponse> {
    return apiRequest(`/api/executions/${executionId}/technical-acceptance`, { method: 'POST', body })
  },
  handover(executionId: number, body: AcceptanceCommand, secondaryVtytToken?: string): Promise<AcceptanceWorkflowResponse> {
    const headers = new Headers()
    if (secondaryVtytToken) headers.set('X-VTYT-Authorization', `Bearer ${secondaryVtytToken}`)
    return apiRequest(`/api/executions/${executionId}/handover`, { method: 'POST', body, headers })
  },
  async signedHandover(executionId: number, body: AcceptanceCommand, username: string, password: string): Promise<AcceptanceWorkflowResponse> {
    const signer = await authApi.login({ username, password })
    if (signer.user.role !== 'PHONG_VTYT') throw new UserInputError('Người xác nhận phải có vai trò Phòng VTYT.')
    return this.handover(executionId, body, signer.accessToken)
  },
}
