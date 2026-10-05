import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { MaintenanceHistoryPage } from './MaintenanceHistoryPage'
import { apiRequest } from '../api/client'

const auth=vi.hoisted(()=>({user:{role:'PHONG_VTYT'}}))
vi.mock('../auth/useAuth',()=>({useAuth:()=>auth}))
vi.mock('../api/client',()=>({apiRequest:vi.fn()}))
const plan={planId:10,planTitle:'Bảo trì Quý I 2027',periodStart:'2027-01-01',periodEnd:'2027-03-31',equipmentCount:2,completedCount:1,damagedCount:1,completedAt:'2027-03-15T00:00:00Z',reportStatus:'DRAFT'}
const device={itemId:31,planId:10,planTitle:plan.planTitle,equipmentId:1,equipmentCode:'TB-01',equipmentName:'Máy thử',equipmentActive:true,departmentName:'Khoa Nội',provider:'An Phát',status:'COMPLETED',completedAt:plan.completedAt,resultNote:null,reportStatus:'DRAFT'}
const paged=(content:unknown[])=>({content,page:0,size:20,totalElements:content.length,totalPages:1,last:true})
function page(){render(<MemoryRouter initialEntries={['/maintenance-history']}><Routes><Route path="/maintenance-history" element={<MaintenanceHistoryPage/>}/><Route path="/maintenance-history/plans/:planId" element={<MaintenanceHistoryPage/>}/></Routes></MemoryRouter>)}
beforeEach(()=>{vi.resetAllMocks();auth.user.role='PHONG_VTYT';vi.mocked(apiRequest).mockImplementation(async path=>path.startsWith('/api/maintenance-history/plans?')?paged([plan]):paged([device,{...device,itemId:32,equipmentId:2,equipmentCode:'TB-02',status:'REPAIR_REQUIRED'}]))})
afterEach(cleanup)
it('shows one row per plan and reveals devices only after opening that plan',async()=>{
 page();fireEvent.click(await screen.findByRole('link',{name:plan.planTitle}))
 expect(await screen.findByText('TB-01 · Máy thử')).toBeTruthy()
 expect(screen.getByText('TB-02 · Máy thử')).toBeTruthy()
 await waitFor(()=>expect(apiRequest).toHaveBeenLastCalledWith('/api/maintenance-history?page=0&size=20&search=&planId=10'))
 fireEvent.click(screen.getByRole('link',{name:'← Lịch sử theo kế hoạch'}))
 await screen.findByRole('link',{name:plan.planTitle})
 expect(document.querySelectorAll('tbody tr')).toHaveLength(1)
 expect(screen.queryByText('TB-01 · Máy thử')).toBeNull()
 expect(screen.getByText('1 có hỏng hóc')).toBeTruthy()
})
it('keeps department history in its existing device view',async()=>{
 auth.user.role='KHOA_PHONG';page()
 expect(await screen.findByText('TB-01 · Máy thử')).toBeTruthy()
 expect(apiRequest).toHaveBeenCalledWith('/api/maintenance-history?page=0&size=20&search=')
})
