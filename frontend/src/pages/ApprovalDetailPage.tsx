import { UserInputError } from '../utils/UserInputError'
import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/types'
import { approvalsApi } from '../api/approvalsApi'
import { plansApi } from '../api/plansApi'
import { serviceChoiceLabels } from '../utils/warranty'
import { StatusBadge } from '../components/StatusBadge'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { ApprovalOutcome, ApprovalReview, Plan, PlanItem } from '../types/workflow'
import { approvalTypeLabels, businessDate, coverageLabels, dateTime, itemStatusLabels, planStatusLabels } from '../utils/workflowLabels'

export function ApprovalDetailPage() {
  const { requestId } = useParams()
  const id = Number(requestId)
  const navigate = useNavigate()
  const [review, setReview] = useState<ApprovalReview | null>(null)
  const [plan, setPlan] = useState<Plan | null>(null)
  const [items, setItems] = useState<PlanItem[] | null>(null)
  const [outcome, setOutcome] = useState<ApprovalOutcome>('APPROVE')
  const [comment, setComment] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [validation, setValidation] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const reload = useCallback(() => { setLoading(true); setReloadKey(value => value + 1) }, [])
  useEffect(() => {
    if (!Number.isInteger(id) || id <= 0) { setError(new ApiError(404, 'APPROVAL_REQUEST_NOT_FOUND', 'Không tìm thấy yêu cầu.')); setLoading(false); return }
    let active = true
    approvalsApi.review(id).then(async current => {
      const details = current.planId ? await Promise.all([plansApi.detail(current.planId), plansApi.allItems(current.planId)]) : [null, null] as const
      if (!active) return
      setReview(current); setPlan(details[0]); setItems(details[1]); setError(null); setLoading(false)
    }).catch(failure => { if (active) { setError(failure); setLoading(false) } })
    return () => { active = false }
  }, [id, reloadKey])

  async function decide() {
    if (!review) return
    setValidation(null); setError(null)
    if (outcome === 'REVISION_REQUIRED' && !comment.trim()) { setValidation('Vui lòng ghi lý do yêu cầu chỉnh sửa.'); return }
    const verb = outcome === 'APPROVE' ? 'phê duyệt' : 'yêu cầu chỉnh sửa'
    if (!window.confirm(review.requestType === 'PLAN_APPROVAL' && outcome === 'APPROVE' ? 'Phê duyệt kế hoạch và tất cả đơn vị bảo trì được đề xuất? VTYT có thể bắt đầu bảo trì ngay sau khi duyệt.' : `Xác nhận ${verb} yêu cầu #${review.id}? Quyết định sẽ được lưu vào lịch sử.`)) return
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
      <div><span className="card-label">QUÝ / NĂM</span><strong>{plan?.planQuarter || "—"} / {plan?.planYear || plan?.periodStart.slice(0, 4)}</strong></div>
      <button className="button secondary" type="button" onClick={reload}>Tải lại</button></section>
    {plan && <section className="panel business-panel"><div className="panel-heading"><div><h2>Thông tin kế hoạch</h2><p>{businessDate(plan.periodStart)} – {businessDate(plan.periodEnd)} · {planStatusLabels[plan.status]}</p></div>
      </div>
      {items && <><p className="muted">{items.length} thiết bị</p>
        <div className="table-scroll"><table className="data-table"><thead><tr>{['STT','Mã thiết bị','Tên thiết bị','Khoa/Phòng','Quý bảo trì','Trạng thái hợp đồng','Công ty / nhà cung cấp','Hợp đồng','Hình thức bảo trì','Đơn vị đề xuất / căn cứ'].map(h=><th key={h}>{h}</th>)}</tr></thead><tbody>{items.map((item,index)=><tr key={item.id}><td>{index+1}</td><td>{item.equipmentCode}</td><td>{item.equipmentName}</td><td>{item.departmentNameAtPlan}</td><td>{plan.planQuarter ?? '—'}</td><td>{item.classification==='FREE'?'Còn hạn':'Hết hạn'}</td><td>{item.assignedProviderName || '—'}</td><td>{item.contractCode || '—'}<span className="row-sub">{businessDate(item.contractEndDate)}</span></td><td>{item.classification==='FREE'?'Theo hợp đồng':'Ngoài hợp đồng'}</td><td>{item.classification!=='FREE' && <>{item.proposedProviderName || '—'}<span className="row-sub">Căn cứ: {item.rationale}</span>{item.warrantyImpactNote && <span className="row-sub">Ghi chú bảo hành: {item.warrantyImpactNote}</span>}</>}</td></tr>)}</tbody></table></div></>}
    </section>}
    {review.requestType === 'VENDOR_SELECTION' && <section className="panel business-panel"><div className="panel-heading"><h2>Đề xuất đơn vị bảo trì</h2></div>
      <dl className="detail-list"><div><dt>Thiết bị</dt><dd>{review.equipmentCode} · {review.equipmentName}</dd></div>
        <div><dt>Đơn vị được đề xuất</dt><dd>{review.proposedProviderName || '—'}</dd></div>
        <div><dt>Hình thức bảo trì</dt><dd>{review.serviceChoice ? serviceChoiceLabels[review.serviceChoice] : review.coverageClassification ? coverageLabels[review.coverageClassification] : '—'}</dd></div>
        <div><dt>Căn cứ hợp đồng</dt><dd>{review.coverageBasis || 'Không có hợp đồng áp dụng; xem căn cứ chọn đơn vị bên dưới.'}</dd></div>
        <div><dt>Lý do đề xuất</dt><dd>{review.rationale || '—'}</dd></div>
        <div><dt>Ảnh hưởng bảo hành</dt><dd>{review.warrantyImpactNote || '—'}</dd></div></dl>
    </section>}
    <section className="panel business-panel"><div className="panel-heading"><div><h2>Quyết định của Ban Giám đốc</h2><p>{review.requestType === 'PLAN_APPROVAL' ? 'Phê duyệt một lần cho kế hoạch và các đơn vị bảo trì được đề xuất. VTYT có thể bắt đầu ngay sau khi duyệt.' : 'Nhận xét sẽ được gửi đến Phòng VTYT.'}</p></div></div>
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
