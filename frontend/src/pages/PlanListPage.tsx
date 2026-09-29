import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { plansApi } from '../api/plansApi'
import { useAuth } from '../auth/useAuth'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { PageResponse, Plan, PlanStatus } from '../types/workflow'
import { businessDate, dateTime, planStatusLabels } from '../utils/workflowLabels'

export function PlanListPage() {
  const { user } = useAuth()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<PlanStatus | ''>('')
  const [data, setData] = useState<PageResponse<Plan> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const load = useCallback(() => { setRefreshKey(value => value + 1) }, [])
  useEffect(() => {
    let active = true
    plansApi.list(page, 10, status || undefined).then(result => {
      if (active) { setData(result); setError(null); setLoading(false) }
    }).catch(failure => { if (active) { setError(failure); setLoading(false) } })
    return () => { active = false }
  }, [page, status, refreshKey])

  return <div className="page-stack">
    <div className="page-title-row"><div className="page-title-block"><p className="eyebrow">KẾ HOẠCH BẢO TRÌ</p><h1>Danh sách kế hoạch</h1>
      <p>Dữ liệu và trạng thái được tải từ backend; chọn một kế hoạch để xem chi tiết.</p></div>
      {user?.role === 'PHONG_VTYT' && <Link className="button primary" to="/plans/new">Tạo kế hoạch</Link>}
    </div>
    <section className="panel business-panel">
      <div className="panel-heading"><div><h2>Kế hoạch hiện có</h2><p>Tối đa 10 bản ghi mỗi trang</p></div>
        <label className="inline-filter">Trạng thái <select value={status} onChange={event => { setStatus(event.target.value as PlanStatus | ''); setPage(0); setLoading(true) }}>
          <option value="">Tất cả</option>{Object.entries(planStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select></label>
      </div>
      {loading ? <p className="muted">Đang tải kế hoạch…</p> : error ? <WorkflowError error={error} onReload={() => { setLoading(true); load() }} /> : data && data.content.length === 0 ? <p className="empty-state">Chưa có kế hoạch trong phạm vi hiển thị.</p> : data && <>
        <div className="table-scroll"><table className="data-table"><thead><tr><th>Tiêu đề</th><th>Thời gian</th><th>Trạng thái</th><th>Người tạo</th><th>Ngày tạo</th><th></th></tr></thead>
          <tbody>{data.content.map(plan => <tr key={plan.id}><td><Link className="table-link" to={`/plans/${plan.id}`}>{plan.title}</Link><span className="row-sub">ID #{plan.id} · v{plan.version}</span></td>
            <td>{businessDate(plan.periodStart)} – {businessDate(plan.periodEnd)}</td>
            <td><StatusBadge label={planStatusLabels[plan.status]} tone={plan.status === 'APPROVED' ? 'teal' : plan.status === 'REVISION_REQUIRED' ? 'amber' : 'neutral'} /></td>
            <td>{plan.createdByName}</td><td>{dateTime(plan.createdAt)}</td><td><Link className="table-link" to={`/plans/${plan.id}`}>Xem</Link></td></tr>)}</tbody></table></div>
        <Pagination data={data} onPage={next => { setPage(next); setLoading(true) }} />
      </>}
    </section>
  </div>
}
