import { UserInputError } from '../utils/UserInputError'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { acceptancesApi } from '../api/acceptancesApi'
import { executionsApi } from '../api/executionsApi'
import { plansApi } from '../api/plansApi'
import { useAuth } from '../auth/useAuth'
import { StatusBadge } from '../components/StatusBadge'
import { ExecutionTimeline } from '../components/ExecutionTimeline'
import { orderedProgress, progressDetails, progressLabels } from '../utils/executionProgress'
import { AttemptHistory } from '../components/AttemptHistory'
import { PasswordInput } from '../components/PasswordInput'
import { WorkflowError, WorkflowSuccess } from '../components/WorkflowFeedback'
import type { AcceptanceResult, EquipmentExecutionHistory, MaintenanceProgressStatus } from '../types/execution'
import type { Plan, PlanItem } from '../types/workflow'
import { orderedAttempts } from '../utils/attempts'
import { serviceChoiceLabels } from '../utils/warranty'
import { assignmentRouteLabels, itemStatusLabels, planStatusLabels } from '../utils/workflowLabels'

async function loadVisibleItem(planId: number, itemId: number): Promise<[Plan, PlanItem]> {
  const plan = await plansApi.detail(planId)
  const first = await plansApi.items(planId, 0, 100)
  let item = first.content.find(row => row.id === itemId)
  for (let page = 1; !item && page < first.totalPages; page++) {
    item = (await plansApi.items(planId, page, 100)).content.find(row => row.id === itemId)
  }
  if (!item) throw new UserInputError('Không tìm thấy hạng mục trong phạm vi được phép.')
  return [plan, item]
}

