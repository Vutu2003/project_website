import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ReportDeliveryPanel } from './ReportDeliveryPanel'
import { reportsApi } from '../api/reportsApi'
vi.mock('../api/reportsApi',()=>({reportsApi:{delivery:vi.fn(),send:vi.fn()}}))
const draft={reportId:99,canSend:false,departments:[{id:1,name:'Khoa Nội'}],deliveries:[]}
afterEach(cleanup)
beforeEach(()=>{vi.resetAllMocks();vi.mocked(reportsApi.delivery).mockResolvedValue(draft);vi.spyOn(window,'confirm').mockReturnValue(true)})
it('does not allow sending an unfinished report',async()=>{render(<ReportDeliveryPanel planId={10} version={3} final={false}/>);await screen.findByText('Khoa Nội');expect((screen.getByRole('button',{name:'Gửi báo cáo cho BGĐ và khoa/phòng'}) as HTMLButtonElement).disabled).toBe(true);expect(reportsApi.send).not.toHaveBeenCalled()})
it('sends the final report and shows durable sender/time receipts',async()=>{vi.mocked(reportsApi.send).mockResolvedValue({...draft,canSend:true,deliveries:[{id:1,department_id:null,recipient:'Ban Giám đốc',sent_at:'2026-10-05T04:00:00Z',sent_by:'Phòng VTYT'}]});render(<ReportDeliveryPanel planId={10} version={3} final/>);fireEvent.click(await screen.findByRole('button',{name:'Gửi báo cáo cho BGĐ và khoa/phòng'}));await waitFor(()=>expect(reportsApi.send).toHaveBeenCalledWith(10,3));expect(await screen.findByText('✓ Báo cáo đã được gửi')).toBeTruthy();expect(screen.getByText('Phòng VTYT')).toBeTruthy();expect(screen.queryByRole('button',{name:'Gửi báo cáo cho BGĐ và khoa/phòng'})).toBeNull()})
