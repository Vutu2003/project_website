import { MaintenanceSuggestionsPage } from './pages/MaintenanceSuggestionsPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { Route, Routes } from 'react-router'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppLayout } from './layouts/AppLayout'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage, UnauthorizedPage } from './pages/PlaceholderPage'
import { PlanListPage } from './pages/PlanListPage'
import { PlanFormPage } from './pages/PlanFormPage'
import { PlanDetailPage } from './pages/PlanDetailPage'
import { ApprovalQueuePage } from './pages/ApprovalQueuePage'
import { ApprovalDetailPage } from './pages/ApprovalDetailPage'
import { RoleGuard } from './auth/RoleGuard'
import { MaintenanceProgressPage } from './pages/MaintenanceProgressPage'
import { ExecutionQueuePage } from './pages/ExecutionQueuePage'
import { ExecutionItemPage } from './pages/ExecutionItemPage'
import { ReportListPage } from './pages/ReportListPage'
import { ReportDetailPage } from './pages/ReportDetailPage'
import { EquipmentListPage } from './pages/EquipmentListPage'
import { EquipmentHistoryPage } from './pages/EquipmentHistoryPage'

import { AdminCatalogListPage, AdminCatalogDetailPage, AdminCatalogFormPage } from './pages/AdminCatalogPages'
import { AdminAccountListPage } from './pages/AdminAccountListPage'
import { AdminAccountFormPage } from './pages/AdminAccountFormPage'
import { AdminAccountDetailPage } from './pages/AdminAccountDetailPage'

export default function App() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route element={<ProtectedRoute />}>
      <Route element={<AppLayout />}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/maintenance-suggestions" element={<RoleGuard roles={['PHONG_VTYT']}><MaintenanceSuggestionsPage /></RoleGuard>} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/plans" element={<PlanListPage />} />
        <Route path="/plans/new" element={<RoleGuard roles={['PHONG_VTYT']}><PlanFormPage mode="create" /></RoleGuard>} />
        <Route path="/plans/:planId/edit" element={<RoleGuard roles={['PHONG_VTYT']}><PlanFormPage mode="edit" /></RoleGuard>} />
        <Route path="/plans/:planId" element={<PlanDetailPage />} />
        <Route path="/maintenance-progress" element={<RoleGuard roles={['PHONG_VTYT']}><MaintenanceProgressPage /></RoleGuard>} />
        <Route path="/maintenance-progress/plans/:planId" element={<RoleGuard roles={['PHONG_VTYT']}><MaintenanceProgressPage key="progress-plan" /></RoleGuard>} />
        <Route path="/execution" element={<RoleGuard roles={['PHONG_VTYT', 'KHOA_PHONG']}><ExecutionQueuePage /></RoleGuard>} />
        <Route path="/execution/plans/:planId" element={<RoleGuard roles={['PHONG_VTYT', 'KHOA_PHONG']}><ExecutionQueuePage /></RoleGuard>} />
        <Route path="/plans/:planId/items/:itemId/execution" element={<RoleGuard roles={['PHONG_VTYT', 'KHOA_PHONG']}><ExecutionItemPage /></RoleGuard>} />
        <Route path="/approvals" element={<RoleGuard roles={['BAN_GIAM_DOC']}><ApprovalQueuePage /></RoleGuard>} />
        <Route path="/approvals/:requestId" element={<RoleGuard roles={['BAN_GIAM_DOC']}><ApprovalDetailPage /></RoleGuard>} />
        <Route path="/reports" element={<RoleGuard roles={['PHONG_VTYT', 'BAN_GIAM_DOC']}><ReportListPage /></RoleGuard>} />
        <Route path="/plans/:planId/report" element={<RoleGuard roles={['PHONG_VTYT', 'BAN_GIAM_DOC']}><ReportDetailPage /></RoleGuard>} />
        <Route path="/equipment" element={<RoleGuard roles={['PHONG_VTYT', 'BAN_GIAM_DOC', 'KHOA_PHONG']}><EquipmentListPage /></RoleGuard>} />
        <Route path="/equipment/:equipmentId/history" element={<RoleGuard roles={['PHONG_VTYT', 'BAN_GIAM_DOC', 'KHOA_PHONG']}><EquipmentHistoryPage /></RoleGuard>} />
        <Route path="/admin/catalogs/departments" element={<RoleGuard roles={['ADMIN']}><AdminCatalogListPage kind="departments" /></RoleGuard>} />
        <Route path="/admin/catalogs/departments/new" element={<RoleGuard roles={['ADMIN']}><AdminCatalogFormPage kind="departments" mode="create" /></RoleGuard>} />
        <Route path="/admin/catalogs/departments/:id" element={<RoleGuard roles={['ADMIN']}><AdminCatalogDetailPage kind="departments" /></RoleGuard>} />
        <Route path="/admin/catalogs/departments/:id/edit" element={<RoleGuard roles={['ADMIN']}><AdminCatalogFormPage kind="departments" mode="edit" /></RoleGuard>} />
        <Route path="/admin/catalogs/providers" element={<RoleGuard roles={['ADMIN']}><AdminCatalogListPage kind="providers" /></RoleGuard>} />
        <Route path="/admin/catalogs/providers/new" element={<RoleGuard roles={['ADMIN']}><AdminCatalogFormPage kind="providers" mode="create" /></RoleGuard>} />
        <Route path="/admin/catalogs/providers/:id" element={<RoleGuard roles={['ADMIN']}><AdminCatalogDetailPage kind="providers" /></RoleGuard>} />
        <Route path="/admin/catalogs/providers/:id/edit" element={<RoleGuard roles={['ADMIN']}><AdminCatalogFormPage kind="providers" mode="edit" /></RoleGuard>} />
        <Route path="/admin/accounts" element={<RoleGuard roles={['ADMIN']}><AdminAccountListPage /></RoleGuard>} />
        <Route path="/admin/accounts/new" element={<RoleGuard roles={['ADMIN']}><AdminAccountFormPage mode="create" /></RoleGuard>} />
        <Route path="/admin/accounts/:id" element={<RoleGuard roles={['ADMIN']}><AdminAccountDetailPage /></RoleGuard>} />
        <Route path="/admin/accounts/:id/edit" element={<RoleGuard roles={['ADMIN']}><AdminAccountFormPage mode="edit" /></RoleGuard>} />
        <Route path="/unauthorized" element={<UnauthorizedPage />} />
      </Route>
    </Route>
    <Route path="*" element={<NotFoundPage />} />
  </Routes>
}
