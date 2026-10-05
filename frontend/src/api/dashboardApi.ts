import { apiRequest } from './client'
import type { DashboardData } from '../types/dashboard'
export const dashboardApi = { get: () => apiRequest<DashboardData>('/api/dashboard') }
