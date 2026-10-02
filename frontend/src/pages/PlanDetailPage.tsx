import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError } from '../api/types'
import { plansApi } from '../api/plansApi'
import { useAuth } from '../auth/useAuth'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { WorkflowError, WorkflowSuccess } from '../components/WorkflowFeedback'
import type { PageResponse, Plan, PlanItem } from '../types/workflow'
import { assignmentRouteLabels, businessDate, dateTime, itemStatusLabels, planStatusLabels } from '../utils/workflowLabels'

export function PlanDetailPage() {
  const { planId } = useParams()
  const id = Number(planId)
  const { user } = useAuth()
  const location = useLocation()
  const [plan, setPlan] = useState<Plan | null>(null)
  const [items, setItems] = useState<PageResponse<PlanItem> | null>(null)
  const [itemPage, setItemPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [flash, setFlash] = useState<string | null>((location.state as { flash?: string } | null)?.flash || null)
  const [reloadKey, setReloadKey] = useState(0)
  const reload = useCallback(() => { setLoading(true); setReloadKey(value => value + 1) }, [])
  useEffect(() => {
    if (!Number.isInteger(id) || id <= 0) { setError(new ApiError(404, 'PLAN_NOT_FOUND', 'Không tìm thấy kế hoạch.')); setLoading(false); return }
    let active = true
    Promise.all([plansApi.detail(id), plansApi.items(id, itemPage, 20)]).then(([current, rows]) => {
      if (active) { setPlan(current); setItems(rows); setError(null); setLoading(false) }
    }).catch(failure => { if (active) { setError(failure); setLoading(false) } })
    return () => { active = false }
  }, [id, itemPage, reloadKey])
  function changed(message: string) { setFlash(message); reload() }
  async function submitPlan() {
    if (!plan || !window.confirm('Gửi kế hoạch này lên Ban Giám đốc để phê duyệt?')) return
    setBusy(true); setError(null)
    try {
      const first = await plansApi.items(plan.id, 0, 100)
      const all = [...first.content]
      for (let p = 1; p < first.totalPages; p++) all.push(...(await plansApi.items(plan.id, p, 100)).content)
      const incomplete = all.find(i => i.status !== 'UNDER_CONTRACT' && (i.status !== 'PENDING_PROPOSAL' || !i.proposedProviderId || !i.rationale?.trim()))
      if (incomplete) { setError(new ApiError(409, 'PLAN_ITEM_INCOMPLETE', `Thiết bị ${incomplete.equipmentCode} chưa đủ hình thức, đơn vị hoặc căn cứ đề xuất. Hãy hiệu chỉnh kế hoạch.`)); return }
      await plansApi.submit(plan.id, plan.version); changed('Đã gửi kế hoạch để phê duyệt.')
    }
    catch (failure) { setError(failure) } finally { setBusy(false) }
  }

  if (loading && !plan) return <p className="muted">Đang tải kế hoạch…</p>
  if (!plan) return <div className="page-stack"><WorkflowError error={error} onReload={reload} /><Link to="/plans">Về danh sách</Link></div>
  const canEdit = user?.role === 'PHONG_VTYT' && ['DRAFT', 'REVISION_REQUIRED'].includes(plan.status)
  const executionApproved = ['APPROVED', 'IN_PROGRESS'].includes(plan.status)
  return <div className="page-stack">
    <div className="page-title-row"><div className="page-title-block"><p className="eyebrow">KẾ HOẠCH #{plan.id}</p><h1>{plan.title}</h1>
      <p>{businessDate(plan.periodStart)} – {businessDate(plan.periodEnd)} · Tạo bởi {plan.createdByName}</p></div>
      <div className="header-actions">{['AWAITING_REPORT', 'REPORTED'].includes(plan.status) && ['PHONG_VTYT', 'BAN_GIAM_DOC'].includes(user?.role ?? '') && <Link className="button secondary" to={`/plans/${plan.id}/report`}>Báo cáo</Link>}{canEdit && <Link className="button secondary" to={`/plans/${plan.id}/edit`}>Chỉnh sửa</Link>}
        {user?.role === 'PHONG_VTYT' && plan.status === 'DRAFT' && <button className="button primary" disabled={busy} type="button" onClick={() => void submitPlan()}>{busy ? 'Đang gửi…' : 'Gửi phê duyệt'}</button>}</div>
    </div>
    <WorkflowSuccess message={flash} /><WorkflowError error={error} onReload={reload} />
    <section className="panel business-panel summary-panel"><div><span className="card-label">TRẠNG THÁI</span><p><StatusBadge label={planStatusLabels[plan.status]} tone={plan.status === 'APPROVED' ? 'teal' : plan.status === 'REVISION_REQUIRED' ? 'amber' : 'neutral'} /></p></div>
      <div><span className="card-label">PHIÊN BẢN</span><strong>v{plan.version}</strong></div>
      <div><span className="card-label">NGÀY TẠO</span><strong>{dateTime(plan.createdAt)}</strong></div>
      <button className="button secondary" type="button" onClick={reload}>Tải lại</button></section>
    <section className="panel business-panel"><div className="panel-heading"><div><h2>Hạng mục thiết bị</h2><p>Dữ liệu hiện tại từ backend</p></div></div>
      {loading ? <p className="muted">Đang tải lại…</p> : items?.content.length === 0 ? <p className="empty-state">Kế hoạch chưa có hạng mục.</p> : items && <>
        <div className="table-scroll"><table className="data-table"><thead><tr><th>Thiết bị</th><th>Khoa tại kế hoạch</th><th>Ngày dự kiến</th><th>Trạng thái</th><th>Đơn vị / tuyến</th><th></th></tr></thead>
          <tbody>{items.content.map(item => <tr key={item.id}><td><strong>{item.equipmentCode}</strong><span className="row-sub">{item.equipmentName} · v{item.version}</span></td>
            <td>{item.departmentNameAtPlan}</td><td>{businessDate(item.plannedDate)}</td><td><StatusBadge label={itemStatusLabels[item.status]} tone={item.status === 'PENDING_PROPOSAL' ? 'amber' : 'neutral'} /></td>
            <td>{item.assignedProviderName || '—'}<span className="row-sub">{item.assignmentRoute ? assignmentRouteLabels[item.assignmentRoute] : 'Chưa phân tuyến'}</span></td>
            <td>{['UNDER_CONTRACT', 'ASSIGNED_EXTERNAL', 'IN_MAINTENANCE', 'AWAITING_TECHNICAL_ACCEPTANCE', 'AWAITING_HANDOVER', 'REWORK_REQUIRED', 'REPAIR_REQUIRED', 'COMPLETED'].includes(item.status) && executionApproved && user?.role !== 'BAN_GIAM_DOC' && user?.role !== 'ADMIN' && <Link className="table-link" to={`/plans/${plan.id}/items/${item.id}/execution`}>Thực hiện / bàn giao</Link>}{canEdit && <Link className="table-link" to={`/plans/${plan.id}/edit`}>Hiệu chỉnh hình thức</Link>}{item.proposedProviderName && <span className="row-sub">Đề xuất: {item.proposedProviderName}<br />Căn cứ: {item.rationale || 'Chưa nhập'}</span>}{item.status === 'PLANNED' && <span className="warning-text">Chưa chọn hình thức bảo trì</span>}{!executionApproved && ['UNDER_CONTRACT', 'PENDING_PROPOSAL'].includes(item.status) && <span className="row-sub">Hình thức bảo trì đã được xác định. Chỉ có thể bắt đầu thực hiện sau khi kế hoạch được phê duyệt.</span>}</td></tr>)}</tbody></table></div>
        <Pagination data={items} onPage={next => { setItemPage(next); setLoading(true) }} />
      </>}
    </section>
    <Link className="text-link" to="/plans">← Danh sách kế hoạch</Link>
  </div>
}
