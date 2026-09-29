import { UserInputError } from '../utils/UserInputError'
import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError } from '../api/types'
import { equipmentApi } from '../api/equipmentApi'
import { plansApi } from '../api/plansApi'
import { providersApi } from '../api/providersApi'
import { useAuth } from '../auth/useAuth'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { WorkflowError, WorkflowSuccess } from '../components/WorkflowFeedback'
import type { CoverageEvidence, PageResponse, Plan, PlanItem, Provider } from '../types/workflow'
import { assignmentRouteLabels, businessDate, coverageLabels, dateTime, itemStatusLabels, planStatusLabels } from '../utils/workflowLabels'

function ItemWorkflowPanel({ item, onChanged, onReload }: {
  item: PlanItem; onChanged: (message: string) => void; onReload: () => void
}) {
  const [coverage, setCoverage] = useState<CoverageEvidence[] | null>(null)
  const [selectedCoverageId, setSelectedCoverageId] = useState('')
  const [providers, setProviders] = useState<Provider[] | null>(null)
  const [draftId, setDraftId] = useState<number | null>(null)
  const [providerId, setProviderId] = useState('')
  const [rationale, setRationale] = useState('')
  const [warrantyImpactNote, setWarrantyImpactNote] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    if (item.status === 'PLANNED') {
      equipmentApi.coverages(item.equipmentId).then(rows => { if (active) { setCoverage(rows); setLoading(false) } })
        .catch(failure => { if (active) { setError(failure); setLoading(false) } })
    } else if (item.status === 'PENDING_PROPOSAL') {
      Promise.all([providersApi.list(), providersApi.draft(item.id).catch(failure => {
        if (failure instanceof ApiError && failure.status === 404) return null
        throw failure
      })]).then(([rows, draft]) => {
        if (!active) return
        setProviders(rows); setDraftId(draft?.id ?? null); setProviderId(draft?.providerId ? String(draft.providerId) : '')
        setRationale(draft?.rationale ?? ''); setWarrantyImpactNote(draft?.warrantyImpactNote ?? ''); setLoading(false)
      }).catch(failure => { if (active) { setError(failure); setLoading(false) } })
    } else setLoading(false)
    return () => { active = false }
  }, [item.id, item.equipmentId, item.status])

  async function route() {
    if (!selectedCoverageId) { setError(new UserInputError('Vui lòng chọn hồ sơ coverage đã xác minh.')); return }
    if (!window.confirm('Xác nhận dùng hồ sơ coverage này để xác định tuyến bảo trì?')) return
    setBusy(true); setError(null)
    try { await providersApi.route(item.id, item.version, Number(selectedCoverageId)); onChanged('Đã xác định tuyến bảo trì từ hồ sơ coverage được chọn.') }
    catch (failure) { setError(failure) } finally { setBusy(false) }
  }
  function proposalBody() {
    return { version: item.version, providerId: providerId ? Number(providerId) : null,
      rationale: rationale.trim() || null, warrantyImpactNote: warrantyImpactNote.trim() || null }
  }
  async function createDraft() {
    setBusy(true); setError(null); setNotice(null)
    try {
      const response = await providersApi.createDraft(item.id, proposalBody())
      setDraftId(response.approvalRequestId)
      setNotice('Đã lưu bản nháp đề xuất. Bạn có thể bổ sung thông tin trước khi gửi duyệt.')
      onReload()
    } catch (failure) { setError(failure) } finally { setBusy(false) }
  }
  async function submitDraft() {
    if (!draftId) return
    if (!providerId || !rationale.trim()) { setError(new UserInputError('Vui lòng chọn đơn vị và nhập lý do đề xuất.')); return }
    if (!window.confirm('Gửi đề xuất đơn vị bảo trì lên Ban Giám đốc?')) return
    setBusy(true); setError(null)
    try { await providersApi.submitDraft(draftId, proposalBody()); onChanged('Đã gửi đề xuất đơn vị bảo trì để phê duyệt.') }
    catch (failure) { setError(failure) } finally { setBusy(false) }
  }

  return <section className="panel business-panel workflow-panel">
    <div className="panel-heading"><div><p className="eyebrow">THAO TÁC HẠNG MỤC #{item.id}</p><h2>{item.equipmentCode} · {item.equipmentName}</h2>
      <p>Phiên bản hạng mục {item.version} · {itemStatusLabels[item.status]}</p></div></div>
    {loading ? <p className="muted">Đang tải thông tin tuyến…</p> : item.status === 'PLANNED' ? <>
      <p className="muted">Chọn đúng một hồ sơ coverage. Backend sẽ kiểm tra thiết bị, hiệu lực và xác minh trước khi chuyển trạng thái.</p>
      {!coverage?.length ? <p className="empty-state">Thiết bị chưa có hồ sơ coverage. Không thể xác định tuyến bảo trì.</p> : <div className="coverage-list" role="radiogroup" aria-label="Hồ sơ coverage">
        {coverage.map(row => <label key={row.id} className={`coverage-option${row.classification === 'UNKNOWN' ? ' blocked' : ''}`}>
          <input type="radio" name={`coverage-${item.id}`} value={row.id} checked={selectedCoverageId === String(row.id)}
            disabled={row.classification === 'UNKNOWN'} onChange={() => setSelectedCoverageId(String(row.id))} />
          <span><strong>Hồ sơ #{row.id} · {coverageLabels[row.classification]}</strong>
            <small>Đơn vị hợp đồng: {row.providerName || '—'} · Hiệu lực: {businessDate(row.effectiveFrom)} – {businessDate(row.effectiveTo)}</small>
            <small>Xác minh: {row.verifiedByName || 'Chưa xác minh'} · {dateTime(row.verifiedAt)}</small>
            <small>Căn cứ: {row.basisNote || 'Chưa có'}{row.contractReference ? ` · Hợp đồng: ${row.contractReference}` : ''}</small>
            {row.classification === 'UNKNOWN' && <small className="warning-text">UNKNOWN không được tự chuyển thành ngoài hợp đồng.</small>}</span>
        </label>)}
      </div>}
      <button className="button primary" type="button" disabled={busy || !selectedCoverageId} onClick={() => void route()}>{busy ? 'Đang xác định…' : 'Xác định tuyến'}</button>
    </> : item.status === 'PENDING_PROPOSAL' ? <>
      <p className="muted">Bản nháp được lưu trên backend. Có thể mở lại sau khi làm mới trình duyệt.</p>
      {draftId && <p className="retention-note">Đang tiếp tục bản nháp đề xuất #{draftId}.</p>}
      <div className="form-grid"><label>Đơn vị bảo trì đề xuất<select value={providerId} onChange={event => setProviderId(event.target.value)}>
        <option value="">Chọn đơn vị đang hoạt động</option>{providers?.map(row => <option key={row.id} value={row.id}>{row.name}</option>)}</select></label>
        <label className="wide-field">Lý do đề xuất<textarea value={rationale} onChange={event => setRationale(event.target.value)} rows={3} placeholder="Nêu lý do chọn đơn vị" /></label>
        <label className="wide-field">Ảnh hưởng bảo hành (nếu có)<textarea value={warrantyImpactNote} onChange={event => setWarrantyImpactNote(event.target.value)} rows={2} /></label></div>
      <div className="form-actions left-actions">{!draftId ? <button className="button secondary" type="button" disabled={busy} onClick={() => void createDraft()}>{busy ? 'Đang lưu…' : 'Lưu bản nháp'}</button> :
        <button className="button primary" type="button" disabled={busy} onClick={() => void submitDraft()}>{busy ? 'Đang gửi…' : 'Gửi đề xuất duyệt'}</button>}</div>
    </> : null}
    <WorkflowSuccess message={notice} /><WorkflowError error={error} onReload={onReload} />
  </section>
}

