import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { reportsApi } from '../api/reportsApi'
import type { ReceivedReport } from '../types/report'
import type { PageResponse } from '../types/workflow'
import { useAuth } from '../auth/useAuth'
import { WorkflowError } from '../components/WorkflowFeedback'
import { Pagination } from '../components/Pagination'
import { businessDate, dateTime } from '../utils/workflowLabels'
import { StatusBadge } from '../components/StatusBadge'
export function ReceivedReportsPage(){
 const {user}=useAuth();const [data,setData]=useState<PageResponse<ReceivedReport>|null>(null);const [page,setPage]=useState(0);const [error,setError]=useState<unknown>(null)
 useEffect(()=>{let active=true;reportsApi.received(page,10).then(r=>{if(active){setData(r);setError(null)}}).catch(e=>{if(active)setError(e)});return()=>{active=false}},[page])
 return <div className="page-stack catalog-page"><div className="catalog-hero"><div><p className="eyebrow">BÁO CÁO ĐƯỢC CHIA SẺ</p><h1>Báo cáo bảo trì</h1><p>{user?.role==='KHOA_PHONG'?'Báo cáo VTYT gửi cho khoa/phòng của bạn. Nội dung thiết bị được giới hạn theo khoa/phòng.':'Báo cáo chính thức được Phòng VTYT gửi đến Ban Giám đốc.'}</p></div><span className="catalog-hero-symbol" aria-hidden="true">▤</span></div><WorkflowError error={error}/><section className="panel business-panel"><div className="panel-heading"><h2>Báo cáo đã nhận</h2><span className="muted">{data?.totalElements ?? 0} báo cáo</span></div>{!data&&!error?<p>Đang tải báo cáo…</p>:data&&!data.content.length?<p className="empty-state">Chưa có báo cáo được gửi đến bạn.</p>:data&&<><div className="table-scroll"><table className="data-table catalog-table"><thead><tr><th>Kế hoạch</th><th>Kỳ bảo trì</th><th>Số thiết bị</th><th>Người gửi</th><th>Ngày nhận</th><th>Trạng thái</th></tr></thead><tbody>{data.content.map(r=><tr key={r.id}><td><Link className="table-link" to={`/plans/${r.id}/report`}>{r.title}</Link></td><td>{businessDate(r.period_start)} – {businessDate(r.period_end)}</td><td>{r.equipment_count}</td><td>{r.sent_by}</td><td>{dateTime(r.sent_at)}</td><td><StatusBadge label="Báo cáo chính thức" tone="teal"/></td></tr>)}</tbody></table></div><Pagination data={data} onPage={setPage}/></>}</section></div>
}
