import type { Role } from './auth'
import type { PlanStatus, PlanItemStatus } from './workflow'
import type { MaintenanceProgressStatus } from './execution'
export interface DashboardAction { label: string; href: string }
export interface AttentionItem { title: string; detail: string; status: string; at: string | null; href: string; cta: string }
export interface DashboardItem { itemId: number; planId: number; equipmentId: number; equipmentCode: string; equipmentName: string; provider: string | null; status: PlanItemStatus | MaintenanceProgressStatus; at: string | null; note: string | null; actor: string | null }
export interface DashboardReport { planId: number; title: string; year: number; quarter: string; reportDate: string; at: string | null; completed: number; repair: number; status: 'DRAFT' | 'FINAL' }
export interface QuarterOverview { year: number; quarter: string; scheduled: number; underContract: number; outsideContract: number; inProgress: number; completed: number; plan: { id: number; title: string; status: PlanStatus; year: number; quarter: string; equipmentCount: number } | null }
interface DashboardBase { generatedAt: string; quickActions: DashboardAction[] }
export interface VtytDashboardData extends DashboardBase {
 role: 'PHONG_VTYT'
 summary: { scheduled: number; activeContracts: number; maintaining: number; technical: number; awaitingReport: number }
 attention: AttentionItem[]
 currentQuarter: QuarterOverview
 progress: { notStarted: number; inProgress: number; workDone: number; damaged: number; technical: number; handover: number }
 contracts: { valid: number; expiring: number; expired: number }
 expiringContracts: { id: number; code: string; provider: string; endDate: string; equipmentCount: number }[]
 recentProgress: DashboardItem[]
 reports: DashboardReport[]
}
export interface BgdDashboardData extends DashboardBase {
 role: 'BAN_GIAM_DOC'
 summary: { pendingPlans: number; pendingProviders: number; approvedThisMonth: number; revisedThisMonth: number; reportsThisMonth: number }
 pending: { id: number; type: 'PLAN_APPROVAL' | 'VENDOR_SELECTION'; title: string; equipmentCode: string | null; sender: string; at: string }[]
 decisions: { requestId: number; title: string; equipmentCode: string | null; outcome: 'APPROVE' | 'REVISION_REQUIRED'; comment: string | null; actor: string; at: string }[]
 reports: DashboardReport[]
}
export interface KhoaDashboardData extends DashboardBase {
 role: 'KHOA_PHONG'
 departmentId: number; departmentName: string
 summary: { equipment: number; maintaining: number; handover: number; completedRecently: number }
 handover: DashboardItem[]; active: DashboardItem[]; history: DashboardItem[]
}
export interface AdminDashboardData extends DashboardBase {
 role: 'ADMIN'
 summary: { activeAccounts: number; inactiveAccounts: number; departments: number; providers: number; contracts: number }
 accountsByRole: { role: Role; count: number }[]
 quality: { label: string; count: number; href: string }[]
}
export type DashboardData = VtytDashboardData | BgdDashboardData | KhoaDashboardData | AdminDashboardData
