import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { plansApi } from '../api/plansApi'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { PageResponse, Plan, PlanItem } from '../types/workflow'
import { businessDate, itemStatusLabels, planStatusLabels } from '../utils/workflowLabels'

export function ExecutionQueuePage() {
  const { planId } = useParams()
  const { user } = useAuth()
  const isVtyt = user?.role === 'PHONG_VTYT'
  const id = Number(planId)
  const inPlan = planId !== undefined
  const [page, setPage] = useState(0)
  const [plans, setPlans] = useState<PageResponse<Plan> | null>(null)
  const [plan, setPlan] = useState<Plan | null>(null)
  const [items, setItems] = useState<PageResponse<PlanItem> | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const [refresh, setRefresh] = useState(0)
  useEffect(() => { setPage(0); setLoading(true) }, [planId])
  useEffect(() => {
    if (isVtyt) return
    let active = true
    const request = inPlan ? Promise.all([plansApi.detail(id), plansApi.items(id, page, 20)]) : plansApi.list(page, 10)
    request.then(result => {
      if (!active) return
      if (Array.isArray(result)) { setPlan(result[0]); setItems(result[1]) }
      else setPlans(result)
      setError(null); setLoading(false)
    }).catch(failure => { if (active) { setError(failure); setLoading(false) } })
    return () => { active = false }
  }, [inPlan, id, page, refresh, isVtyt])
  function reload() { setLoading(true); setRefresh(value => value + 1) }
  if (isVtyt) return <Navigate replace to={inPlan ? `/maintenance-progress/plans/${planId}` : "/maintenance-progress"} />
  return <div className="page-stack">
    <div className="page-title-block"><p className="eyebrow">UC08–UC10 · THỰC HIỆN & NGHIỆM THU</p>
      <h1>{inPlan ? plan?.title || 'Hạng mục kế hoạch' : 'Kế hoạch có thể theo dõi'}</h1>
      <p>{inPlan ? 'Chọn một hạng mục để xem mọi lần thực hiện và thao tác được phép.' : 'Chọn kế hoạch trong phạm vi truy cập để xem các hạng mục. Danh sách lấy từ backend.'}</p></div>
    <WorkflowError error={error} onReload={reload} />
    {loading ? <p className="muted">Đang tải dữ liệu…</p> : !inPlan && plans ? <section className="panel business-panel">
      <div className="panel-heading"><h2>Danh sách kế hoạch</h2></div>
      {plans.content.length === 0 ? <p className="empty-state">Chưa có kế hoạch trong phạm vi hiển thị.</p> : <div className="table-scroll"><table className="data-table"><thead><tr><th>Kế hoạch</th><th>Thời gian</th><th>Trạng thái</th><th></th></tr></thead><tbody>
        {plans.content.map(row => <tr key={row.id}><td><strong>{row.title}</strong><span className="row-sub">#{row.id}</span></td><td>{businessDate(row.periodStart)} – {businessDate(row.periodEnd)}</td>
          <td><StatusBadge label={planStatusLabels[row.status]} /></td><td><Link className="table-link" to={`/execution/plans/${row.id}`}>Xem hạng mục</Link></td></tr>)}</tbody></table></div>}
      <Pagination data={plans} onPage={next => { setPage(next); setLoading(true) }} />
    </section> : inPlan && plan && items ? <section className="panel business-panel">
      <div className="panel-heading"><div><h2>Hạng mục</h2><p>{items.totalElements} hạng mục thuộc phạm vi được phép</p></div><StatusBadge label={planStatusLabels[plan.status]} /></div>
      {items.content.length === 0 ? <p className="empty-state">Không có hạng mục trong phạm vi truy cập.</p> : <div className="table-scroll"><table className="data-table"><thead><tr><th>Thiết bị</th><th>Khoa tại kế hoạch</th><th>Trạng thái</th><th>Đơn vị</th><th></th></tr></thead><tbody>
        {items.content.map(row => <tr key={row.id}><td><strong>{row.equipmentCode}</strong><span className="row-sub">{row.equipmentName}</span></td><td>{row.departmentNameAtPlan}</td>
          <td><StatusBadge label={itemStatusLabels[row.status]} /></td><td>{row.assignedProviderName || '—'}</td>
          <td><Link className="table-link" to={`/plans/${plan.id}/items/${row.id}/execution`}>Xem thực hiện</Link></td></tr>)}</tbody></table></div>}
      <Pagination data={items} onPage={next => { setPage(next); setLoading(true) }} />
    </section> : null}
    {inPlan && <Link className="text-link" to="/execution">← Danh sách kế hoạch</Link>}
  </div>
}
