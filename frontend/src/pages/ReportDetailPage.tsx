import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { reportsApi } from '../api/reportsApi'
import { plansApi } from '../api/plansApi'
import { ApiError } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { StatusBadge } from '../components/StatusBadge'
import { WorkflowError, WorkflowSuccess } from '../components/WorkflowFeedback'
import type { ReportNarrative, ReportResponse } from '../types/report'
import type { Plan, PlanItem } from '../types/workflow'
import { UserInputError } from '../utils/UserInputError'
import { businessDate, dateTime, planStatusLabels } from '../utils/workflowLabels'

const fields: { key: keyof ReportNarrative; label: string; rows: number }[] = [
  { key: 'reportNumber', label: 'Số báo cáo (nếu có)', rows: 1 },
  { key: 'workDone', label: 'Công việc đã thực hiện', rows: 3 },
  { key: 'achieved', label: 'Kết quả đạt được', rows: 3 },
  { key: 'notAchieved', label: 'Kết quả chưa đạt', rows: 2 },
  { key: 'causes', label: 'Nguyên nhân', rows: 2 },
  { key: 'nextWork', label: 'Công việc tiếp theo', rows: 2 },
  { key: 'resolutions', label: 'Biện pháp xử lý', rows: 2 },
  { key: 'recommendations', label: 'Kiến nghị', rows: 2 },
]
const blank: Record<keyof ReportNarrative, string> = {
  reportNumber: '', workDone: '', achieved: '', notAchieved: '', causes: '', nextWork: '', resolutions: '', recommendations: '',
}
function toForm(report: ReportResponse | null): typeof blank {
  return Object.fromEntries(fields.map(field => [field.key, report?.[field.key] ?? ''])) as typeof blank
}
function ReportStatusBadge({ report }: { report: ReportResponse | null }) {
  return <StatusBadge label={report?.status === 'FINAL' ? 'Báo cáo chính thức' : report ? 'Bản nháp' : 'Chưa có báo cáo'} tone={report?.status === 'FINAL' ? 'teal' : 'neutral'} />
}
function OutcomeSummary({ completed, repair, provisional }: { completed: number; repair: number; provisional: boolean }) {
  return <section className="panel business-panel"><div className="panel-heading"><div><h2>Kết quả hạng mục</h2>
    <p>{provisional ? 'Tạm xem từ trạng thái hạng mục; số chính thức do backend ghi khi lập báo cáo.' : 'Số liệu do backend trả về cùng báo cáo.'}</p></div></div>
    <div className="report-counts"><div><span>Hoàn tất bảo trì</span><strong>{completed}</strong></div><div><span>Chuyển sửa chữa</span><strong>{repair}</strong></div></div>
  </section>
}
function ReportForm({ value, onChange }: { value: typeof blank; onChange: (field: keyof ReportNarrative, text: string) => void }) {
  return <div className="report-fields">{fields.map(field => <label className="block-label" key={field.key}>{field.label}
    {field.rows === 1 ? <input value={value[field.key]} onChange={event => onChange(field.key, event.target.value)} /> :
      <textarea rows={field.rows} value={value[field.key]} onChange={event => onChange(field.key, event.target.value)} />}
  </label>)}</div>
}
function ReportReadView({ report }: { report: ReportResponse }) {
  return <dl className="report-read-view">{fields.map(field => <div key={field.key}><dt>{field.label}</dt><dd>{report[field.key] || '—'}</dd></div>)}</dl>
}

