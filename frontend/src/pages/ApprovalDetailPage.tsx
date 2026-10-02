import { UserInputError } from '../utils/UserInputError'
import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/types'
import { approvalsApi } from '../api/approvalsApi'
import { plansApi } from '../api/plansApi'
import { StatusBadge } from '../components/StatusBadge'
import { Pagination } from '../components/Pagination'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { ApprovalOutcome, ApprovalReview, PageResponse, Plan, PlanItem } from '../types/workflow'
import { approvalTypeLabels, businessDate, coverageLabels, dateTime, itemStatusLabels, planStatusLabels } from '../utils/workflowLabels'

export function ApprovalDetailPage() {
  const { requestId } = useParams()
  const id = Number(requestId)
  const navigate = useNavigate()
  const [review, setReview] = useState<ApprovalReview | null>(null)
  const [plan, setPlan] = useState<Plan | null>(null)
  const [items, setItems] = useState<PageResponse<PlanItem> | null>(null)
  const [outcome, setOutcome] = useState<ApprovalOutcome>('APPROVE')
  const [comment, setComment] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [validation, setValidation] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [itemPage, setItemPage] = useState(0)
  const reload = useCallback(() => { setLoading(true); setReloadKey(value => value + 1) }, [])
  useEffect(() => {
    if (!Number.isInteger(id) || id <= 0) { setError(new ApiError(404, 'APPROVAL_REQUEST_NOT_FOUND', 'Không tìm thấy yêu cầu.')); setLoading(false); return }
    let active = true
    approvalsApi.review(id).then(async current => {
      const details = current.planId ? await Promise.all([plansApi.detail(current.planId), plansApi.items(current.planId, itemPage, 20)]) : [null, null] as const
      if (!active) return
      setReview(current); setPlan(details[0]); setItems(details[1]); setError(null); setLoading(false)
    }).catch(failure => { if (active) { setError(failure); setLoading(false) } })
    return () => { active = false }
  }, [id, reloadKey, itemPage])

  async function decide() {
    if (!review) return
    setValidation(null); setError(null)
    if (outcome === 'REVISION_REQUIRED' && !comment.trim()) { setValidation('Vui lòng ghi lý do yêu cầu chỉnh sửa.'); return }
    const verb = outcome === 'APPROVE' ? 'phê duyệt' : 'yêu cầu chỉnh sửa'
    if (!window.confirm(`Xác nhận ${verb} yêu cầu #${review.id}? Quyết định sẽ được lưu vào lịch sử.`)) return
    const version = review.requestType === 'PLAN_APPROVAL' ? review.planVersion : review.itemVersion
    if (version == null) { setError(new UserInputError('Thiếu phiên bản hiện tại. Vui lòng tải lại.')); return }
    setBusy(true)
    try {
      const result = await approvalsApi.decide(review.id, { version, outcome, comment: comment.trim() || null })
      const state = 'itemId' in result ? itemStatusLabels[result.status] : planStatusLabels[result.planStatus]
      navigate('/approvals', { replace: true, state: { flash: `Đã ghi quyết định. Trạng thái từ backend: ${state}.` } })
    } catch (failure) { setError(failure) } finally { setBusy(false) }
  }

  if (loading && !review) return <p className="muted">Đang tải yêu cầu phê duyệt…</p>
  if (!review) return <div className="page-stack"><WorkflowError error={error} onReload={reload} /><Link to="/approvals">Về hàng chờ</Link></div>
  return <div className="page-stack">
    <div className="page-title-block"><p className="eyebrow">YÊU CẦU #{review.id}</p><h1>{approvalTypeLabels[review.requestType]}</h1>
      <p>Người gửi: {review.createdByName} · {dateTime(review.submittedAt)}</p></div>
    <section className="panel business-panel summary-panel"><div><span className="card-label">ĐỐI TƯỢNG</span><strong>{review.planTitle || `Kế hoạch #${review.planId}`}</strong></div>
      <div><span className="card-label">TRẠNG THÁI</span><StatusBadge label="Chờ quyết định" tone="amber" /></div>
      <div><span className="card-label">PHIÊN BẢN HIỆN TẠI</span><strong>v{review.requestType === 'PLAN_APPROVAL' ? review.planVersion : review.itemVersion}</strong></div>
      <button className="button secondary" type="button" onClick={reload}>Tải lại</button></section>
    {plan && <section className="panel business-panel"><div className="panel-heading"><div><h2>Thông tin kế hoạch</h2><p>{businessDate(plan.periodStart)} – {businessDate(plan.periodEnd)} · {planStatusLabels[plan.status]}</p></div>
      <Link className="table-link" to={`/plans/${plan.id}`}>Xem kế hoạch</Link></div>
      {items && <><p className="muted">{items.totalElements} hạng mục · hình thức và đơn vị do Phòng VTYT chuẩn bị.</p>
        <div className="table-scroll"><table className="data-table"><thead><tr><th>Thiết bị / khoa</th><th>Ngày dự kiến</th><th>Trạng thái</th><th>Hình thức</th><th>Đơn vị / căn cứ</th></tr></thead>
          <tbody>{items.content.map(item => <tr key={item.id}><td>{item.equipmentCode} · {item.equipmentName}<span className="row-sub">{item.departmentNameAtPlan}</span></td><td>{businessDate(item.plannedDate)}</td><td>{itemStatusLabels[item.status]}</td>
            <td>{item.classification ? coverageLabels[item.classification] : 'Chưa xác định'}{item.coverageId && <span className="row-sub">Hồ sơ hợp đồng #{item.coverageId}</span>}</td>
            <td>{item.classification === 'FREE' ? item.assignedProviderName || '—' : item.proposedProviderName || item.assignedProviderName || '—'}
              {item.rationale && <span className="row-sub">Căn cứ: {item.rationale}</span>}{item.warrantyImpactNote && <span className="row-sub">Ghi chú bảo hành: {item.warrantyImpactNote}</span>}</td>
          </tr>)}</tbody></table></div><Pagination data={items} onPage={next => { setLoading(true); setItemPage(next) }} /></>}
    </section>}
    {review.requestType === 'VENDOR_SELECTION' && <section className="panel business-panel"><div className="panel-heading"><h2>Đề xuất đơn vị bảo trì</h2></div>
      <dl className="detail-list"><div><dt>Thiết bị</dt><dd>{review.equipmentCode} · {review.equipmentName}</dd></div>
        <div><dt>Đơn vị được đề xuất</dt><dd>{review.proposedProviderName || '—'}</dd></div>
        <div><dt>Hình thức bảo trì</dt><dd>{review.coverageClassification ? coverageLabels[review.coverageClassification] : '—'}</dd></div>
        <div><dt>Căn cứ hợp đồng</dt><dd>{review.coverageBasis || 'Không có hợp đồng áp dụng; xem căn cứ chọn đơn vị bên dưới.'}</dd></div>
        <div><dt>Lý do đề xuất</dt><dd>{review.rationale || '—'}</dd></div>
        <div><dt>Ảnh hưởng bảo hành</dt><dd>{review.warrantyImpactNote || '—'}</dd></div></dl>
    </section>}
    <section className="panel business-panel"><div className="panel-heading"><div><h2>Quyết định của Ban Giám đốc</h2><p>Backend kiểm tra trạng thái và phiên bản khi ghi quyết định.</p></div></div>
      <div className="decision-options"><label><input type="radio" name="decision" checked={outcome === 'APPROVE'} onChange={() => setOutcome('APPROVE')} /> Phê duyệt</label>
        <label><input type="radio" name="decision" checked={outcome === 'REVISION_REQUIRED'} onChange={() => setOutcome('REVISION_REQUIRED')} /> Yêu cầu chỉnh sửa</label></div>
      <label className="block-label">Nhận xét / lý do {outcome === 'REVISION_REQUIRED' && <span className="warning-text">(bắt buộc)</span>}
        <textarea rows={4} value={comment} onChange={event => setComment(event.target.value)} placeholder="Nhập nhận xét cho quyết định" /></label>
      {validation && <div className="workflow-error" role="alert">{validation}</div>}
      <WorkflowError error={error} onReload={reload} />
      <div className="form-actions"><Link className="button secondary" to="/approvals">Hủy</Link><button className="button primary" type="button" disabled={busy || loading} onClick={() => void decide()}>{busy ? 'Đang ghi quyết định…' : 'Xác nhận quyết định'}</button></div>
    </section>
  </div>
}
