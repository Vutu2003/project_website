import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { apiRequest } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { PageResponse } from '../types/workflow'
import { businessDate, dateTime } from '../utils/workflowLabels'
interface HistoryRow {itemId:number;planId:number;planTitle:string;equipmentId:number;equipmentCode:string;equipmentName:string;equipmentActive:boolean;departmentName:string;provider:string|null;status:string;completedAt:string|null;resultNote:string|null;reportStatus:string|null}
interface PlanHistoryRow {planId:number;planTitle:string;periodStart:string;periodEnd:string;equipmentCount:number;completedCount:number;damagedCount:number;completedAt:string|null;reportStatus:string|null}
export function MaintenanceHistoryPage(){
 const {user}=useAuth();const {planId}=useParams()
 return user?.role==='PHONG_VTYT'&&!planId?<PlanHistoryPage/>:<HistoryItemsPage key={planId||'all'} planId={planId}/>
}
function PlanHistoryPage(){
 const [data,setData]=useState<PageResponse<PlanHistoryRow>|null>(null)
 const [page,setPage]=useState(0);const [input,setInput]=useState('');const [search,setSearch]=useState('')
 const [loading,setLoading]=useState(true);const [error,setError]=useState<unknown>(null);const [refresh,setRefresh]=useState(0)
 useEffect(()=>{let active=true;apiRequest<PageResponse<PlanHistoryRow>>(`/api/maintenance-history/plans?${new URLSearchParams({page:String(page),size:'20',search})}`).then(rows=>{if(active){setData(rows);setError(null);setLoading(false)}}).catch(e=>{if(active){setError(e);setLoading(false)}});return()=>{active=false}},[page,search,refresh])
 return <div className="page-stack catalog-page">
  <div className="catalog-hero"><div><p className="eyebrow">HỒ SƠ BẢO TRÌ</p><h1>Lịch sử bảo trì</h1><p>Lịch sử theo kế hoạch. Chọn kế hoạch để xem thiết bị và kết quả bảo trì.</p></div><button className="button secondary" onClick={()=>{setLoading(true);setRefresh(v=>v+1)}}>Tải lại</button></div>
  <WorkflowError error={error}/>
  <section className="panel business-panel">
   <form className="history-search" onSubmit={e=>{e.preventDefault();setPage(0);setSearch(input.trim());setLoading(true);setRefresh(v=>v+1)}}><label>Tìm kế hoạch hoặc thiết bị<input type="search" placeholder="Tên kế hoạch, mã hoặc tên thiết bị…" value={input} onChange={e=>setInput(e.target.value)}/></label><button className="button secondary">Tìm kiếm</button></form>
   <div className="panel-heading"><h2>Kế hoạch đã hoàn thành bảo trì</h2><span className="muted">{data?.totalElements??0} kế hoạch</span></div>
   {loading?<p>Đang tải lịch sử…</p>:data?.content.length?<>
    <div className="table-scroll"><table className="data-table catalog-table"><thead><tr><th>Kế hoạch / kỳ bảo trì</th><th>Thiết bị</th><th>Kết quả</th><th>Hoàn thành</th><th>Báo cáo</th></tr></thead><tbody>{data.content.map(plan=><tr key={plan.planId}>
     <td><Link className="table-link" to={`/maintenance-history/plans/${plan.planId}`}>{plan.planTitle}</Link><span className="row-sub">{businessDate(plan.periodStart)} – {businessDate(plan.periodEnd)}</span></td>
     <td><strong>{plan.equipmentCount}</strong><span className="row-sub"><Link className="text-link" to={`/maintenance-history/plans/${plan.planId}`}>Xem thiết bị →</Link></span></td>
     <td><StatusBadge label={`${plan.completedCount} bảo trì xong`} tone="teal"/>{plan.damagedCount>0&&<span className="row-sub"><StatusBadge label={`${plan.damagedCount} có hỏng hóc`} tone="amber"/></span>}</td>
     <td>{dateTime(plan.completedAt)}</td>
     <td>{plan.reportStatus?<Link className="table-link" to={`/plans/${plan.planId}/report`}>{plan.reportStatus==='FINAL'?'Xem báo cáo':'Báo cáo nháp'}</Link>:'—'}</td>
    </tr>)}</tbody></table></div>
    <Pagination data={data} onPage={next=>{setPage(next);setLoading(true)}}/>
   </>:<p className="empty-state">Chưa có kế hoạch đã hoàn thành bảo trì.</p>}
  </section>
 </div>
}
function HistoryItemsPage({planId}:{planId?:string}){
 const {user}=useAuth();const [data,setData]=useState<PageResponse<HistoryRow>|null>(null);const [page,setPage]=useState(0);const [input,setInput]=useState('');const [search,setSearch]=useState('');const [error,setError]=useState<unknown>(null);const [loading,setLoading]=useState(true);const [refresh,setRefresh]=useState(0)
 useEffect(()=>{let active=true;apiRequest<PageResponse<HistoryRow>>(`/api/maintenance-history?${new URLSearchParams({page:String(page),size:'20',search,...(planId?{planId}:{})})}`).then(r=>{if(active){setData(r);setError(null);setLoading(false)}}).catch(e=>{if(active){setError(e);setLoading(false)}});return()=>{active=false}},[page,search,refresh,planId])
 return <div className="page-stack catalog-page"><div className="catalog-hero"><div><p className="eyebrow">HỒ SƠ BẢO TRÌ</p><h1>{planId?data?.content[0]?.planTitle||'Chi tiết lịch sử kế hoạch':'Lịch sử bảo trì'}</h1><p>Các lần bảo trì đã hoàn thành, kết quả thiết bị và báo cáo được lưu để tra cứu.</p></div><button className="button secondary" onClick={()=>{setLoading(true);setRefresh(v=>v+1)}}>Tải lại</button></div>{planId&&<Link className="text-link" to="/maintenance-history">← Lịch sử theo kế hoạch</Link>}<WorkflowError error={error}/><section className="panel business-panel"><form className="history-search" onSubmit={e=>{e.preventDefault();setPage(0);setSearch(input.trim());setRefresh(v=>v+1);setLoading(true)}}><label>Tìm thiết bị hoặc kế hoạch<input type="search" value={input} onChange={e=>setInput(e.target.value)} placeholder="Mã, tên thiết bị hoặc tên kế hoạch…"/></label><button className="button secondary">Tìm kiếm</button></form><div className="panel-heading"><h2>Kết quả bảo trì đã lưu</h2><span className="muted">{data?.totalElements??0} kết quả</span></div>{loading?<p>Đang tải lịch sử…</p>:data?.content.length?<><div className="table-scroll"><table className="data-table catalog-table"><thead><tr><th>Thiết bị / khoa</th><th>{planId?'Công ty bảo trì':'Kế hoạch / công ty'}</th><th>Hoàn thành</th><th>Kết quả</th><th>Tra cứu</th></tr></thead><tbody>{data.content.map(r=><tr key={r.itemId}><td><strong>{r.equipmentCode} · {r.equipmentName}</strong><span className="row-sub">{r.departmentName}{!r.equipmentActive&&' · Đã xóa khỏi danh mục'}</span></td><td>{planId?<strong>{r.provider||'—'}</strong>:<><strong>{r.planTitle}</strong><span className="row-sub">{r.provider||'—'}</span></>}</td><td>{dateTime(r.completedAt)}</td><td><StatusBadge label={r.status==='REPAIR_REQUIRED'?'Có hỏng hóc':'Bảo trì xong'} tone={r.status==='REPAIR_REQUIRED'?'amber':'teal'}/>{r.resultNote&&<p className="tracking-note">{r.resultNote}</p>}</td><td><div className="table-actions">{r.reportStatus&&<Link className="table-link" to={`/plans/${r.planId}/report`}>{r.reportStatus==='FINAL'?'Xem báo cáo':'Báo cáo nháp'}</Link>}{user?.role!=='BAN_GIAM_DOC'&&<Link className="text-link" to={`/equipment/${r.equipmentId}/history`}>Lịch sử thiết bị</Link>}</div></td></tr>)}</tbody></table></div><Pagination data={data} onPage={p=>{setPage(p);setLoading(true)}}/></>:<p className="empty-state">Chưa có kết quả bảo trì trong phạm vi hiển thị.</p>}</section></div>
}