export function PlanDetailPage() {
  const { planId } = useParams()
  const id = Number(planId)
  const { user } = useAuth()
  const location = useLocation()
  const [plan, setPlan] = useState<Plan | null>(null)
  const [items, setItems] = useState<PageResponse<PlanItem> | null>(null)
  const [itemPage, setItemPage] = useState(0)
  const [selectedItemId, setSelectedItemId] = useState<number | null>(null)
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
  function changed(message: string) { setFlash(message); setSelectedItemId(null); reload() }
  async function submitPlan() {
    if (!plan || !window.confirm('Gửi kế hoạch này lên Ban Giám đốc để phê duyệt?')) return
    setBusy(true); setError(null)
    try { await plansApi.submit(plan.id, plan.version); changed('Đã gửi kế hoạch để phê duyệt.') }
    catch (failure) { setError(failure) } finally { setBusy(false) }
  }

  if (loading && !plan) return <p className="muted">Đang tải kế hoạch…</p>
  if (!plan) return <div className="page-stack"><WorkflowError error={error} onReload={reload} /><Link to="/plans">Về danh sách</Link></div>
  const canEdit = user?.role === 'PHONG_VTYT' && ['DRAFT', 'REVISION_REQUIRED'].includes(plan.status)
  const canRoute = user?.role === 'PHONG_VTYT' && plan.status === 'APPROVED'
  const selectedItem = items?.content.find(item => item.id === selectedItemId)
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
            <td>{['UNDER_CONTRACT', 'ASSIGNED_EXTERNAL', 'IN_MAINTENANCE', 'AWAITING_TECHNICAL_ACCEPTANCE', 'AWAITING_HANDOVER', 'REWORK_REQUIRED', 'REPAIR_REQUIRED', 'COMPLETED'].includes(item.status) && user?.role !== 'BAN_GIAM_DOC' && user?.role !== 'ADMIN' && <Link className="table-link" to={`/plans/${plan.id}/items/${item.id}/execution`}>Thực hiện / bàn giao</Link>}{canRoute && ['PLANNED', 'PENDING_PROPOSAL'].includes(item.status) && <button className="button secondary compact" type="button" onClick={() => setSelectedItemId(current => current === item.id ? null : item.id)}>{selectedItemId === item.id ? 'Đóng' : item.status === 'PLANNED' ? 'Chọn coverage' : 'Đề xuất đơn vị'}</button>}</td></tr>)}</tbody></table></div>
        <Pagination data={items} onPage={next => { setItemPage(next); setSelectedItemId(null); setLoading(true) }} />
      </>}
    </section>
    {canRoute && selectedItem && <ItemWorkflowPanel key={`${selectedItem.id}-${selectedItem.version}-${selectedItem.status}`} item={selectedItem} onChanged={changed} onReload={reload} />}
    <Link className="text-link" to="/plans">← Danh sách kế hoạch</Link>
  </div>
}
