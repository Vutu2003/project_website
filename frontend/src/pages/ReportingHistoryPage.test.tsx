import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { plansApi } from '../api/plansApi'
import { reportsApi } from '../api/reportsApi'
import { equipmentApi } from '../api/equipmentApi'
import { approvalsApi } from '../api/approvalsApi'
import { EquipmentListPage } from './EquipmentListPage'
import { ExecutionQueuePage } from './ExecutionQueuePage'
import { ApprovalDetailPage } from './ApprovalDetailPage'
import { historyApi } from '../api/historyApi'
import type { ReportEvidence, ReportResponse } from '../types/report'
import type { Plan } from '../types/workflow'
import { ReportDetailPage } from './ReportDetailPage'
import { EquipmentHistoryPage } from './EquipmentHistoryPage'

const auth = vi.hoisted(() => ({ user: { role: 'PHONG_VTYT' } }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => auth }))
vi.mock('../api/plansApi', () => ({ plansApi: { detail: vi.fn(), items: vi.fn(), list: vi.fn() } }))
vi.mock('../api/reportsApi', () => ({ reportsApi: { evidence: vi.fn(), get: vi.fn(), create: vi.fn(), edit: vi.fn(), finalize: vi.fn() } }))
vi.mock('../api/equipmentApi', () => ({ equipmentApi: { list: vi.fn() } }))
vi.mock('../api/approvalsApi', () => ({ approvalsApi: { review: vi.fn(), decide: vi.fn() } }))
vi.mock('../api/historyApi', () => ({ historyApi: { get: vi.fn() } }))
const plan = { id: 10, title: 'Đợt bảo trì thử nghiệm', periodStart: '2026-10-01', periodEnd: '2026-10-31', status: 'AWAITING_REPORT', version: 6,
  createdAt: '2026-09-01T00:00:00Z', createdByUserId: 1, createdByName: 'VTYT' } as Plan
const draft = { id: 99, planId: 10, status: 'DRAFT', planStatus: 'AWAITING_REPORT', planVersion: 6, reportDate: '2026-10-01', finalizedAt: null,
  completedCount: 1, repairRequiredCount: 1, reportNumber: null, workDone: 'Đã bảo trì', achieved: null, notAchieved: null,
  causes: null, nextWork: null, resolutions: null, recommendations: null } as ReportResponse
