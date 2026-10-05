import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { quarterlyApi, quarterLabels } from '../api/quarterlyApi'
import type { Quarter, QuarterPreview, Proposal, QuarterlyEquipment } from '../api/quarterlyApi'
import { plansApi } from '../api/plansApi'
import { providersApi } from '../api/providersApi'
import { ReviewComments } from '../components/ReviewComments'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { Plan, PlanItem, Provider } from '../types/workflow'
import { businessDate } from '../utils/workflowLabels'

export function PlanFormPage({ mode }: { mode: 'create' | 'edit' }) {
 const id = Number(useParams().planId); const navigate = useNavigate()
 const [search] = useSearchParams()
 const requestedYear = Number(search.get('year')); const requestedQuarter = search.get('quarter')
 const [year, setYear] = useState(Number.isInteger(requestedYear) && requestedYear >= 2000 && requestedYear <= 2100 ? requestedYear : new Date().getFullYear() + 1)
 const [quarter, setQuarter] = useState<Quarter>(requestedQuarter && ['Q1','Q2','Q3','Q4'].includes(requestedQuarter) ? requestedQuarter as Quarter : 'Q1')
 const [preview, setPreview] = useState<QuarterPreview | null>(null)
 const [plan, setPlan] = useState<Plan | null>(null)
 const [items, setItems] = useState<PlanItem[]>([])
 const [providers, setProviders] = useState<Provider[]>([])
 const [proposals, setProposals] = useState<Record<number, Proposal>>({})
 const [error, setError] = useState<unknown>(null)
 const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false)
 const [highlight, setHighlight] = useState(false)
 useEffect(() => { let active = true; providersApi.list().then(p => { if (active) setProviders(p.filter(v => v.active)) }).catch(e => { if (active) setError(e) }); return () => { active = false } }, [])
 useEffect(() => {
  if (mode !== 'edit') return
  let active = true
  Promise.all([plansApi.detail(id), plansApi.allItems(id)]).then(([p, rows]) => {
   if (!active) return
   setPlan(p); setItems(rows); setYear(p.planYear ?? Number(p.periodStart.slice(0, 4))); setQuarter(p.planQuarter ?? `Q${Math.floor((Number(p.periodStart.slice(5, 7)) - 1) / 3) + 1}` as Quarter)
   setProposals(Object.fromEntries(rows.map(r => [r.equipmentId, { equipmentId: r.equipmentId, proposedProviderId: r.proposedProviderId ?? null, rationale: r.rationale ?? '' }])))
   setLoading(false)
  }).catch(e => { if (active) { setError(e); setLoading(false) } })
  return () => { active = false }
 }, [mode, id])
 useEffect(() => {
  if (mode !== 'create') return
  let active = true
  quarterlyApi.preview(year, quarter).then(p => { if (active) { setPreview(p); setProposals({}); setError(null); setLoading(false) } }).catch(e => { if (active) { setPreview(null); setError(e); setLoading(false) } })
  return () => { active = false }
 }, [year, quarter, mode])
 const rows: QuarterlyEquipment[] = mode === 'create' ? preview?.equipment ?? [] : items.map(i => ({ equipment_id: i.equipmentId, equipment_code: i.equipmentCode, equipment_name: i.equipmentName, department_name: i.departmentNameAtPlan, quarters: plan?.planQuarter ?? quarter, contract_status: i.classification === 'FREE' ? 'VALID' : 'EXPIRED', classification: i.classification ?? 'NOT_FREE', provider_id: i.assignedProviderId ?? undefined, provider_name: i.assignedProviderName ?? undefined, contract_id: i.contractId ?? undefined, contract_code: i.contractCode ?? undefined }))
 function change(id: number, patch: Partial<Proposal>) { setProposals(p => ({ ...p, [id]: { ...(p[id] ?? { equipmentId: id, proposedProviderId: null, rationale: '' }), ...patch } })) }
 const incomplete = (r: QuarterlyEquipment) => r.classification !== 'FREE' && (!proposals[r.equipment_id]?.proposedProviderId || !proposals[r.equipment_id]?.rationale.trim())
 async function save(submit: boolean) {
  if (loading || busy || !rows.length) return
  if (submit && rows.some(incomplete)) { setHighlight(true); return }
  setBusy(true); setError(null)
  try {
   const result = mode === 'create' ? await quarterlyApi.create(year, quarter, Object.values(proposals)) : await plansApi.edit(id, { version: plan!.version, title: plan!.title, periodStart: plan!.periodStart, periodEnd: plan!.periodEnd, items: items.map(i => ({ equipmentId: i.equipmentId, plannedDate: plan!.planYear ? plan!.periodStart : i.plannedDate, version: i.version, proposedProviderId: proposals[i.equipmentId]?.proposedProviderId ?? null, rationale: proposals[i.equipmentId]?.rationale || null, warrantyImpactNote: i.warrantyImpactNote, serviceChoice: i.serviceChoice })) })
   if (submit) {
    // The saved draft remains reachable if submission fails (e.g. a contract changed).
    try { await plansApi.submit(result.id, result.version) } catch (e) { navigate(`/plans/${result.id}`, { state: { flash: 'Đã lưu bản nháp. Vui lòng kiểm tra trước khi gửi lại.' } }); throw e }
   }
   navigate(`/plans/${result.id}`)
  } catch (e) { setError(e) } finally { setBusy(false) }
 }
 const title = plan?.title ?? preview?.title ?? 'Tạo kế hoạch bảo trì'
 return <div className="page-stack"><h1>{title}</h1>{mode === 'edit' && <ReviewComments planId={id} />}<WorkflowError error={error} />
  <section className="panel business-panel"><div className="form-actions"><label>Năm<select aria-label="Năm" disabled={mode === 'edit'} value={year} onChange={e => { if(Number(e.target.value) !== year) { setYear(Number(e.target.value)); setLoading(true); setPreview(null) } }}>{Array.from({ length: 101 }, (_, i) => 2000 + i).map(y => <option key={y} value={y}>{y}</option>)}</select></label><label>Quý<select aria-label="Quý" disabled={mode === 'edit'} value={quarter} onChange={e => { if(e.target.value !== quarter) { setQuarter(e.target.value as Quarter); setLoading(true); setPreview(null) } }}>{Object.entries(quarterLabels).map(([q, label]) => <option key={q} value={q}>{label}</option>)}</select></label></div>
   <p>{businessDate(plan?.periodStart ?? preview?.periodStart)} – {businessDate(plan?.periodEnd ?? preview?.periodEnd)}</p>
   <h2>Danh sách thiết bị bảo trì {quarterLabels[quarter]} năm {year}</h2>
   {loading ? <p>Đang tải danh sách thiết bị…</p> : <><p>{rows.length} thiết bị</p><div className="table-scroll"><table className="data-table"><thead><tr>{['STT', 'Mã thiết bị', 'Tên thiết bị', 'Khoa/Phòng', 'Lịch bảo trì', 'Trạng thái hợp đồng', 'Hình thức bảo trì', 'Công ty / đơn vị', 'Hợp đồng', 'Đơn vị đề xuất / căn cứ'].map(h => <th key={h}>{h}</th>)}</tr></thead><tbody>{rows.map((r, index) => <tr key={r.equipment_id} className={highlight && incomplete(r) ? 'incomplete-quarter-row' : ''}><td>{index + 1}</td><td><Link to={`/equipment/${r.equipment_id}`}>{r.equipment_code}</Link></td><td>{r.equipment_name}</td><td>{r.department_name}</td><td>{r.quarters}</td><td>{r.contract_status === 'VALID' ? 'Còn hạn' : 'Hết hạn'}</td><td>{r.classification === 'FREE' ? 'Theo hợp đồng' : 'Ngoài hợp đồng'}</td><td>{r.provider_id ? <Link to={`/providers/${r.provider_id}`}>{r.provider_name}</Link> : '—'}</td><td>{r.contract_id ? <Link to={`/contracts/${r.contract_id}`}>{r.contract_code}</Link> : '—'}{r.conflict && <span role="alert">Nhiều hợp đồng hợp lệ; cần kiểm tra.</span>}</td><td>{r.classification !== 'FREE' && <><select aria-label={`Đơn vị đề xuất ${r.equipment_code}`} value={proposals[r.equipment_id]?.proposedProviderId ?? ''} onChange={e => change(r.equipment_id, { proposedProviderId: Number(e.target.value) || null })}><option value="">Chọn đơn vị</option>{providers.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select><textarea rows={2} aria-label={`Căn cứ ${r.equipment_code}`} placeholder="Căn cứ chọn đơn vị" value={proposals[r.equipment_id]?.rationale ?? ''} onChange={e => change(r.equipment_id, { rationale: e.target.value })} />{highlight && incomplete(r) && <span className="warning-text">Cần chọn đơn vị và nhập căn cứ.</span>}</>}</td></tr>)}</tbody></table></div></>}
   {highlight && rows.some(incomplete) && <p role="alert">Bổ sung đơn vị đề xuất và căn cứ cho các thiết bị ngoài hợp đồng.</p>}
   <div className="form-actions"><Link className="button secondary" to="/plans">Về kế hoạch</Link><button className="button secondary" disabled={loading || busy || !rows.length || rows.some(r => r.conflict)} onClick={() => void save(false)}>{mode === 'create' ? 'Tạo kế hoạch' : 'Lưu chỉnh sửa'}</button><button className="button primary" disabled={loading || busy || !rows.length || rows.some(r => r.conflict)} onClick={() => void save(true)}>Gửi phê duyệt</button></div>
  </section></div>
}
