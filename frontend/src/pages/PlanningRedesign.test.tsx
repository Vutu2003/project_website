import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { PlanFormPage } from './PlanFormPage'
import { ExecutionItemPage } from './ExecutionItemPage'
import { executionsApi } from '../api/executionsApi'
import { PlanDetailPage } from './PlanDetailPage'
import { MaintenanceSuggestionsPage } from './MaintenanceSuggestionsPage'
import { NotificationsPage } from './NotificationsPage'
import { NotificationBell } from '../components/NotificationBell'
import { RoleGuard } from '../auth/RoleGuard'
import { plansApi } from '../api/plansApi'
import { equipmentApi } from '../api/equipmentApi'
import { providersApi } from '../api/providersApi'
import { maintenanceSuggestionsApi } from '../api/maintenanceSuggestionsApi'
import { notificationsApi } from '../api/notificationsApi'
import { apiRequest } from '../api/client'
import { itemCompleteness } from '../utils/itemCompleteness'
import { freeCoverageReason } from '../utils/planningDecision'
import type { CoverageEvidence, MaintenanceSuggestion, Plan, PlanItem, PlanStatus } from '../types/workflow'
import type { Role } from '../types/auth'
const auth = vi.hoisted(() => ({ user: { id: 1, role: 'PHONG_VTYT' as Role } }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => auth }))
vi.mock('../api/plansApi', () => ({ plansApi: { detail: vi.fn(), items: vi.fn(), submit: vi.fn(), create: vi.fn(), edit: vi.fn() } }))
vi.mock('../api/equipmentApi', () => ({ equipmentApi: { list: vi.fn(), coverages: vi.fn() } }))
vi.mock('../api/providersApi', () => ({ providersApi: { list: vi.fn() } }))
vi.mock('../api/maintenanceSuggestionsApi', () => ({ maintenanceSuggestionsApi: { list: vi.fn() } }))
vi.mock('../api/notificationsApi', () => ({ notificationsApi: { list: vi.fn(), unread: vi.fn(), read: vi.fn(), readAll: vi.fn() }, notificationsChanged: 'changed' }))
vi.mock('../api/executionsApi', () => ({ executionsApi: { history: vi.fn(), start: vi.fn() } }))
vi.mock('../api/client', () => ({ apiRequest: vi.fn() }))
const plan: Plan = { id: 10, title: 'Kế hoạch mới', periodStart: '2026-11-01', periodEnd: '2026-11-30', status: 'DRAFT', version: 2, createdAt: '2026-10-02T00:00:00Z', createdByUserId: 1, createdByName: 'VTYT' }
const item: PlanItem = { id: 31, planId: 10, equipmentId: 1, equipmentCode: 'EQ-01', equipmentName: 'Máy thử', departmentIdAtPlan: 1, departmentNameAtPlan: 'Khoa Nội', plannedDate: '2026-11-15', status: 'PLANNED', version: 0, assignedProviderId: null, assignedProviderName: null, assignmentRoute: null }
const free: CoverageEvidence = { id: 15, equipmentId: 1, classification: 'FREE', providerId: 7, providerName: 'Đơn vị hợp đồng', providerActive: true, contractReference: 'HD-01', coverageScope: 'Định kỳ', effectiveFrom: '2026-01-01', effectiveTo: '2026-12-31', verifiedByName: 'VTYT', verifiedByRole: 'PHONG_VTYT', verifiedAt: '2026-01-01T00:00:00Z', basisNote: 'Hợp đồng hợp lệ' }
const suggestion: MaintenanceSuggestion = { equipmentId: 1, equipmentCode: 'EQ-01', equipmentName: 'Máy thử', departmentId: 1, departmentName: 'Khoa Nội', lastMaintenanceDate: '2026-01-01', latestResult: 'PASS', latestStatus: 'COMPLETED', classification: 'FREE', coverageId: 15, contractReference: 'HD-01', contractualProviderName: 'Đơn vị hợp đồng', lastExternalProviderName: null, suggestedDate: '2026-11-15', referenceDate: '2026-11-15', suggestionBasis: 'Ngày dự kiến trong kế hoạch đang mở', coverageNote: 'Hợp đồng hợp lệ', openPlanIds: [3] }
const notification = { id: 5, notificationType: 'PLAN_APPROVED', title: 'Kế hoạch được duyệt', message: 'Kế hoạch mới', targetUrl: '/plans/10', createdAt: '2026-10-02T00:00:00Z', readAt: null }
function paged<T>(rows: T[]) { return { content: rows, page: 0, size: 10, totalElements: rows.length, totalPages: 1, last: true } }
function page(path = '/plans/10/edit', state?: unknown) { return render(<MemoryRouter initialEntries={[{ pathname: path, state }]}><Routes><Route path="/plans/new" element={<PlanFormPage mode="create" />} /><Route path="/plans/:planId/edit" element={<PlanFormPage mode="edit" />} /><Route path="/plans/:planId/items/:itemId/execution" element={<ExecutionItemPage />} /><Route path="/plans/:planId" element={<PlanDetailPage />} /><Route path="/maintenance-suggestions" element={<RoleGuard roles={['PHONG_VTYT']}><MaintenanceSuggestionsPage /></RoleGuard>} /><Route path="/notifications" element={<NotificationsPage />} /><Route path="/unauthorized" element={<p>Không có quyền</p>} /></Routes></MemoryRouter>) }
afterEach(() => { cleanup(); vi.restoreAllMocks() })
beforeEach(() => {
 vi.resetAllMocks(); auth.user.role = 'PHONG_VTYT'
 vi.spyOn(window, 'confirm').mockReturnValue(true)
 vi.mocked(plansApi.detail).mockResolvedValue({ ...plan }); vi.mocked(plansApi.items).mockResolvedValue(paged([item]))
 vi.mocked(equipmentApi.list).mockResolvedValue(paged([{ id: 1, equipmentCode: 'EQ-01', name: 'Máy thử', active: true, departmentId: 1, departmentName: 'Khoa Nội', departmentCode: 'NOI', model: null, serialNumber: null }]))
 vi.mocked(equipmentApi.coverages).mockResolvedValue([free]); vi.mocked(providersApi.list).mockResolvedValue([{ id: 7, code: 'P07', name: 'Đơn vị hợp đồng', active: true }, { id: 8, code: 'P08', name: 'Đơn vị ngoài', active: true }, { id: 9, code: 'P09', name: 'Ngừng hoạt động', active: false }])
 vi.mocked(plansApi.create).mockResolvedValue({ id: 10, status: 'DRAFT', version: 0, approvalRequestId: null }); vi.mocked(plansApi.edit).mockResolvedValue({ id: 10, status: 'DRAFT', version: 3, approvalRequestId: null })
 vi.mocked(maintenanceSuggestionsApi.list).mockResolvedValue(paged([suggestion])); vi.mocked(notificationsApi.list).mockResolvedValue(paged([notification])); vi.mocked(notificationsApi.unread).mockResolvedValue({ count: 3 }); vi.mocked(notificationsApi.read).mockResolvedValue({ ...notification, readAt: '2026-10-02T01:00:00Z' }); vi.mocked(notificationsApi.readAll).mockResolvedValue()
 vi.mocked(apiRequest).mockResolvedValue({ id: 1, equipmentCode: 'EQ-01', name: 'Máy thử', active: true, departmentName: 'Khoa Nội' })
})
describe('Planning decisions', () => {
 it('displays FREE evidence and saves contractual coverage', async () => { page(); fireEvent.click(await screen.findByLabelText('Theo hợp đồng')); fireEvent.click(await screen.findByRole('radio', { name: /HD-01/ })); expect(screen.getByText('Căn cứ: Hợp đồng hợp lệ')).toBeTruthy(); fireEvent.click(screen.getByRole('button', { name: 'Lưu chỉnh sửa' })); await waitFor(() => expect(plansApi.edit).toHaveBeenCalledWith(10, expect.objectContaining({ items: [expect.objectContaining({ classification: 'FREE', coverageId: 15, proposedProviderId: null })] }))) })
 it('prepares NOT_FREE provider and basis in editable form', async () => { page(); fireEvent.click(await screen.findByLabelText('Ngoài hợp đồng')); fireEvent.change(screen.getByLabelText('Đơn vị đề xuất EQ-01'), { target: { value: '8' } }); fireEvent.change(screen.getByLabelText('Căn cứ chọn đơn vị EQ-01'), { target: { value: 'Đủ năng lực' } }); expect(screen.getByText('✓ Đã đủ thông tin')).toBeTruthy(); expect(screen.queryByRole('option', { name: 'Ngừng hoạt động' })).toBeNull(); fireEvent.click(screen.getByRole('button', { name: 'Lưu chỉnh sửa' })); await waitFor(() => expect(plansApi.edit).toHaveBeenCalledWith(10, expect.objectContaining({ items: [expect.objectContaining({ classification: 'NOT_FREE', proposedProviderId: 8, rationale: 'Đủ năng lực' })] }))) })
 it('shows missing proposed provider', async () => { page(); fireEvent.click(await screen.findByLabelText('Ngoài hợp đồng')); expect(screen.getByText('⚠ Chưa chọn đơn vị đề xuất')).toBeTruthy() })
 it('shows missing basis', async () => { page(); fireEvent.click(await screen.findByLabelText('Ngoài hợp đồng')); fireEvent.change(screen.getByLabelText('Đơn vị đề xuất EQ-01'), { target: { value: '8' } }); expect(screen.getByText('⚠ Chưa nhập căn cứ chọn đơn vị')).toBeTruthy() })
 it('blocks UC03 and identifies equipment when incomplete', async () => { page('/plans/10'); fireEvent.click(await screen.findByRole('button', { name: 'Gửi phê duyệt' })); expect(await screen.findByText(/Thiết bị EQ-01 chưa đủ/)).toBeTruthy(); expect(plansApi.submit).not.toHaveBeenCalled() })
 it('submits complete external preparation', async () => { vi.mocked(plansApi.items).mockResolvedValue(paged([{ ...item, status: 'PENDING_PROPOSAL', proposedProviderId: 8, rationale: 'Đủ năng lực' }])); vi.mocked(plansApi.submit).mockResolvedValue({ id: 10, status: 'SUBMITTED', version: 3, approvalRequestId: 20 }); page('/plans/10'); fireEvent.click(await screen.findByRole('button', { name: 'Gửi phê duyệt' })); await waitFor(() => expect(plansApi.submit).toHaveBeenCalledWith(10, 2)) })
 it.each<PlanStatus>(['SUBMITTED', 'APPROVED', 'IN_PROGRESS', 'AWAITING_REPORT', 'REPORTED', 'CLOSED'])('locks all controls in %s', async status => { vi.mocked(plansApi.detail).mockResolvedValue({ ...plan, status }); page(); const radio = await screen.findByLabelText('Theo hợp đồng'); expect((radio as HTMLInputElement).closest('fieldset')?.disabled).toBe(true); expect((screen.getByLabelText('Tiêu đề') as HTMLInputElement).closest('fieldset')?.disabled).toBe(true); expect(screen.queryByRole('button', { name: 'Lưu chỉnh sửa' })).toBeNull() })
 it('revision restores method/date/provider editing', async () => { vi.mocked(plansApi.detail).mockResolvedValue({ ...plan, status: 'REVISION_REQUIRED' }); page(); const radio = await screen.findByLabelText('Ngoài hợp đồng'); expect((radio as HTMLInputElement).closest('fieldset')?.disabled).toBe(false); fireEvent.click(radio); expect(screen.getByLabelText('Đơn vị đề xuất EQ-01')).toBeTruthy() })
 it.each<PlanStatus>(['SUBMITTED', 'APPROVED'])('has no obsolete UC05/UC06 action in %s', async status => { vi.mocked(plansApi.detail).mockResolvedValue({ ...plan, status }); page('/plans/10'); await screen.findByText('Kế hoạch mới'); expect(screen.queryByText('Xác định hình thức & đối tác')).toBeNull(); expect(screen.queryByText('Đề xuất đơn vị')).toBeNull(); expect(screen.queryByText('UNKNOWN')).toBeNull() })
 it('creates a plan with FREE selected before saving', async () => { page('/plans/new'); fireEvent.click(await screen.findByRole('button', { name: 'Thêm EQ-01' })); fireEvent.change(screen.getByLabelText('Tiêu đề'), { target: { value: 'Thử mới' } }); fireEvent.change(screen.getByLabelText('Ngày bắt đầu'), { target: { value: '2026-11-01' } }); fireEvent.change(screen.getByLabelText('Ngày kết thúc'), { target: { value: '2026-11-30' } }); fireEvent.click(screen.getByLabelText('Theo hợp đồng')); fireEvent.click(await screen.findByRole('radio', { name: /HD-01/ })); fireEvent.click(screen.getByRole('button', { name: 'Tạo kế hoạch' })); await waitFor(() => expect(plansApi.create).toHaveBeenCalledWith(expect.objectContaining({ items: [expect.objectContaining({ classification: 'FREE', coverageId: 15 })] }))) })
})
describe('Suggestions', () => {
 it('aggregates history coverage and basis for VTYT', async () => { page('/maintenance-suggestions'); expect(await screen.findByText('EQ-01 · Máy thử')).toBeTruthy(); expect(screen.getByText('Ngày dự kiến trong kế hoạch đang mở')).toBeTruthy(); expect(screen.getByText(/Đơn vị hợp đồng/)).toBeTruthy(); expect(screen.getByText(/Đang có trong kế hoạch/)).toBeTruthy() })
 it.each<Role>(['BAN_GIAM_DOC', 'KHOA_PHONG', 'ADMIN'])('denies %s', async role => { auth.user.role = role; page('/maintenance-suggestions'); expect(await screen.findByText('Không có quyền')).toBeTruthy(); expect(maintenanceSuggestionsApi.list).not.toHaveBeenCalled() })
 it('handles insufficient time data', async () => { vi.mocked(maintenanceSuggestionsApi.list).mockResolvedValue(paged([{ ...suggestion, suggestedDate: null, suggestionBasis: 'Chưa đủ dữ liệu' }])); page('/maintenance-suggestions'); expect(await screen.findByText('Chưa đủ dữ liệu để đề xuất thời gian')).toBeTruthy() })
 it('direct action preselects equipment and FREE without persisting', async () => { page('/maintenance-suggestions'); fireEvent.click(await screen.findByRole('button', { name: 'Đưa vào kế hoạch' })); expect(await screen.findByText('EQ-01 · Máy thử')).toBeTruthy(); expect((screen.getByLabelText('Theo hợp đồng') as HTMLInputElement).checked).toBe(true); expect((screen.getByLabelText('Ngày dự kiến EQ-01') as HTMLInputElement).value).toBe('2026-11-15'); expect(plansApi.create).not.toHaveBeenCalled() })
 it('multi-select uses same creation flow', async () => { page('/maintenance-suggestions'); fireEvent.click(await screen.findByRole('checkbox')); fireEvent.click(screen.getByRole('button', { name: 'Tạo kế hoạch từ 1 thiết bị đã chọn' })); expect(await screen.findByText('Kế hoạch bảo trì mới')).toBeTruthy() })
 it('external prefill never chooses a historical provider', async () => { page('/plans/new', { suggestions: [{ ...suggestion, classification: 'NOT_FREE', coverageId: null, lastExternalProviderName: 'Đơn vị ngoài' }] }); const select = await screen.findByLabelText('Đơn vị đề xuất EQ-01'); expect((select as HTMLSelectElement).value).toBe('') })
})
describe('Notifications', () => {
 it('bell shows unread count and preview', async () => { render(<MemoryRouter><NotificationBell /></MemoryRouter>); const bell = await screen.findByRole('button', { name: 'Thông báo: 3 chưa đọc' }); fireEvent.click(bell); expect(await screen.findByText('Kế hoạch được duyệt')).toBeTruthy(); expect(screen.getByRole('link', { name: 'Xem tất cả' })).toBeTruthy() })
 it('list marks a notification read before navigating', async () => { page('/notifications'); fireEvent.click(await screen.findByRole('button', { name: /Kế hoạch được duyệt/ })); await waitFor(() => expect(notificationsApi.read).toHaveBeenCalledWith(5)); expect(await screen.findByText('Kế hoạch mới')).toBeTruthy() })
 it('marks all read and reloads list', async () => { page('/notifications'); await screen.findByText(/Kế hoạch được duyệt/); fireEvent.click(screen.getByRole('button', { name: 'Đánh dấu tất cả đã đọc' })); await waitFor(() => expect(notificationsApi.readAll).toHaveBeenCalled()); await waitFor(() => expect(notificationsApi.list).toHaveBeenCalledTimes(2)) })
 it('handles empty list', async () => { vi.mocked(notificationsApi.list).mockResolvedValue(paged([])); page('/notifications'); expect(await screen.findByText('Chưa có thông báo.')).toBeTruthy() })
 it('shows ownership denial without navigating', async () => { const { ApiError } = await import('../api/types'); vi.mocked(notificationsApi.read).mockRejectedValue(new ApiError(404, 'NOTIFICATION_NOT_FOUND', 'Không tìm thấy')); page('/notifications'); fireEvent.click(await screen.findByRole('button', { name: /Kế hoạch được duyệt/ })); expect(await screen.findByText(/Không tìm thấy dữ liệu/)).toBeTruthy(); expect(plansApi.detail).not.toHaveBeenCalled() })
})
describe('Coverage and completeness validation', () => {
 it.each([
  [{ providerActive: false }, 'Đơn vị hợp đồng không hoạt động'], [{ verifiedAt: null }, 'Thiếu căn cứ xác minh hợp đồng'], [{ basisNote: ' ' }, 'Thiếu căn cứ xác minh hợp đồng'], [{ verifiedByRole: 'ADMIN' }, 'Thiếu căn cứ xác minh hợp đồng'], [{ equipmentId: 99 }, 'Hồ sơ khác thiết bị'], [{ effectiveTo: '2026-10-01' }, 'Hợp đồng không áp dụng vào ngày tham chiếu'], [{ effectiveFrom: '2027-01-01' }, 'Hợp đồng không áp dụng vào ngày tham chiếu'],
 ])('rejects invalid evidence %j', (patch, reason) => { expect(freeCoverageReason({ ...free, ...patch } as CoverageEvidence, 1, '2026-11-15')).toBe(reason) })
 it('includes boundary dates', () => { expect(freeCoverageReason(free, 1, '2026-01-01')).toBeNull(); expect(freeCoverageReason(free, 1, '2026-12-31')).toBeNull() })
 it('requires an explicit method', () => { expect(itemCompleteness({ equipmentId: 1, plannedDate: null }, '2026-11-01')).toBe('Chưa chọn hình thức bảo trì') })
 it('renders information within the item card', async () => { page(); const card = await screen.findByRole('article', { name: 'Hạng mục EQ-01' }); expect(within(card).getByText('Khoa Nội')).toBeTruthy() })
})

describe('Prepared vendor execution gate', () => {
 it.each([true, false])('gates direct execution URL when pending=%s', async pendingVendorApproval => {
  vi.mocked(plansApi.detail).mockResolvedValue({ ...plan, status: 'APPROVED', pendingVendorApproval })
  vi.mocked(plansApi.items).mockResolvedValue(paged([{ ...item, status: 'UNDER_CONTRACT', assignmentRoute: 'UNDER_CONTRACT', assignedProviderName: 'Đơn vị hợp đồng' }]))
  vi.mocked(executionsApi.history).mockResolvedValue({ equipmentId: 1, equipmentCode: 'EQ-01', equipmentName: 'Máy thử', currentDepartmentId: 1, campaigns: [] })
  page('/plans/10/items/31/execution')
  await screen.findByText('TRẠNG THÁI HẠNG MỤC')
  expect(Boolean(screen.queryByRole('button', { name: 'Bắt đầu bảo trì' }))).toBe(!pendingVendorApproval)
  expect(Boolean(screen.queryByText('Cần phê duyệt xong các đơn vị đề xuất trước khi bắt đầu thực hiện kế hoạch.'))).toBe(pendingVendorApproval)
  expect(executionsApi.start).not.toHaveBeenCalled()
 })
})
