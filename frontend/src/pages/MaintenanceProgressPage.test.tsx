import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { MaintenanceProgressPage } from './MaintenanceProgressPage'
import { ExecutionQueuePage } from './ExecutionQueuePage'
import { RoleGuard } from '../auth/RoleGuard'
import { maintenanceTrackingApi } from '../api/maintenanceTrackingApi'
import { executionsApi } from '../api/executionsApi'
import { plansApi } from '../api/plansApi'
import { navigationItems } from '../routes/navigation'
import type { Plan, PlanStatus } from '../types/workflow'
const auth = vi.hoisted(() => ({ user: { role: 'PHONG_VTYT' } }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => auth }))
vi.mock('../api/maintenanceTrackingApi',()=>({maintenanceTrackingApi:{list:vi.fn(),plans:vi.fn(),startAll:vi.fn(),markWorkDone:vi.fn(),complete:vi.fn()}}))
vi.mock('../api/executionsApi',()=>({executionsApi:{start:vi.fn(),updateProgress:vi.fn()}}))
vi.mock('../api/plansApi', () => ({ plansApi: { list: vi.fn(), detail: vi.fn(), items: vi.fn() } }))
const plan: Plan = { id: 10, title: 'Kế hoạch BGĐ đã duyệt', periodStart: '2026-11-01', periodEnd: '2026-11-30', status: 'APPROVED', version: 2, createdAt: '2026-10-02T00:00:00Z', createdByUserId: 1, createdByName: 'VTYT' }
function paged<T>(content: T[]) { return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1, last: true } }
function page(path = '/maintenance-progress') { return render(<MemoryRouter initialEntries={[path]}><Routes>
 <Route path="/maintenance-progress" element={<RoleGuard roles={['PHONG_VTYT']}><MaintenanceProgressPage /></RoleGuard>} />
 <Route path="/maintenance-progress/plans/:planId" element={<MaintenanceProgressPage key="detail" />} />
 <Route path="/execution" element={<ExecutionQueuePage />} />
 <Route path="/plans/:planId/report" element={<p>Báo cáo vừa tạo</p>} />
 <Route path="/dashboard" element={<p>Tổng quan theo vai trò</p>} />
</Routes></MemoryRouter>) }
afterEach(cleanup)
beforeEach(() => {
 vi.resetAllMocks(); HTMLDialogElement.prototype.showModal=vi.fn(function(this:HTMLDialogElement){this.setAttribute('open','')}); auth.user.role = 'PHONG_VTYT'
 vi.mocked(maintenanceTrackingApi.plans).mockResolvedValue(paged([plan])); vi.mocked(plansApi.list).mockResolvedValue(paged([plan])); vi.mocked(plansApi.detail).mockResolvedValue(plan)
 vi.mocked(maintenanceTrackingApi.list).mockResolvedValue([{item_id:31,equipment_id:1,equipment_code:'EQ-01',equipment_name:'Máy thử',department_name:'Khoa Nội',provider_name:'Đơn vị hợp đồng',status:'UNDER_CONTRACT',version:0,execution_id:null,started_at:null,ended_at:null,updated_at:null,work_note:null,damage_note:null,progress_status:'NOT_STARTED'}])
 vi.mocked(plansApi.items).mockResolvedValue(paged([{ id: 31, planId: 10, equipmentId: 1, equipmentCode: 'EQ-01', equipmentName: 'Máy thử', departmentIdAtPlan: 1, departmentNameAtPlan: 'Khoa Nội', plannedDate: '2026-11-15', status: 'UNDER_CONTRACT', assignedProviderId: 7, assignedProviderName: 'Đơn vị hợp đồng', assignmentRoute: 'UNDER_CONTRACT', version: 0 }]))
})
describe('Separate maintenance progress section', () => {
 it('has a dedicated VTYT navigation item and loads all active tracking states by default', async () => {
  expect(navigationItems.find(row => row.path === '/maintenance-progress')).toMatchObject({ label: 'Theo dõi tiến độ bảo trì', roles: ['PHONG_VTYT'] })
  page(); expect(await screen.findByText(plan.title)).toBeTruthy(); expect(maintenanceTrackingApi.plans).toHaveBeenCalledWith(0, undefined)
  expect(screen.queryByRole('option', { name: 'Nháp' })).toBeNull(); expect(screen.queryByRole('option', { name: 'Đã gửi duyệt' })).toBeNull()
 })
 it('keeps plans visible after maintenance starts via the approved-stage filter', async () => {
  page(); await screen.findByText(plan.title); vi.mocked(maintenanceTrackingApi.plans).mockResolvedValue(paged([{ ...plan, status: 'IN_PROGRESS' }]))
  fireEvent.change(screen.getByLabelText('Trạng thái kế hoạch'), { target: { value: 'IN_PROGRESS' } }); await waitFor(() => expect(maintenanceTrackingApi.plans).toHaveBeenLastCalledWith(0, 'IN_PROGRESS')); expect(await screen.findByText(plan.title)).toBeTruthy()
 })
 it('opens approved plan equipment with one plan start action', async () => {
  page(); fireEvent.click(await screen.findByRole('link', { name: 'Cập nhật tiến độ' })); expect(await screen.findByText('EQ-01 · Máy thử')).toBeTruthy(); expect(screen.getByText('Chưa bắt đầu')).toBeTruthy(); expect(screen.getByRole('button', { name: 'Bắt đầu kế hoạch' })).toBeTruthy(); expect(screen.queryByRole('button',{name:'Lưu hỏng hóc'})).toBeNull()
 })
 it.each<PlanStatus>(['DRAFT', 'SUBMITTED', 'REVISION_REQUIRED'])('blocks %s through a direct tracking URL', async status => {
  vi.mocked(plansApi.detail).mockResolvedValue({ ...plan, status }); page('/maintenance-progress/plans/10'); expect(await screen.findByText(/Kế hoạch chưa được phê duyệt/)).toBeTruthy(); expect(plansApi.items).not.toHaveBeenCalled(); expect(screen.queryByRole('link', { name: 'Xem / cập nhật tiến độ' })).toBeNull()
 })
 it('redirects the old VTYT execution entry to the new filtered section', async () => { page('/execution'); await screen.findByText(plan.title); expect(maintenanceTrackingApi.plans).toHaveBeenCalledWith(0, undefined) })
 it.each(['BAN_GIAM_DOC', 'KHOA_PHONG', 'ADMIN'])('sends %s to its dashboard for a stale tracking URL', async role => {
  auth.user.role = role; page(); expect(await screen.findByText('Tổng quan theo vai trò')).toBeTruthy(); expect(plansApi.list).not.toHaveBeenCalled()
 })
})

