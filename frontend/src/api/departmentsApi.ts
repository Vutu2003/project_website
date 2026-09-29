import { apiRequest } from './client'
import type { Department } from '../types/workflow'

export const departmentsApi = {
  list(): Promise<Department[]> { return apiRequest('/api/departments') },
}
