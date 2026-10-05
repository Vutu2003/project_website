import { apiRequest } from './client'
import type { PageResponse, Plan, PlanStatus, PlanItemStatus } from '../types/workflow'
import type { MaintenanceProgressStatus } from '../types/execution'
export interface TrackingItem {item_id:number;equipment_id:number;equipment_code:string;equipment_name:string;department_name:string;provider_name:string|null;status:PlanItemStatus;version:number;execution_id:number|null;started_at:string|null;ended_at:string|null;updated_at:string|null;work_note:string|null;damage_note:string|null;progress_status:MaintenanceProgressStatus|'NOT_STARTED'}
export const maintenanceTrackingApi={
 plans:(page=0,status?:PlanStatus):Promise<PageResponse<Plan>>=>apiRequest(`/api/maintenance-tracking-plans?page=${page}&size=10${status?`&status=${status}`:''}`),
 startAll:(planId:number,version:number):Promise<unknown>=>apiRequest(`/api/plans/${planId}/start-maintenance`,{method:'POST',body:{version}}),
 markWorkDone:(planId:number,version:number):Promise<unknown>=>apiRequest(`/api/plans/${planId}/mark-work-done`,{method:'POST',body:{version}}),
 list:(planId:number):Promise<TrackingItem[]>=>apiRequest(`/api/plans/${planId}/tracking`),
 complete:(planId:number,version:number):Promise<{planId:number;reportId:number;planVersion:number;status:string}>=>apiRequest(`/api/plans/${planId}/complete-maintenance`,{method:'POST',body:{version}}),
}
