import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { ExecutionItemPage } from './ExecutionItemPage'
import { acceptancesApi } from '../api/acceptancesApi'
import { executionsApi } from '../api/executionsApi'
import { plansApi } from '../api/plansApi'
import { progressLabels } from '../utils/executionProgress'
import type { Plan, PlanItem } from '../types/workflow'
import type { EquipmentExecutionHistory, ExecutionAttempt, MaintenanceProgressStatus } from '../types/execution'
const auth = vi.hoisted(() => ({ user: { role: 'PHONG_VTYT' } }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => auth }))
vi.mock('../api/acceptancesApi', () => ({ acceptancesApi: { technical: vi.fn() } }))
vi.mock('../api/plansApi', () => ({ plansApi: { detail: vi.fn(), items: vi.fn() } }))
vi.mock('../api/executionsApi', () => ({ executionsApi: { history: vi.fn(), start: vi.fn(), updateProgress: vi.fn(), finish: vi.fn() } }))
const plan: Plan = { id: 10, title: 'Kế hoạch thử', periodStart: '2026-11-01', periodEnd: '2026-11-30', status: 'IN_PROGRESS', version: 3, createdAt: '2026-10-02T00:00:00Z', createdByUserId: 1, createdByName: 'VTYT' }
const item: PlanItem = { id: 31, planId: 10, equipmentId: 1, equipmentCode: 'EQ-01', equipmentName: 'Máy thử', departmentIdAtPlan: 1, departmentNameAtPlan: 'Khoa Nội', plannedDate: '2026-11-15', status: 'IN_MAINTENANCE', version: 1, assignedProviderId: 7, assignedProviderName: 'Đơn vị hợp đồng', assignmentRoute: 'UNDER_CONTRACT' }
const attempt: ExecutionAttempt = { executionId: 48, attemptNo: 1, actualProviderId: 7, actualProviderName: 'Đơn vị hợp đồng', startedAt: '2026-11-15T02:00:00Z', endedAt: null, resultNote: null, progress: [], technicalAcceptance: null, handoverAcceptance: null }
function rows(selected: PlanItem) { return { content: [selected], page: 0, size: 100, totalElements: 1, totalPages: 1, last: true } }
function history(selected = attempt): EquipmentExecutionHistory { return { equipmentId: 1, equipmentCode: 'EQ-01', equipmentName: 'Máy thử', currentDepartmentId: 1, campaigns: [{ planId: 10, planTitle: plan.title, planStatus: 'IN_PROGRESS', periodStart: plan.periodStart, periodEnd: plan.periodEnd, itemId: 31, departmentIdAtPlan: 1, itemStatus: item.status, assignmentRoute: 'UNDER_CONTRACT', assignedProviderId: 7, coverageId: 15, attempts: [selected], itemHistory: [{ id: 1, oldState: 'UNDER_CONTRACT', newState: 'IN_MAINTENANCE', action: 'START_MAINTENANCE', reason: null, at: attempt.startedAt, actorUserId: 1 }], planHistory: [], report: null }] } }
function page() { render(<MemoryRouter initialEntries={['/plans/10/items/31/execution']}><Routes><Route path="/plans/:planId/items/:itemId/execution" element={<ExecutionItemPage />} /></Routes></MemoryRouter>) }
afterEach(() => { cleanup(); vi.restoreAllMocks() })
beforeEach(() => {
 vi.resetAllMocks(); auth.user.role = 'PHONG_VTYT'; vi.spyOn(window, 'confirm').mockReturnValue(true)
 vi.mocked(plansApi.detail).mockResolvedValue(plan); vi.mocked(plansApi.items).mockResolvedValue(rows(item)); vi.mocked(executionsApi.history).mockResolvedValue(history())
 vi.mocked(executionsApi.updateProgress).mockResolvedValue({ progressId: 9, executionId: 48, eventAt: '2026-11-15T02:30:00Z' })
})
describe('Simple UC08', () => {
 it('starts an eligible VTYT item using current versions', async () => {
  vi.mocked(plansApi.detail).mockResolvedValue({ ...plan, status: 'APPROVED' }); vi.mocked(plansApi.items).mockResolvedValue(rows({ ...item, status: 'UNDER_CONTRACT' })); vi.mocked(executionsApi.history).mockResolvedValue({ ...history(), campaigns: [] })
  page(); fireEvent.click(await screen.findByRole('button', { name: 'Bắt đầu bảo trì' })); await waitFor(() => expect(executionsApi.start).toHaveBeenCalledWith(31, 1, 3))
 })
 it.each(Object.keys(progressLabels) as MaintenanceProgressStatus[])('appends predefined %s with an optional note', async status => {
  page(); fireEvent.change(await screen.findByLabelText('Tiến độ hiện tại'), { target: { value: status } }); fireEvent.change(screen.getByLabelText('Ghi chú'), { target: { value: 'Thông tin từ đơn vị' } }); fireEvent.click(screen.getByRole('button', { name: 'Lưu tiến độ' })); await waitFor(() => expect(executionsApi.updateProgress).toHaveBeenCalledWith(48, status, 'Thông tin từ đơn vị', 1))
 })
 it('does not require a note', async () => { page(); fireEvent.click(await screen.findByRole('button', { name: 'Lưu tiến độ' })); await waitFor(() => expect(executionsApi.updateProgress).toHaveBeenCalledWith(48, 'IN_PROGRESS', null, 1)) })
 it('shows chronological status, note, actor and legacy progress', async () => {
  vi.mocked(executionsApi.history).mockResolvedValue(history({ ...attempt, progress: [{ id: 2, eventAt: '2026-11-15T03:00:00Z', workNote: 'Chờ linh kiện\nChờ bộ lọc', damageNote: null, recordedByUserId: 2 }, { id: 1, eventAt: '2026-11-15T02:30:00Z', workNote: 'Ghi chú cũ', damageNote: null, recordedByUserId: 1 }] }))
  page(); const timeline = await screen.findByRole('list', { name: 'Timeline tiến độ' }); const entries = within(timeline).getAllByRole('listitem'); expect(entries).toHaveLength(3); expect(entries[0].textContent).toContain('Bắt đầu bảo trì'); expect(entries[1].textContent).toContain('Ghi chú cũ'); expect(entries[2].textContent).toContain('Chờ linh kiện'); expect(entries[2].textContent).toContain('Chờ bộ lọc'); expect(entries[2].textContent).toContain('#2'); expect(screen.getByText('Tạm dừng', { selector: 'strong' })).toBeTruthy()
 })
 it('confirms completion, reloads and removes progress controls', async () => {
  vi.mocked(executionsApi.finish).mockImplementation(async () => {
   vi.mocked(plansApi.items).mockResolvedValue(rows({ ...item, status: 'AWAITING_TECHNICAL_ACCEPTANCE', version: 2 })); vi.mocked(executionsApi.history).mockResolvedValue(history({ ...attempt, endedAt: '2026-11-15T04:00:00Z' })); return { executionId: 48, itemId: 31, attemptNo: 1, providerId: 7, itemStatus: 'AWAITING_TECHNICAL_ACCEPTANCE', itemVersion: 2, planStatus: 'IN_PROGRESS', planVersion: 3 }
  })
  page(); fireEvent.click(await screen.findByRole('button', { name: 'Hoàn thành kỹ thuật' })); await screen.findByText('Chờ nghiệm thu kỹ thuật', { selector: 'p.retention-note' }); expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('hoàn thành kỹ thuật')); expect(executionsApi.finish).toHaveBeenCalledWith(48, 1, null); expect(screen.queryByLabelText('Tiến độ hiện tại')).toBeNull(); expect(screen.queryByRole('button', { name: 'Lưu tiến độ' })).toBeNull()
 })
 it('canceling completion keeps the active work', async () => { vi.mocked(window.confirm).mockReturnValue(false); page(); fireEvent.click(await screen.findByRole('button', { name: 'Hoàn thành kỹ thuật' })); expect(executionsApi.finish).not.toHaveBeenCalled(); expect(screen.getByLabelText('Tiến độ hiện tại')).toBeTruthy() })
 it('a read-only role has no UC08 command', async () => { auth.user.role = 'BAN_GIAM_DOC'; page(); await screen.findByText('Theo dõi bảo trì'); expect(screen.queryByRole('button', { name: 'Lưu tiến độ' })).toBeNull(); expect(screen.queryByRole('button', { name: 'Hoàn thành kỹ thuật' })).toBeNull() })
})