export function ExecutionItemPage() {
  const { planId, itemId } = useParams()
  const id = Number(itemId)
  const parentId = Number(planId)
  const { user } = useAuth()
  const [plan, setPlan] = useState<Plan | null>(null)
  const [item, setItem] = useState<PlanItem | null>(null)
  const [history, setHistory] = useState<EquipmentExecutionHistory | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const inFlight = useRef(false)
  const [error, setError] = useState<unknown>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [progressStatus, setProgressStatus] = useState<MaintenanceProgressStatus>('IN_PROGRESS')
  const [progressNote, setProgressNote] = useState('')
  const [resultNote, setResultNote] = useState('')
  const [repairReason, setRepairReason] = useState('')
  const [technicalResult, setTechnicalResult] = useState<AcceptanceResult>('PASS')
  const [technicalConclusion, setTechnicalConclusion] = useState('')
  const [technicalRepair, setTechnicalRepair] = useState(false)
  const [handoverResult, setHandoverResult] = useState<AcceptanceResult>('PASS')
  const [handoverConclusion, setHandoverConclusion] = useState('')
  const [handoverRepair, setHandoverRepair] = useState(false)
  const [signerUsername, setSignerUsername] = useState('')
  const [signerPassword, setSignerPassword] = useState('')

  const reload = useCallback(() => { setLoading(true); setReloadKey(value => value + 1) }, [])
  useEffect(() => {
    if (!Number.isInteger(id) || id <= 0 || !Number.isInteger(parentId) || parentId <= 0) {
      setError(new UserInputError('Đường dẫn hạng mục không hợp lệ.')); setLoading(false); return
    }
    let active = true
    loadVisibleItem(parentId, id).then(async ([loadedPlan, loadedItem]) => {
      const loadedHistory = await executionsApi.history(loadedItem.equipmentId)
      if (!active) return
      setPlan(loadedPlan); setItem(loadedItem); setHistory(loadedHistory)
      setError(null); setLoading(false)
    }).catch(failure => { if (active) { setError(failure); setLoading(false) } })
    return () => { active = false }
  }, [id, parentId, reloadKey])

  const campaign = history?.campaigns.find(row => row.itemId === id && row.planId === parentId)
  const attempts = orderedAttempts(campaign?.attempts ?? [])
  const current = attempts.at(-1) ?? null
  const isVtyt = user?.role === 'PHONG_VTYT'
  const isDepartment = user?.role === 'KHOA_PHONG'
  const canStart = !loading && isVtyt && plan && !plan.pendingVendorApproval && ['APPROVED', 'IN_PROGRESS'].includes(plan.status) && item && ['UNDER_CONTRACT', 'ASSIGNED_EXTERNAL', 'REWORK_REQUIRED'].includes(item.status)
  const canWork = !loading && isVtyt && plan && ['APPROVED', 'IN_PROGRESS'].includes(plan.status) && item?.status === 'IN_MAINTENANCE' && current && current.endedAt == null
  const canTechnical = !loading && isVtyt && item?.status === 'AWAITING_TECHNICAL_ACCEPTANCE' && current?.endedAt != null && !current.technicalAcceptance
  const canHandover = !loading && isDepartment && item?.status === 'AWAITING_HANDOVER' && current?.technicalAcceptance?.result === 'PASS' && !current.handoverAcceptance

  const latestProgress = current ? orderedProgress(current.progress).at(-1) : undefined
  const latestDetails = latestProgress ? progressDetails(latestProgress) : undefined
  const executionLabel = item?.status === 'REPAIR_REQUIRED' || latestDetails?.status === 'DAMAGE_DETECTED' ? 'Có hỏng hóc' : current?.endedAt || latestDetails?.status === 'WORK_DONE' ? 'Bảo trì xong' : !current ? 'Chưa bắt đầu' : 'Đang bảo trì'


  async function perform(action: () => Promise<unknown>, success: string, reset?: () => void) {
    if (inFlight.current) return
    inFlight.current = true; setBusy(true); setError(null); setNotice(null)
    try { await action(); reset?.(); setNotice(success); reload() }
    catch (failure) { setError(failure) }
    finally { inFlight.current = false; setBusy(false) }
  }
  function confirmAction(message: string): boolean { return window.confirm(message) }

  function start() {
    if (!item || !plan || !confirmAction('Bắt đầu lần thực hiện bảo trì mới?')) return
    void perform(() => executionsApi.start(item.id, item.version, plan.version), 'Đã bắt đầu lần thực hiện mới.', () => { setTechnicalResult('PASS'); setTechnicalRepair(false); setHandoverResult('PASS'); setHandoverRepair(false) })
  }
  function appendProgress() {
    if (!current || !item || !canWork) return
    if(progressStatus === 'DAMAGE_DETECTED' && !progressNote.trim()) {setError(new UserInputError('Vui lòng mô tả hỏng hóc.'));return}
    void perform(() => executionsApi.updateProgress(current.executionId, progressStatus, progressNote.trim() || null, item.version),
      'Đã lưu cập nhật tiến độ.', () => setProgressNote(''))
  }
  function finish() {
    if (!item || !current || !confirmAction('Xác nhận hoàn thành kỹ thuật và chuyển sang chờ nghiệm thu kỹ thuật?')) return
    void perform(() => executionsApi.finish(current.executionId, item.version, resultNote.trim() || null),
      'Đã hoàn thành kỹ thuật; chờ nghiệm thu kỹ thuật.', () => setResultNote(''))
  }
  function repair() {
    if (!item || !current || !repairReason.trim()) { setError(new UserInputError('Vui lòng nhập lý do chuyển sửa chữa.')); return }
    if (!confirmAction('Chuyển hạng mục sang sửa chữa? Đây là điểm bàn giao cuối trong V1.')) return
    void perform(() => executionsApi.repair(current.executionId, item.version, repairReason.trim()),
      'Đã chuyển sang quy trình sửa chữa (ngoài phạm vi V1).', () => setRepairReason(''))
  }
  function technical() {
    if (!item || !current || !technicalConclusion.trim()) { setError(new UserInputError('Vui lòng nhập kết luận nghiệm thu kỹ thuật.')); return }
    if (!confirmAction('Ghi kết quả nghiệm thu kỹ thuật cho lần thực hiện hiện tại?')) return
    void perform(() => acceptancesApi.technical(current.executionId, {
      version: item.version, result: technicalResult, conclusion: technicalConclusion.trim(),
      repairRequired: technicalResult === 'FAIL' && technicalRepair,
    }), 'Đã ghi kết quả nghiệm thu kỹ thuật.', () => { setTechnicalConclusion(''); setTechnicalRepair(false) })
  }
  function handover() {
    if (!item || !current || !handoverConclusion.trim()) { setError(new UserInputError('Vui lòng nhập kết luận bàn giao.')); return }
    if (handoverResult === 'PASS' && (!signerUsername.trim() || !signerPassword)) {
      setError(new UserInputError('Bàn giao đạt cần tài khoản và mật khẩu xác nhận của Phòng VTYT.')); setSignerPassword(''); return
    }
    if (!confirmAction('Ghi kết quả bàn giao cho lần thực hiện hiện tại?')) return
    void perform(async () => {
      try {
        const command = { version: item.version, result: handoverResult,
          conclusion: handoverConclusion.trim(), repairRequired: handoverResult === 'FAIL' && handoverRepair }
        if (handoverResult === 'PASS')
          return await acceptancesApi.signedHandover(current.executionId, command, signerUsername.trim(), signerPassword)
        return await acceptancesApi.handover(current.executionId, command)
      } finally {
        setSignerPassword(''); setSignerUsername('')
      }
    }, 'Đã ghi kết quả bàn giao.', () => { setHandoverConclusion(''); setHandoverRepair(false) })
  }

  if (loading && !item) return <p className="muted">Đang tải hạng mục và các lần thực hiện…</p>
  if (!item || !plan) return <div className="page-stack"><WorkflowError error={error} onReload={reload} /><Link to={isVtyt ? "/maintenance-progress" : "/execution"}>Về danh sách</Link></div>
  return <div className="page-stack execution-page">
    <div className="page-title-row"><div className="page-title-block"><p className="eyebrow">UC08–UC10 · HẠNG MỤC #{item.id}</p><h1>{item.equipmentCode} · {item.equipmentName}</h1>
      <p>{plan.title} · Khoa tại kế hoạch: {item.departmentNameAtPlan}</p></div>
      <Link className="button secondary" to={isVtyt ? `/maintenance-progress/plans/${plan.id}` : `/execution/plans/${plan.id}`}>Về hạng mục</Link></div>
    <WorkflowSuccess message={notice} /><WorkflowError error={error} onReload={reload} />
    <section className="panel business-panel summary-panel"><div><span className="card-label">TRẠNG THÁI HẠNG MỤC</span><StatusBadge label={itemStatusLabels[item.status]} tone={item.status === 'COMPLETED' ? 'teal' : item.status === 'REPAIR_REQUIRED' ? 'amber' : 'neutral'} /></div>
      <div><span className="card-label">KẾ HOẠCH</span><strong>{planStatusLabels[plan.status]} · v{plan.version}</strong></div>
      <div><span className="card-label">PHIÊN BẢN HẠNG MỤC</span><strong>v{item.version}</strong></div>
      <div><span className="card-label">TUYẾN / ĐƠN VỊ</span><strong>{item.serviceChoice ? serviceChoiceLabels[item.serviceChoice] : item.assignmentRoute ? assignmentRouteLabels[item.assignmentRoute] : '—'} · {item.assignedProviderName || '—'}</strong></div>
      <button className="button secondary" type="button" onClick={reload}>Tải lại</button></section>
    <section className="panel business-panel"><h2>Theo dõi bảo trì</h2>
      <p>Trạng thái thực hiện: <strong>{item.status === 'REPAIR_REQUIRED' ? 'Chuyển sửa chữa' : executionLabel}</strong></p>
      <p>Cập nhật mới nhất: {current?.endedAt ? 'Hoàn thành kỹ thuật' : latestDetails?.label ?? (current ? 'Bắt đầu bảo trì' : 'Chưa có cập nhật')}
        {current && ` · ${new Date(current.endedAt ?? latestProgress?.eventAt ?? current.startedAt).toLocaleString('vi-VN')}`}</p>
      {item.status === 'AWAITING_TECHNICAL_ACCEPTANCE' && <p className="retention-note">Chờ nghiệm thu kỹ thuật</p>}
      {current && <ExecutionTimeline attempt={current} history={campaign?.itemHistory ?? []} />}
    </section>
    {plan.status === 'AWAITING_REPORT' && <div className="retention-note">Kế hoạch đã sẵn sàng cho bước báo cáo. {isVtyt && <Link className="text-link" to={`/plans/${plan.id}/report`}>Mở báo cáo</Link>}</div>}
    {item.status === 'REPAIR_REQUIRED' && <div className="retention-note">Đã chuyển sang quy trình sửa chữa (ngoài phạm vi V1). Dữ liệu thực hiện và lý do vẫn được giữ lại.</div>}
    {item.status === 'COMPLETED' && <div className="retention-note">Hạng mục đã được xác nhận hoàn thành bảo trì. Lịch sử thực hiện và các biên bản nghiệm thu/bàn giao đã có vẫn hiển thị bên dưới.</div>}
    {isVtyt && !['APPROVED', 'IN_PROGRESS'].includes(plan.status) && ['UNDER_CONTRACT', 'ASSIGNED_EXTERNAL'].includes(item.status) && <p className="retention-note">Hình thức bảo trì đã được xác định. Chỉ có thể bắt đầu thực hiện sau khi kế hoạch được phê duyệt.</p>}
    {isVtyt && plan.pendingVendorApproval && <p className="retention-note">Cần phê duyệt xong các đơn vị đề xuất trước khi bắt đầu thực hiện kế hoạch.</p>}
    {canStart && <section className="panel business-panel"><div className="panel-heading"><div><h2>{item.status === 'REWORK_REQUIRED' ? 'Bắt đầu lần thực hiện lại' : 'Bắt đầu bảo trì'}</h2><p>Bắt đầu khi đơn vị bảo trì xác nhận triển khai công việc.</p></div></div>
      <button className="button primary" type="button" disabled={busy} onClick={start}>{busy ? 'Đang xử lý…' : 'Bắt đầu bảo trì'}</button></section>}
    {canWork && <section className="panel business-panel execution-command-grid"><div><h2>Cập nhật tiến độ</h2><p className="muted">Ghi nhận thông tin nhận được từ đơn vị bảo trì.</p>
      <label className="block-label">Tiến độ hiện tại<select value={progressStatus} disabled={busy} onChange={event => setProgressStatus(event.target.value as MaintenanceProgressStatus)}>
        {Object.entries(progressLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <label className="block-label">Ghi chú<textarea value={progressNote} maxLength={4000} disabled={busy} rows={3} onChange={event => setProgressNote(event.target.value)} /></label>
      <button className="button secondary" type="button" disabled={busy} onClick={appendProgress}>{busy ? 'Đang lưu…' : 'Lưu tiến độ'}</button></div>
      <div><h2>Hoàn thành kỹ thuật</h2><p className="muted">Sau khi kết thúc, hạng mục chuyển sang chờ nghiệm thu kỹ thuật.</p>
        <label className="block-label">Ghi chú kết quả (nếu có)<textarea value={resultNote} rows={3} onChange={event => setResultNote(event.target.value)} /></label>
        <button className="button primary" type="button" disabled={busy} onClick={finish}>{busy ? 'Đang xử lý…' : 'Hoàn thành kỹ thuật'}</button>
        <div className="repair-action"><h3>Chuyển sửa chữa</h3><label className="block-label">Lý do bắt buộc<textarea value={repairReason} rows={2} onChange={event => setRepairReason(event.target.value)} /></label>
          <button className="button secondary" type="button" disabled={busy} onClick={repair}>Chuyển sang sửa chữa</button></div></div></section>}
    {canTechnical && <section className="panel business-panel"><div className="panel-heading"><h2>Nghiệm thu kỹ thuật · lần {current.attemptNo}</h2></div>
      <div className="decision-options"><label><input type="radio" name="technical-result" checked={technicalResult === 'PASS'} onChange={() => { setTechnicalResult('PASS'); setTechnicalRepair(false) }} /> Đạt</label>
        <label><input type="radio" name="technical-result" checked={technicalResult === 'FAIL'} onChange={() => setTechnicalResult('FAIL')} /> Không đạt</label></div>
      {technicalResult === 'FAIL' && <label className="check-line"><input type="checkbox" checked={technicalRepair} onChange={event => setTechnicalRepair(event.target.checked)} /> Chuyển sửa chữa thay vì thực hiện lại</label>}
      <label className="block-label">Kết luận bắt buộc<textarea rows={3} value={technicalConclusion} onChange={event => setTechnicalConclusion(event.target.value)} /></label>
      <button className="button primary" type="button" disabled={busy} onClick={technical}>{busy ? 'Đang ghi…' : 'Ghi nghiệm thu kỹ thuật'}</button></section>}
    {canHandover && <section className="panel business-panel"><div className="panel-heading"><div><h2>Bàn giao · lần {current.attemptNo}</h2><p>Chỉ áp dụng cho lần thực hiện hiện tại đã nghiệm thu kỹ thuật đạt.</p></div></div>
      <div className="decision-options"><label><input type="radio" name="handover-result" checked={handoverResult === 'PASS'} onChange={() => { setHandoverResult('PASS'); setHandoverRepair(false) }} /> Đạt</label>
        <label><input type="radio" name="handover-result" checked={handoverResult === 'FAIL'} onChange={() => { setHandoverResult('FAIL'); setSignerPassword(''); setSignerUsername('') }} /> Không đạt</label></div>
      {handoverResult === 'FAIL' && <label className="check-line"><input type="checkbox" checked={handoverRepair} onChange={event => setHandoverRepair(event.target.checked)} /> Chuyển sửa chữa thay vì thực hiện lại</label>}
      <label className="block-label">Kết luận bắt buộc<textarea rows={3} value={handoverConclusion} onChange={event => setHandoverConclusion(event.target.value)} /></label>
      {handoverResult === 'PASS' && <div className="cosigner-panel"><h3>Xác nhận thứ hai · Phòng VTYT</h3><p className="muted">Đăng nhập một lần để ký bàn giao. Phiên Khoa/Phòng hiện tại vẫn được giữ nguyên.</p>
        <div className="form-grid"><label>Tên đăng nhập VTYT<input autoComplete="off" value={signerUsername} onChange={event => setSignerUsername(event.target.value)} /></label>
          <PasswordInput label="Mật khẩu VTYT" autoComplete="off" value={signerPassword} onChange={event => setSignerPassword(event.target.value)} /></div></div>}
      <button className="button primary" type="button" disabled={busy} onClick={handover}>{busy ? 'Đang xác nhận…' : 'Ghi kết quả bàn giao'}</button></section>}
    <section className="panel business-panel"><div className="panel-heading"><div><h2>Lịch sử các lần thực hiện</h2><p>{attempts.length} lần thực hiện được lưu trên backend; các lần cũ chỉ đọc.</p></div></div>
      {loading ? <p className="muted">Đang tải lại bằng chứng…</p> : <AttemptHistory attempts={attempts} currentExecutionId={current?.executionId} />}
    </section>
  </div>
}