const evidence: ReportEvidence = { planId: 10, items: [
  { itemId: 31, equipmentCode: 'EQ-01', equipmentName: 'Máy thử', departmentName: 'Khoa Nội', status: 'COMPLETED', providerName: 'Đơn vị thử', attempts: [
    { executionId: 48, attemptNo: 1, actualProviderId: 7, actualProviderName: 'Đơn vị thử', startedAt: '2026-10-01T02:00:00Z', endedAt: '2026-10-01T03:00:00Z', resultNote: 'Đã kiểm tra xong',
      progress: [{ id: 1, eventAt: '2026-10-01T02:30:00Z', workNote: 'Đã thay bộ lọc', damageNote: null, recordedByUserId: 1 }],
      technicalAcceptance: { id: 1, type: 'TECHNICAL_ACCEPTANCE', result: 'PASS', observedAt: '2026-10-01T04:00:00Z', conclusion: 'Kết luận kỹ thuật đạt', recordedByUserId: 1, departmentSignerId: null, departmentConfirmedAt: null, vtytSignerId: null, vtytConfirmedAt: null },
      handoverAcceptance: { id: 2, type: 'HANDOVER_ACCEPTANCE', result: 'PASS', observedAt: '2026-10-01T05:00:00Z', conclusion: 'Khoa đã nhận bàn giao', recordedByUserId: 2, departmentSignerId: 2, departmentConfirmedAt: '2026-10-01T05:00:00Z', vtytSignerId: 1, vtytConfirmedAt: '2026-10-01T05:00:00Z' } },
  ] },
  { itemId: 32, equipmentCode: 'EQ-02', equipmentName: 'Máy chuyển sửa chữa', departmentName: 'Khoa Nội', status: 'REPAIR_REQUIRED', providerName: 'Đơn vị thử', attempts: [] },
] }
function reportPage() { return render(<MemoryRouter initialEntries={['/plans/10/report']}><Routes><Route path="/plans/:planId/report" element={<ReportDetailPage />} /></Routes></MemoryRouter>) }
function historyPage() { return render(<MemoryRouter initialEntries={['/equipment/4/history']}><Routes><Route path="/equipment/:equipmentId/history" element={<EquipmentHistoryPage />} /></Routes></MemoryRouter>) }
afterEach(cleanup)
beforeEach(() => {
  vi.clearAllMocks()
  auth.user.role = 'PHONG_VTYT'
  vi.mocked(reportsApi.evidence).mockResolvedValue(evidence)
  vi.mocked(plansApi.detail).mockResolvedValue(plan)
  vi.mocked(plansApi.items).mockResolvedValue({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0, last: true })
})
describe('UC11 report presentation', () => {
  it('keeps completed and repair outcomes separate and offers draft editing', async () => {
    vi.mocked(reportsApi.get).mockResolvedValue(draft)
    reportPage()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Lưu bản nháp' })).toBeTruthy())
    const counts = document.querySelectorAll('.report-counts strong')
    expect([...counts].map(node => node.textContent)).toEqual(['1', '1'])
    expect(screen.getByText('Hoàn tất bảo trì')).toBeTruthy()
    expect(screen.getAllByText('Chuyển sửa chữa').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: 'Hoàn tất báo cáo' })).toBeTruthy()
  })
  it('renders FINAL as read only and does not offer a close command', async () => {
    vi.mocked(reportsApi.get).mockResolvedValue({ ...draft, status: 'FINAL', planStatus: 'REPORTED' })
    vi.mocked(plansApi.detail).mockResolvedValue({ ...plan, status: 'REPORTED' })
    reportPage()
    await waitFor(() => expect(screen.getByText('Báo cáo chính thức')).toBeTruthy())
    expect(screen.queryByRole('button', { name: 'Lưu bản nháp' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Hoàn tất báo cáo' })).toBeNull()
    expect(screen.queryByRole('button', { name: /đóng kế hoạch/i })).toBeNull()
  })
})
describe('UC11 evidence gap regression', () => {
  it('shows progress and acceptance data before finalization', async () => {
    vi.mocked(reportsApi.get).mockResolvedValue(draft); reportPage()
    expect(await screen.findByText('Dữ liệu thực hiện và nghiệm thu')).toBeTruthy()
    expect(screen.getByText('Đã thay bộ lọc')).toBeTruthy(); expect(screen.getByText('Kết luận kỹ thuật đạt')).toBeTruthy(); expect(screen.getByText('Khoa đã nhận bàn giao')).toBeTruthy()
    expect(reportsApi.evidence).toHaveBeenCalledWith(10)
  })
  it('BGD sees a final report and its evidence without editing controls', async () => {
    auth.user.role = 'BAN_GIAM_DOC'; vi.mocked(reportsApi.get).mockResolvedValue({ ...draft, status: 'FINAL', planStatus: 'REPORTED' }); vi.mocked(plansApi.detail).mockResolvedValue({ ...plan, status: 'REPORTED' }); reportPage()
    await screen.findByText('Báo cáo chính thức'); expect(screen.getByText('Kết luận kỹ thuật đạt')).toBeTruthy(); expect(screen.queryByRole('textbox')).toBeNull(); expect(screen.queryByRole('button', { name: 'Hoàn tất báo cáo' })).toBeNull()
  })
  it('identifies unfinished equipment and blocks report actions', async () => {
    vi.mocked(reportsApi.get).mockResolvedValue(draft); vi.mocked(reportsApi.evidence).mockResolvedValue({ planId: 10, items: [{ ...evidence.items[0], status: 'IN_MAINTENANCE' }] }); reportPage()
    expect(await screen.findByText(/Chưa thể chốt báo cáo: EQ-01/)).toBeTruthy(); expect(screen.queryByRole('button', { name: 'Hoàn tất báo cáo' })).toBeNull(); expect(reportsApi.finalize).not.toHaveBeenCalled()
  })
})
describe('UC12 multi-campaign presentation', () => {
  it('renders all campaigns in server order, repair distinctly, and no history mutation controls', async () => {
    vi.mocked(historyApi.get).mockResolvedValue({ equipmentId: 4, equipmentCode: 'DEMO-EQ-004', equipmentName: 'Máy sốc điện', currentDepartmentId: 3,
      campaigns: [
        { planId: 5, planTitle: 'Đợt mới', planStatus: 'IN_PROGRESS', periodStart: '2026-09-01', periodEnd: '2026-09-30',
          itemId: 25, departmentIdAtPlan: 3, itemStatus: 'REPAIR_REQUIRED', assignmentRoute: 'UNDER_CONTRACT', assignedProviderId: 1,
          coverageId: 4, attempts: [], itemHistory: [], planHistory: [], report: null },
        { planId: 2, planTitle: 'Đợt cũ', planStatus: 'CLOSED', periodStart: '2026-01-01', periodEnd: '2026-03-31',
          itemId: 4, departmentIdAtPlan: 3, itemStatus: 'COMPLETED', assignmentRoute: 'UNDER_CONTRACT', assignedProviderId: 1,
          coverageId: 4, attempts: [], itemHistory: [], planHistory: [], report: { id: 8, status: 'FINAL', reportDate: '2026-03-20', finalizedAt: '2026-03-20T00:00:00Z' } },
      ] })
    historyPage()
    await waitFor(() => expect(document.querySelectorAll('.campaign-card').length).toBe(2))
    const cards = document.querySelectorAll('.campaign-card')
    expect(cards[0].textContent).toContain('Đợt mới')
    expect(cards[0].textContent).toContain('Chuyển sửa chữa')
    expect(cards[1].textContent).toContain('Đợt cũ')
    expect(cards[1].textContent).toContain('Hoàn tất')
    expect(screen.getByText('Mở báo cáo')).toBeTruthy()
    fireEvent.click(cards[1].querySelector('summary')!)
    expect(screen.queryByRole('button', { name: /xóa|sửa lịch sử/i })).toBeNull()
    expect(historyApi.get).toHaveBeenCalledTimes(1)
  })
})

describe('BGD / KHOA audit gaps', () => {
  it('searches equipment through the API and resets pagination', async () => {
    auth.user.role = 'BAN_GIAM_DOC'
    vi.mocked(equipmentApi.list).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true })
    render(<MemoryRouter><EquipmentListPage /></MemoryRouter>)
    await screen.findByText('Không có thiết bị trong phạm vi hiển thị.')
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: ' DEMO-EQ-001 ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }))
    await waitFor(() => expect(equipmentApi.list).toHaveBeenLastCalledWith(0, 20, undefined, 'DEMO-EQ-001'))
  })
  it('KHOA queue requests only plans with scoped waiting handover items', async () => {
    auth.user.role = 'KHOA_PHONG'
    vi.mocked(plansApi.list).mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true })
    render(<MemoryRouter><ExecutionQueuePage /></MemoryRouter>)
    await screen.findByText('Không có thiết bị đang chờ bàn giao trong khoa/phòng của bạn.')
    expect(plansApi.list).toHaveBeenCalledWith(0, 10, undefined, 'AWAITING_HANDOVER')
  })
  it('KHOA plan queue filters waiting handover items', async () => {
    auth.user.role = 'KHOA_PHONG'
    render(<MemoryRouter initialEntries={['/execution/plans/10']}><Routes><Route path="/execution/plans/:planId" element={<ExecutionQueuePage />} /></Routes></MemoryRouter>)
    await screen.findByText('Không có hạng mục trong phạm vi truy cập.')
    expect(plansApi.items).toHaveBeenCalledWith(10, 0, 20, 'AWAITING_HANDOVER')
  })
  it('BGD plan review shows prepared methods, providers and proposal evidence', async () => {
    auth.user.role = 'BAN_GIAM_DOC'
    vi.mocked(approvalsApi.review).mockResolvedValue({ id: 20, requestType: 'PLAN_APPROVAL', status: 'PENDING', planId: 10, planVersion: 6 } as Awaited<ReturnType<typeof approvalsApi.review>>)
    vi.mocked(plansApi.items).mockResolvedValue({ content: [
      { id: 1, equipmentCode: 'EQ-FREE', classification: 'FREE', assignedProviderName: 'Hợp đồng thử', status: 'UNDER_CONTRACT' },
      { id: 2, equipmentCode: 'EQ-PAID', classification: 'NOT_FREE', proposedProviderName: 'Đề xuất thử', rationale: 'Năng lực phù hợp', warrantyImpactNote: 'Bảo hành được giữ', status: 'PENDING_PROPOSAL' },
    ] as Awaited<ReturnType<typeof plansApi.items>>['content'], page: 0, size: 20, totalElements: 2, totalPages: 1, last: true })
    render(<MemoryRouter initialEntries={['/approvals/20']}><Routes><Route path="/approvals/:requestId" element={<ApprovalDetailPage />} /></Routes></MemoryRouter>)
    await screen.findByText('Hợp đồng thử')
    expect(screen.getByText('Đề xuất thử')).toBeTruthy()
    expect(screen.getByText('Căn cứ: Năng lực phù hợp')).toBeTruthy()
    expect(screen.getByText('Ghi chú bảo hành: Bảo hành được giữ')).toBeTruthy()
    fireEvent.click(screen.getByLabelText('Yêu cầu chỉnh sửa'))
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' }))
    expect(screen.getByText('Vui lòng ghi lý do yêu cầu chỉnh sửa.')).toBeTruthy()
    expect(approvalsApi.decide).not.toHaveBeenCalled()
  })
})
