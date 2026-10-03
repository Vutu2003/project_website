import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { plansApi } from '../api/plansApi'
import { Pagination } from '../components/Pagination'
import { WarrantyModal } from '../components/WarrantyModal'
import { serviceChoiceLabels } from '../utils/warranty'
import { StatusBadge } from '../components/StatusBadge'
import { UserInputError } from '../utils/UserInputError'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { PageResponse, Plan, PlanItem, PlanStatus } from '../types/workflow'
import { assignmentRouteLabels, businessDate, itemStatusLabels, planStatusLabels } from '../utils/workflowLabels'

const trackingStates: PlanStatus[] = ['APPROVED', 'IN_PROGRESS', 'AWAITING_REPORT']

export function MaintenanceProgressPage() {
  const { planId } = useParams()
  const id = Number(planId)
  const inPlan = planId !== undefined
  const [search, setSearch] = useSearchParams()
  const requestedStatus = search.get('status') as PlanStatus | null
  const status = requestedStatus && trackingStates.includes(requestedStatus) ? requestedStatus : 'APPROVED'
  const [page, setPage] = useState(0)
  const [plans, setPlans] = useState<PageResponse<Plan> | null>(null)
  const [plan, setPlan] = useState<Plan | null>(null)
  const [items, setItems] = useState<PageResponse<PlanItem> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [refresh, setRefresh] = useState(0)
  const [warranty, setWarranty] = useState<PlanItem | null>(null)

  useEffect(() => { setPage(0) }, [planId])

  useEffect(() => {
    let active = true
    async function load() {
      setLoading(true)
      setError(null)
      setPlan(null)
      setItems(null)
      try {
        if (inPlan) {
          if (!Number.isInteger(id) || id <= 0) throw new UserInputError('Đường dẫn kế hoạch không hợp lệ.')
          const selected = await plansApi.detail(id)
          if (!active) return
          setPlan(selected)
          if (trackingStates.includes(selected.status)) {
            const result = await plansApi.items(id, page, 20)
            if (active) setItems(result)
          }
        } else {
          const result = await plansApi.list(page, 10, status)
          if (active) setPlans(result)
        }
      } catch (failure) { if (active) setError(failure) }
      finally { if (active) setLoading(false) }
    }
    void load()
    return () => { active = false }
  }, [id, inPlan, page, status, refresh])

  return <div className="page-stack maintenance-progress-page">
    <div className="page-title-row"><div className="page-title-block">
      <p className="eyebrow">PHÒNG VẬT TƯ Y TẾ · UC08</p>
      <h1>Theo dõi tiến độ bảo trì</h1>
      <p>{inPlan ? 'Chọn thiết bị để bắt đầu bảo trì, cập nhật tiến độ và xem nhật ký.' : 'Theo dõi các kế hoạch đã được Ban Giám đốc phê duyệt.'}</p>
    </div><button className="button secondary" type="button" onClick={() => setRefresh(value => value + 1)}>Tải lại</button></div>
    {inPlan && <Link className="text-link" to={plan && trackingStates.includes(plan.status) ? `/maintenance-progress?status=${plan.status}` : "/maintenance-progress"}>← Các kế hoạch đã phê duyệt</Link>}
    {error ? <WorkflowError error={error} onReload={() => setRefresh(value => value + 1)} /> : loading ? <p className="muted">Đang tải tiến độ bảo trì…</p> : !inPlan && plans ? <section className="panel business-panel">
      <div className="panel-heading"><div><h2>Kế hoạch đã được BGĐ duyệt</h2><p>{plans.totalElements} kế hoạch · chọn kế hoạch để theo dõi thiết bị</p></div>
        <label className="inline-filter">Trạng thái kế hoạch<select value={status} onChange={event => { setSearch({ status: event.target.value }); setPage(0) }}>
          {trackingStates.map(value => <option key={value} value={value}>{planStatusLabels[value]}</option>)}
        </select></label></div>
      {plans.content.length === 0 ? <p className="empty-state">Không có kế hoạch {planStatusLabels[status].toLowerCase()} để theo dõi.</p> : <div className="table-scroll"><table className="data-table compact-table"><thead><tr><th>Kế hoạch</th><th>Thời gian</th><th>Người lập</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
        {plans.content.filter(row => row.status === status).map(row => <tr key={row.id}><td><strong>{row.title}</strong><span className="row-sub">#{row.id}</span></td><td>{businessDate(row.periodStart)} – {businessDate(row.periodEnd)}</td><td>{row.createdByName}</td><td><StatusBadge label={planStatusLabels[row.status]} tone={row.status === 'APPROVED' ? 'teal' : 'neutral'} /></td><td><Link className="table-link" to={`/maintenance-progress/plans/${row.id}`}>Theo dõi kế hoạch</Link></td></tr>)}
      </tbody></table></div>}
      <Pagination data={plans} onPage={setPage} />
    </section> : plan && !trackingStates.includes(plan.status) ? <section className="panel business-panel">
      <h2>{plan.title}</h2><StatusBadge label={planStatusLabels[plan.status]} />
      <p className="retention-note">Kế hoạch này chưa được phê duyệt hoặc đã kết thúc theo dõi tiến độ. Chỉ các kế hoạch đã được BGĐ duyệt mới xuất hiện tại đây.</p>
      <Link className="text-link" to={`/plans/${plan.id}`}>Xem thông tin kế hoạch</Link>
    </section> : plan && items ? <section className="panel business-panel">
      <div className="panel-heading"><div><h2>{plan.title}</h2><p>{businessDate(plan.periodStart)} – {businessDate(plan.periodEnd)} · {items.totalElements} thiết bị</p></div><StatusBadge label={planStatusLabels[plan.status]} /></div>
      {plan.pendingVendorApproval && <p className="retention-note">Cần BGĐ duyệt xong các đơn vị đề xuất trước khi bắt đầu thực hiện kế hoạch.</p>}
      {items.content.length === 0 ? <p className="empty-state">Kế hoạch chưa có thiết bị để theo dõi.</p> : <div className="table-scroll"><table className="data-table compact-table"><thead><tr><th>Thiết bị / khoa</th><th>Ngày dự kiến</th><th>Trạng thái</th><th>Hình thức</th><th>Đơn vị</th><th>Thao tác</th></tr></thead><tbody>
        {items.content.map(row => <tr key={row.id}><td><strong>{row.equipmentCode} · {row.equipmentName}</strong><span className="row-sub">{row.departmentNameAtPlan}</span></td><td>{businessDate(row.plannedDate)}</td><td><StatusBadge label={['UNDER_CONTRACT', 'ASSIGNED_EXTERNAL'].includes(row.status) ? 'Chưa bắt đầu' : itemStatusLabels[row.status]} tone={row.status === 'COMPLETED' ? 'teal' : 'neutral'} /></td><td>{row.serviceChoice ? serviceChoiceLabels[row.serviceChoice] : row.assignmentRoute ? assignmentRouteLabels[row.assignmentRoute] : 'Ngoài hợp đồng · chờ duyệt đơn vị'}</td><td>{row.assignedProviderName || row.proposedProviderName || 'Chưa phân công'}</td><td><div className="table-actions"><Link className="table-link" to={`/plans/${plan.id}/items/${row.id}/execution`}>Xem / cập nhật tiến độ</Link><button type="button" className="button secondary compact" onClick={() => setWarranty(row)}>Thông tin bảo hành</button></div></td></tr>)}
      </tbody></table></div>}
      <Pagination data={items} onPage={setPage} />
    </section> : null}
    {warranty && <WarrantyModal equipmentId={warranty.equipmentId} onClose={() => setWarranty(null)} />}
  </div>
}
