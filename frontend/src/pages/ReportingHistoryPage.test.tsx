import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { plansApi } from '../api/plansApi'
import { reportsApi } from '../api/reportsApi'
import { historyApi } from '../api/historyApi'
import type { ReportResponse } from '../types/report'
import type { Plan } from '../types/workflow'
import { ReportDetailPage } from './ReportDetailPage'
import { EquipmentHistoryPage } from './EquipmentHistoryPage'

vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { role: 'PHONG_VTYT' } }) }))
vi.mock('../api/plansApi', () => ({ plansApi: { detail: vi.fn(), items: vi.fn() } }))
vi.mock('../api/reportsApi', () => ({ reportsApi: { get: vi.fn(), create: vi.fn(), edit: vi.fn(), finalize: vi.fn() } }))
vi.mock('../api/historyApi', () => ({ historyApi: { get: vi.fn() } }))
const plan = { id: 10, title: 'Đợt bảo trì thử nghiệm', periodStart: '2026-10-01', periodEnd: '2026-10-31', status: 'AWAITING_REPORT', version: 6,
  createdAt: '2026-09-01T00:00:00Z', createdByUserId: 1, createdByName: 'VTYT' } as Plan
const draft = { id: 99, planId: 10, status: 'DRAFT', planStatus: 'AWAITING_REPORT', planVersion: 6, reportDate: '2026-10-01', finalizedAt: null,
  completedCount: 1, repairRequiredCount: 1, reportNumber: null, workDone: 'Đã bảo trì', achieved: null, notAchieved: null,
  causes: null, nextWork: null, resolutions: null, recommendations: null } as ReportResponse
function reportPage() { return render(<MemoryRouter initialEntries={['/plans/10/report']}><Routes><Route path="/plans/:planId/report" element={<ReportDetailPage />} /></Routes></MemoryRouter>) }
function historyPage() { return render(<MemoryRouter initialEntries={['/equipment/4/history']}><Routes><Route path="/equipment/:equipmentId/history" element={<EquipmentHistoryPage />} /></Routes></MemoryRouter>) }
afterEach(cleanup)
beforeEach(() => {
  vi.clearAllMocks()
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
    expect(screen.getByText('Chuyển sửa chữa')).toBeTruthy()
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