export function ReportDetailPage() {
  const id = Number(useParams().planId)
  const { user } = useAuth()
  const [plan, setPlan] = useState<Plan | null>(null)
  const [report, setReport] = useState<ReportResponse | null>(null)
  const [items, setItems] = useState<PlanItem[]>([])
  const [form, setForm] = useState(blank)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [refresh, setRefresh] = useState(0)
  const inFlight = useRef(false)
  const reload = useCallback(() => { setLoading(true); setRefresh(value => value + 1) }, [])
  useEffect(() => {
    if (!Number.isInteger(id) || id <= 0) { setError(new UserInputError('Đường dẫn kế hoạch không hợp lệ.')); setLoading(false); return }
    let active = true
    Promise.all([plansApi.detail(id), reportsApi.get(id).catch(failure => {
      if (failure instanceof ApiError && failure.status === 404) return null
      throw failure
    }), plansApi.items(id, 0, 100)]).then(async ([loadedPlan, loadedReport, first]) => {
      const all = [...first.content]
      for (let page = 1; page < first.totalPages; page++) all.push(...(await plansApi.items(id, page, 100)).content)
      if (active) { setPlan(loadedPlan); setReport(loadedReport); setItems(all); setForm(toForm(loadedReport)); setError(null); setLoading(false) }
    }).catch(failure => { if (active) { setError(failure); setLoading(false) } })
    return () => { active = false }
  }, [id, refresh])
  const editable = user?.role === 'PHONG_VTYT' && plan?.status === 'AWAITING_REPORT' && report?.status !== 'FINAL'
  async function mutate(action: () => Promise<unknown>, message: string) {
    if (inFlight.current) return
    inFlight.current = true; setBusy(true); setError(null); setNotice(null)
    try { await action(); setNotice(message); reload() }
    catch (failure) { setError(failure) }
    finally { inFlight.current = false; setBusy(false) }
  }
  function save() {
    if (!plan || !editable) return
    const body = { version: plan.version, ...Object.fromEntries(fields.map(field => [field.key, form[field.key].trim() || null])) } as ReportNarrative & { version: number }
    void mutate(() => report ? reportsApi.edit(id, body) : reportsApi.create(id, body), report ? 'Đã lưu bản nháp báo cáo.' : 'Đã tạo bản nháp báo cáo.')
  }
  function finalize() {
    if (!plan || !report || !editable) return
    if (fields.some(field => form[field.key] !== (report[field.key] ?? ''))) { setError(new UserInputError('Vui lòng lưu thay đổi bản nháp trước khi hoàn tất.')); return }
    if (!report.workDone?.trim()) { setError(new UserInputError('Cần có nội dung công việc đã thực hiện trước khi hoàn tất báo cáo.')); return }
    if (!window.confirm('Hoàn tất báo cáo? Báo cáo chính thức sẽ chỉ đọc.')) return
    void mutate(() => reportsApi.finalize(id, plan.version), 'Báo cáo đã hoàn tất; kế hoạch chuyển sang Đã báo cáo.')
  }
  if (loading && !plan) return <p className="muted">Đang tải báo cáo…</p>
  if (!plan) return <div className="page-stack"><WorkflowError error={error} onReload={reload} /><Link to="/reports">Về danh sách báo cáo</Link></div>
  const completed = report?.completedCount ?? items.filter(item => item.status === 'COMPLETED').length
  const repair = report?.repairRequiredCount ?? items.filter(item => item.status === 'REPAIR_REQUIRED').length
  return <div className="page-stack report-page">
    <div className="page-title-row"><div className="page-title-block"><p className="eyebrow">UC11 · KẾ HOẠCH #{plan.id}</p><h1>Báo cáo bảo trì</h1><p>{plan.title} · {businessDate(plan.periodStart)} – {businessDate(plan.periodEnd)}</p></div>
      <Link className="button secondary" to="/reports">Về danh sách</Link></div>
    <WorkflowSuccess message={notice} /><WorkflowError error={error} onReload={reload} />
    <section className="panel business-panel summary-panel"><div><span className="card-label">KẾ HOẠCH</span><StatusBadge label={planStatusLabels[plan.status]} tone={plan.status === 'REPORTED' ? 'teal' : 'neutral'} /></div>
      <div><span className="card-label">BÁO CÁO</span><ReportStatusBadge report={report} /></div><div><span className="card-label">PHIÊN BẢN KẾ HOẠCH</span><strong>v{plan.version}</strong></div>
      <button className="button secondary" type="button" onClick={reload}>Tải lại</button></section>
    <OutcomeSummary completed={completed} repair={repair} provisional={!report} />
    <section className="panel business-panel"><div className="panel-heading"><div><h2>{report?.status === 'FINAL' ? 'Nội dung báo cáo chính thức' : report ? 'Bản nháp báo cáo' : 'Lập báo cáo'}</h2>
      <p>{report ? `Ngày báo cáo: ${businessDate(report.reportDate)} · Hoàn tất: ${dateTime(report.finalizedAt)}` : 'Chỉ Phòng VTYT có thể lập báo cáo khi kế hoạch đang chờ báo cáo.'}</p></div></div>
      {loading ? <p className="muted">Đang tải lại…</p> : editable ? <><ReportForm value={form} onChange={(field, text) => setForm(current => ({ ...current, [field]: text }))} />
        <div className="form-actions left-actions"><button className="button secondary" type="button" disabled={busy} onClick={save}>{busy ? 'Đang lưu…' : report ? 'Lưu bản nháp' : 'Tạo bản nháp'}</button>
          {report && <button className="button primary" type="button" disabled={busy} onClick={finalize}>Hoàn tất báo cáo</button>}</div></> : report ? <ReportReadView report={report} /> :
        <p className="empty-state">Chưa có báo cáo để xem hoặc kế hoạch chưa ở trạng thái cho phép lập báo cáo.</p>}
      {report?.status === 'FINAL' && <p className="retention-note">Báo cáo chính thức chỉ đọc. Kế hoạch đã báo cáo; không có thao tác đóng kế hoạch trong V1.</p>}
    </section>
  </div>
}
