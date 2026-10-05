import { ReceivedReportsPage } from './ReceivedReportsPage'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { plansApi } from '../api/plansApi'
import { useAuth } from '../auth/useAuth'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { PageResponse, Plan, PlanStatus } from '../types/workflow'
import { businessDate, planStatusLabels } from '../utils/workflowLabels'

export function ReportListPage(){const {user}=useAuth();return user?.role==='KHOA_PHONG'||user?.role==='BAN_GIAM_DOC'?<ReceivedReportsPage/>:<PlanningReportsPage/>}
function PlanningReportsPage() {
  const { user } = useAuth()
  const [status, setStatus] = useState<Extract<PlanStatus, 'AWAITING_REPORT' | 'REPORTED'>>(user?.role === 'BAN_GIAM_DOC' ? 'REPORTED' : 'AWAITING_REPORT')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<Plan> | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const [refresh, setRefresh] = useState(0)
  useEffect(() => {
    let active = true
    plansApi.list(page, 10, status).then(rows => { if (active) { setData(rows); setError(null); setLoading(false) } })
      .catch(failure => { if (active) { setError(failure); setLoading(false) } })
    return () => { active = false }
  }, [page, status, refresh])
  return <div className="page-stack">
    <div className="page-title-block"><p className="eyebrow">BÁO CÁO BẢO TRÌ</p><h1>Báo cáo bảo trì</h1>
      <p>Các kế hoạch đã hoàn thành bảo trì. Mở báo cáo để kiểm tra, hoàn tất và gửi đến người nhận.</p></div>
    <section className="panel business-panel"><div className="panel-heading"><h2>Kế hoạch</h2>
      <label className="inline-filter">Trạng thái <select value={status} onChange={event => { setStatus(event.target.value as typeof status); setPage(0); setLoading(true) }}>
        <option value="AWAITING_REPORT">Chờ báo cáo</option><option value="REPORTED">Đã báo cáo</option>
      </select></label></div>
      {loading ? <p className="muted">Đang tải…</p> : error ? <WorkflowError error={error} onReload={() => { setLoading(true); setRefresh(value => value + 1) }} /> : data?.content.length === 0 ?
        <p className="empty-state">Không có kế hoạch ở trạng thái này.</p> : data && <><div className="table-scroll"><table className="data-table"><thead><tr><th>Kế hoạch</th><th>Kỳ bảo trì</th><th>Trạng thái</th><th></th></tr></thead><tbody>
          {data.content.map(plan => <tr key={plan.id}><td><strong>{plan.title}</strong><span className="row-sub">#{plan.id} · v{plan.version}</span></td>
            <td>{businessDate(plan.periodStart)} – {businessDate(plan.periodEnd)}</td>
            <td><StatusBadge label={planStatusLabels[plan.status]} tone={plan.status === 'REPORTED' ? 'teal' : 'neutral'} /></td>
            <td><Link className="table-link" to={`/plans/${plan.id}/report`}>Mở báo cáo</Link></td></tr>)}</tbody></table></div>
          <Pagination data={data} onPage={next => { setPage(next); setLoading(true) }} /></>}
    </section>
  </div>
}