it('updates one of exactly three progress levels directly from the tracking table',async()=>{vi.mocked(plansApi.detail).mockResolvedValue({...plan,status:'IN_PROGRESS'});vi.mocked(maintenanceTrackingApi.list).mockResolvedValue([{item_id:31,equipment_id:1,equipment_code:'EQ-01',equipment_name:'Máy thử',department_name:'Khoa Nội',provider_name:'Đơn vị hợp đồng',status:'IN_MAINTENANCE',version:2,execution_id:48,started_at:null,ended_at:null,updated_at:null,work_note:null,damage_note:null,progress_status:'IN_PROGRESS'}]);page('/maintenance-progress/plans/10');const select=await screen.findByLabelText('Tiến độ EQ-01');expect([...select.querySelectorAll('option')].map(o=>o.textContent)).toEqual(['Đang bảo trì','Bảo trì xong','Có hỏng hóc']);fireEvent.change(select,{target:{value:'DAMAGE_DETECTED'}});fireEvent.change(screen.getByLabelText('Mô tả hỏng hóc'),{target:{value:'Có rò rỉ'}});fireEvent.click(screen.getByRole('button',{name:'Lưu hỏng hóc'}));await waitFor(()=>expect(executionsApi.updateProgress).toHaveBeenCalledWith(48,'DAMAGE_DETECTED','Có rò rỉ',2))})
it('enables VTYT completion and creates a report only after every device has a result',async()=>{vi.spyOn(window,'confirm').mockReturnValue(true);vi.mocked(plansApi.detail).mockResolvedValue({...plan,status:'IN_PROGRESS'});vi.mocked(maintenanceTrackingApi.list).mockResolvedValue([{item_id:31,equipment_id:1,equipment_code:'EQ-01',equipment_name:'Máy thử',department_name:'Khoa Nội',provider_name:'Đơn vị hợp đồng',status:'IN_MAINTENANCE',version:2,execution_id:48,started_at:null,ended_at:null,updated_at:null,work_note:'Bảo trì xong',damage_note:null,progress_status:'WORK_DONE'}]);vi.mocked(maintenanceTrackingApi.complete).mockResolvedValue({planId:10,reportId:99,planVersion:3,status:'AWAITING_REPORT'});page('/maintenance-progress/plans/10');fireEvent.click(await screen.findByRole('button',{name:'Hoàn thành bảo trì & tạo báo cáo'}));await waitFor(()=>expect(maintenanceTrackingApi.complete).toHaveBeenCalledWith(10,2))})

it('starts the whole plan from one action',async()=>{page('/maintenance-progress/plans/10');fireEvent.click(await screen.findByRole('button',{name:'Bắt đầu kế hoạch'}));await waitFor(()=>expect(maintenanceTrackingApi.startAll).toHaveBeenCalledWith(10,2))})

it('refreshes the plan after VTYT returns from a director approval in another window', async () => {
 vi.mocked(plansApi.detail).mockResolvedValueOnce({...plan,status:'SUBMITTED'}).mockResolvedValue({...plan,pendingVendorApproval:false})
 page('/maintenance-progress/plans/10')
 await screen.findByText(/Kế hoạch chưa được phê duyệt/)
 fireEvent(window,new Event('focus'))
 const button=await screen.findByRole('button',{name:'Bắt đầu kế hoạch'})
 expect((button as HTMLButtonElement).disabled).toBe(false)
 fireEvent.click(button)
 await waitFor(()=>expect(maintenanceTrackingApi.startAll).toHaveBeenCalledWith(10,2))
})
