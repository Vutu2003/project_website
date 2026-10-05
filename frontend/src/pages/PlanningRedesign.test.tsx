import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { PlanFormPage } from './PlanFormPage'
import { ApprovalDetailPage } from './ApprovalDetailPage'
import { QuarterlyEquipmentListPage, EquipmentDetailPage } from './QuarterlyEquipmentPages'
import { AppLayout } from '../layouts/AppLayout'
import { quarterlyApi } from '../api/quarterlyApi'
import { plansApi } from '../api/plansApi'
import { providersApi } from '../api/providersApi'
import { approvalsApi } from '../api/approvalsApi'
import { apiRequest } from '../api/client'
const auth = vi.hoisted(() => ({ user: { id: 1, username: 'vtyt', role: 'PHONG_VTYT' }, logout: vi.fn() }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => auth }))
vi.mock('../components/NotificationBell', () => ({ NotificationBell: () => null }))
vi.mock('./EquipmentHistoryPage', () => ({ EquipmentHistoryPage: () => <p>Chưa có đợt bảo trì.</p> }))
vi.mock('../api/quarterlyApi', async importOriginal => ({ ...await importOriginal<typeof import('../api/quarterlyApi')>(), quarterlyApi: { preview: vi.fn(), create: vi.fn() } }))
vi.mock('../api/plansApi', () => ({ plansApi: { detail: vi.fn(), allItems: vi.fn(), edit: vi.fn(), submit: vi.fn() } }))
vi.mock('../api/providersApi', () => ({ providersApi: { list: vi.fn() } }))
vi.mock('../api/approvalsApi', () => ({ approvalsApi: { review: vi.fn(), decide: vi.fn() } }))
vi.mock('../api/client', () => ({ apiRequest: vi.fn() }))
const equipment = { equipment_id: 1, equipment_code: 'TB-001', equipment_name: 'Máy siêu âm', department_name: 'Khoa Nội', quarters: 'Q1, Q3', classification: 'FREE', contract_status: 'VALID', provider_id: 7, provider_name: 'An Phát', contract_id: 15, contract_code: 'HD-01' }
const preview = { year: 2027, quarter: 'Q1' as const, title: 'Kế hoạch bảo trì Quý I năm 2027', periodStart: '2027-01-01', periodEnd: '2027-03-31', referenceDate: '2027-01-01', equipment: [equipment] }
const plan = { id: 10, title: preview.title, periodStart: preview.periodStart, periodEnd: preview.periodEnd, planYear: 2027, planQuarter: 'Q1' as const, status: 'DRAFT' as const, version: 0, createdAt: '', createdByUserId: 1, createdByName: 'VTYT' }
const item = { id: 31, planId: 10, equipmentId: 1, equipmentCode: 'TB-001', equipmentName: 'Máy siêu âm', departmentIdAtPlan: 1, departmentNameAtPlan: 'Khoa Nội', plannedDate: '2027-01-01', status: 'UNDER_CONTRACT' as const, version: 0, assignedProviderId: 7, assignedProviderName: 'An Phát', assignmentRoute: 'UNDER_CONTRACT' as const, classification: 'FREE' as const, contractId: 15, contractCode: 'HD-01' }
function page(path = '/plans/new') { return render(<MemoryRouter initialEntries={[path]}><Routes><Route path="/plans/new" element={<PlanFormPage mode="create" />} /><Route path="/plans/:planId/edit" element={<PlanFormPage mode="edit" />} /><Route path="/plans/:planId" element={<p>Chi tiết kế hoạch đã lưu</p>} /><Route path="/equipment" element={<QuarterlyEquipmentListPage />} /><Route path="/equipment/:equipmentId" element={<EquipmentDetailPage />} /><Route path="/approvals/:requestId" element={<ApprovalDetailPage />} /><Route path="/approvals" element={<p>Hàng chờ</p>} /></Routes></MemoryRouter>) }
afterEach(cleanup)
beforeEach(() => {
 vi.resetAllMocks(); auth.user.role = 'PHONG_VTYT'
 vi.mocked(quarterlyApi.preview).mockResolvedValue(preview)
 vi.mocked(quarterlyApi.create).mockResolvedValue({ id: 10, status: 'DRAFT', version: 0, approvalRequestId: null })
 vi.mocked(providersApi.list).mockResolvedValue([{ id: 7, code: 'AP', name: 'An Phát', active: true }])
 vi.mocked(plansApi.detail).mockResolvedValue(plan); vi.mocked(plansApi.allItems).mockResolvedValue([item])
 vi.mocked(plansApi.edit).mockResolvedValue({ id: 10, status: 'DRAFT', version: 1, approvalRequestId: null })
 vi.mocked(plansApi.submit).mockResolvedValue({ id: 10, status: 'SUBMITTED', version: 1, approvalRequestId: 20 })
 vi.mocked(apiRequest).mockImplementation(async path => path === '/api/equipment-catalog' ? [equipment] : path.includes('quarterly-detail') ? { ...equipment, contracts: [{ contract_id: 15, contract_code: 'HD-01', provider_id: 7, provider_name: 'An Phát', valid: true, start_date: '2026-01-01', end_date: '2030-12-31' }] } : [])
})
describe('Fixed quarterly planning', () => {
 it('renders the exact VTYT navigation order', () => { render(<MemoryRouter><AppLayout /></MemoryRouter>); expect(within(screen.getByRole('navigation')).getAllByRole('link').map(a => a.textContent)).toEqual(['Tổng quan','Danh sách hợp đồng','Danh sách thiết bị','Kế hoạch bảo trì','Theo dõi tiến độ bảo trì','Báo cáo bảo trì']) })
 it('limits BGD navigation to approval and reports', () => { auth.user.role='BAN_GIAM_DOC';render(<MemoryRouter><AppLayout /></MemoryRouter>);expect(within(screen.getByRole('navigation')).getAllByRole('link').map(a=>a.textContent)).toEqual(['Tổng quan','Phê duyệt','Báo cáo']) })
 it('has only year and quarter selectors without manual title, devices or dates', async () => { page();await screen.findByRole('link',{name:'TB-001'});expect(screen.getByLabelText('Năm')).toBeTruthy();expect(screen.getByLabelText('Quý')).toBeTruthy();expect(screen.queryByRole('textbox',{name:/Tiêu đề/})).toBeNull();expect(screen.queryByRole('checkbox')).toBeNull();expect(document.querySelector('input[type=date]')).toBeNull() })
 it('previews every automatically scheduled device and contract/provider links', async () => { page();await screen.findByRole('link',{name:'HD-01'});expect(screen.getByText('Còn hạn')).toBeTruthy();expect(screen.getByText('Theo hợp đồng')).toBeTruthy();expect(screen.getByRole('link',{name:'An Phát'}).getAttribute('href')).toBe('/providers/7');expect(screen.queryByLabelText('Đơn vị đề xuất TB-001')).toBeNull();expect(quarterlyApi.create).not.toHaveBeenCalled() })
 it('reloads quarter preview without creating records', async () => { page();await screen.findByRole('link',{name:'TB-001'});fireEvent.change(screen.getByLabelText('Quý'),{target:{value:'Q2'}});await waitFor(()=>expect(quarterlyApi.preview).toHaveBeenLastCalledWith(2027,'Q2'));expect(quarterlyApi.create).not.toHaveBeenCalled() })
 it('creates and submits from year/quarter', async () => {page();fireEvent.click(await screen.findByRole('button',{name:'Gửi phê duyệt'}));await screen.findByText('Chi tiết kế hoạch đã lưu');expect(quarterlyApi.create).toHaveBeenCalledWith(2027,'Q1',[]);expect(plansApi.submit).toHaveBeenCalledWith(10,0)})
 it('highlights incomplete outside-contract proposals before submission', async () => {vi.mocked(quarterlyApi.preview).mockResolvedValue({...preview,equipment:[{...equipment,classification:'NOT_FREE',contract_status:'EXPIRED',provider_id:undefined,contract_id:undefined}]});page();await screen.findByLabelText('Đơn vị đề xuất TB-001');fireEvent.click(screen.getByRole('button',{name:'Gửi phê duyệt'}));expect(screen.getByRole('alert').textContent).toContain('Bổ sung');expect(quarterlyApi.create).not.toHaveBeenCalled();fireEvent.change(screen.getByLabelText('Đơn vị đề xuất TB-001'),{target:{value:'7'}});fireEvent.change(screen.getByLabelText('Căn cứ TB-001'),{target:{value:'Đủ năng lực bảo trì'}});fireEvent.click(screen.getByRole('button',{name:'Gửi phê duyệt'}));await screen.findByText('Chi tiết kế hoạch đã lưu');expect(quarterlyApi.create).toHaveBeenCalledWith(2027,'Q1',[{equipmentId:1,proposedProviderId:7,rationale:'Đủ năng lực bảo trì'}])})
 it('keeps revision comments visible on the quarterly edit form', async () => {vi.mocked(apiRequest).mockResolvedValue([{id:2,request_id:22,comment:'Bổ sung căn cứ chọn đơn vị.',reviewer:'BGĐ',action_at:'2026-10-05T00:00:00Z'}]);page('/plans/10/edit');expect(await screen.findByText('Bổ sung căn cứ chọn đơn vị.')).toBeTruthy();expect((screen.getByLabelText('Quý') as HTMLSelectElement).disabled).toBe(true)})
 it('shows a four-column equipment list linked to equipment detail', async () => {page('/equipment');await screen.findByRole('link',{name:'TB-001'});expect(screen.getAllByRole('columnheader').map(h=>h.textContent)).toEqual(['Mã thiết bị','Tên thiết bị','Khoa/Phòng','Thời hạn bảo hành','Trạng thái bảo hành','Lịch bảo trì']);expect(screen.getByRole('link',{name:'TB-001'}).getAttribute('href')).toBe('/equipment/1')})
 it('shows equipment quarters, company/contracts and history', async () => {page('/equipment/1');await screen.findByText('Q3');expect(screen.getByRole('heading',{name:'Lịch sử bảo trì'})).toBeTruthy();expect(screen.getByRole('link',{name:'HD-01'}).getAttribute('href')).toBe('/contracts/15');expect(screen.getByText('Còn hạn')).toBeTruthy()})
 it('shows all approval equipment in one table without accordions or paging', async () => {
  vi.mocked(approvalsApi.review).mockResolvedValue({id:20,requestType:'PLAN_APPROVAL',status:'PENDING',planId:10,planTitle:plan.title,planVersion:0,createdByName:'VTYT'} as Awaited<ReturnType<typeof approvalsApi.review>>)
  vi.mocked(plansApi.allItems).mockResolvedValue(Array.from({length:25},(_,i)=>({...item,id:i+1,equipmentCode:`TB-${i+1}`})))
  page('/approvals/20');await screen.findByText('TB-25');expect(screen.getAllByRole('row')).toHaveLength(26);expect(document.querySelector('details')).toBeNull();expect(screen.queryByRole('button',{name:/Trang tiếp/})).toBeNull();expect(screen.getAllByText('Theo hợp đồng')).toHaveLength(25)
 })
})

it('preselects current quarter from a dashboard shortcut',async()=>{page('/plans/new?year=2026&quarter=Q4');await screen.findByRole('link',{name:'TB-001'});expect(quarterlyApi.preview).toHaveBeenCalledWith(2026,'Q4');expect((screen.getByLabelText('Năm') as HTMLSelectElement).value).toBe('2026');expect((screen.getByLabelText('Quý') as HTMLSelectElement).value).toBe('Q4')})
