import { apiRequest } from './client'
import type { EquipmentExecutionHistory, ExecutionWorkflowResponse, ProgressResponse, MaintenanceProgressStatus } from '../types/execution'
import { historyApi } from './historyApi'

export const executionsApi = {
  history(equipmentId: number): Promise<EquipmentExecutionHistory> {
    return historyApi.get(equipmentId)
  },
  start(itemId: number, version: number, planVersion: number): Promise<ExecutionWorkflowResponse> {
    return apiRequest(`/api/plan-items/${itemId}/executions`, {
      method: 'POST', body: { version, planVersion },
    })
  },
  progress(executionId: number, workNote: string, damageNote: string | null): Promise<ProgressResponse> {
    return apiRequest(`/api/executions/${executionId}/progress`, {
      method: 'POST', body: { workNote, damageNote },
    })
  },
  updateProgress(executionId: number, status: MaintenanceProgressStatus, note: string | null, version: number): Promise<ProgressResponse> {
    return apiRequest(`/api/executions/${executionId}/progress`, {
      method: 'POST', body: { status, note, version },
    })
  },
  finish(executionId: number, version: number, resultNote: string | null): Promise<ExecutionWorkflowResponse> {
    return apiRequest(`/api/executions/${executionId}/complete-work`, {
      method: 'POST', body: { version, resultNote },
    })
  },
  repair(executionId: number, version: number, reason: string): Promise<ExecutionWorkflowResponse> {
    return apiRequest(`/api/executions/${executionId}/repair-required`, {
      method: 'POST', body: { version, reason },
    })
  },
}