describe('Existing UC09 controls', () => {
 function ready() {
  vi.mocked(plansApi.items).mockResolvedValue(rows({ ...item, status: 'AWAITING_TECHNICAL_ACCEPTANCE' }))
  vi.mocked(executionsApi.history).mockResolvedValue(history({ ...attempt, endedAt: '2026-11-15T04:00:00Z' }))
 }
 it.each([{ result: 'PASS' as const, repair: false }, { result: 'FAIL' as const, repair: false }, { result: 'FAIL' as const, repair: true }])('sends $result with repair=$repair and mandatory conclusion', async ({ result, repair }) => {
  ready(); page(); await screen.findByRole('button', { name: 'Ghi nghiệm thu kỹ thuật' })
  fireEvent.click(screen.getByRole('radio', { name: result === 'PASS' ? 'Đạt' : 'Không đạt' }))
  if (repair) fireEvent.click(screen.getByLabelText('Chuyển sửa chữa thay vì thực hiện lại'))
  fireEvent.change(screen.getByLabelText('Kết luận bắt buộc'), { target: { value: 'Kết luận của VTYT' } }); fireEvent.click(screen.getByRole('button', { name: 'Ghi nghiệm thu kỹ thuật' }))
  await waitFor(() => expect(acceptancesApi.technical).toHaveBeenCalledWith(48, { version: 1, result, conclusion: 'Kết luận của VTYT', repairRequired: repair }))
 })
 it('does not save without a conclusion', async () => {
  ready(); page(); fireEvent.click(await screen.findByRole('button', { name: 'Ghi nghiệm thu kỹ thuật' })); expect(await screen.findByText('Vui lòng nhập kết luận nghiệm thu kỹ thuật.')).toBeTruthy(); expect(acceptancesApi.technical).not.toHaveBeenCalled()
 })
})
