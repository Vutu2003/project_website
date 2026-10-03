import { Fragment, useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/types'
import { apiRequest } from '../api/client'
import { equipmentApi } from '../api/equipmentApi'
import { providersApi } from '../api/providersApi'
import { plansApi } from '../api/plansApi'
import { Pagination } from '../components/Pagination'
import { WarrantySummary } from '../components/WarrantySummary'
import { WarrantyModal } from '../components/WarrantyModal'
import { StatusBadge } from '../components/StatusBadge'
import { businessToday, warrantyAt, warrantyLabels, serviceChoiceLabels } from '../utils/warranty'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { CoverageEvidence, Equipment, MaintenanceSuggestion, PageResponse, Plan, PlanItemInput, Provider, WarrantyInfo } from '../types/workflow'
import { businessDate } from '../utils/workflowLabels'
import { itemCompleteness } from '../utils/itemCompleteness'
import { freeCoverageReason } from '../utils/planningDecision'

interface DraftItem extends PlanItemInput {
 equipmentCode: string; equipmentName: string; departmentName: string; proposedProviderName?: string | null; coverages?: CoverageEvidence[]; warranty?: WarrantyInfo | null
}
async function loadAllItems(planId: number): Promise<DraftItem[]> {
 const first = await plansApi.items(planId, 0, 100); const content = [...first.content]
 for (let p = 1; p < first.totalPages; p++) content.push(...(await plansApi.items(planId, p, 100)).content)
 return content.map(i => ({ equipmentId: i.equipmentId, equipmentCode: i.equipmentCode, equipmentName: i.equipmentName,
  departmentName: i.departmentNameAtPlan, plannedDate: i.plannedDate, classification: i.classification,
  coverageId: i.coverageId, proposedProviderId: i.proposedProviderId, proposedProviderName: i.proposedProviderName, rationale: i.rationale,
  warrantyImpactNote: i.warrantyImpactNote, version: i.version, serviceChoice: i.classification === 'NOT_FREE' ? i.serviceChoice || 'EXTERNAL' : null }))
}
function ItemDecision({ item, periodStart, providers, locked, onChange }: {
 item: DraftItem; periodStart: string; providers: Provider[]; locked: boolean; onChange: (patch: Partial<DraftItem>) => void
}) {
 const [error, setError] = useState<unknown>(null)
 const [reload, setReload] = useState(0)
 useEffect(() => {
  let active = true
  Promise.all([equipmentApi.coverages(item.equipmentId), equipmentApi.warranty(item.equipmentId)]).then(([rows, warranty]) => { if (active) { onChange({ coverages: rows, warranty }); setError(null) } }).catch(e => { if (active) { onChange({ warranty: null }); setError(e) } })
  return () => { active = false }
  // Parent callback changes as draft values change; evidence reload only follows identity.
  // eslint-disable-next-line react-hooks/exhaustive-deps
 }, [item.equipmentId, reload])
 const manufacturer = item.warranty?.manufacturerProviderId
 const referenceDate = item.plannedDate || periodStart
 const missing = item.classification === 'NOT_FREE' && item.proposedProviderId && !providers.some(p => p.id === item.proposedProviderId && p.active) ? 'Đơn vị đề xuất đã ngừng hoạt động' : itemCompleteness(item, periodStart)
 return <article className="planning-item" aria-label={`Hạng mục ${item.equipmentCode}`}><div><h3>{item.equipmentCode} · {item.equipmentName}</h3><p className="muted">{item.departmentName}</p></div>
 <fieldset disabled={locked}><div className="form-grid"><label>Ngày dự kiến {item.equipmentCode}<input type="date" value={item.plannedDate || ''} onChange={e => onChange({ plannedDate: e.target.value || null })} /></label></div>
 <p className="muted">Ngày xét hợp đồng: {businessDate(item.plannedDate || periodStart)} (dùng ngày bắt đầu kế hoạch nếu chưa nhập ngày dự kiến).</p>
 <legend>Hình thức bảo trì</legend><div className="decision-options"><label><input type="radio" name={`method-${item.equipmentId}`} checked={item.classification === 'FREE'} onChange={() => onChange({ classification: 'FREE', proposedProviderId: null, rationale: null, warrantyImpactNote: null, serviceChoice: null })} />Theo hợp đồng</label><label><input type="radio" name={`method-${item.equipmentId}`} checked={item.classification === 'NOT_FREE'} onChange={() => onChange({ classification: 'NOT_FREE', coverageId: null, serviceChoice: item.serviceChoice || 'EXTERNAL' })} />Ngoài hợp đồng</label></div>
 {item.classification === 'FREE' && <div className="coverage-list">{!item.coverages ? <p>Đang tải hợp đồng…</p> : item.coverages.filter(c => c.classification === 'FREE').length === 0 ? <p className="warning-text">Không có hợp đồng phù hợp. Có thể chọn Ngoài hợp đồng và chuẩn bị đề xuất.</p> : item.coverages.filter(c => c.classification === 'FREE').map(c => {
  const reason = freeCoverageReason(c, item.equipmentId, item.plannedDate || periodStart)
  return <label className={`coverage-option ${reason ? 'blocked' : ''}`} key={c.id}><input type="radio" name={`coverage-${item.equipmentId}`} checked={item.coverageId === c.id} disabled={!!reason} onChange={() => onChange({ coverageId: c.id })} /><span><strong>{c.contractReference || `Hợp đồng #${c.id}`} · {c.providerName}</strong><small>Hiệu lực: {businessDate(c.effectiveFrom)} – {businessDate(c.effectiveTo)}</small><small>Ngày hết bảo hành: {businessDate(c.warrantyExpiresOn)} · {warrantyLabels[warrantyAt(c.warrantyExpiresOn, c.effectiveFrom, referenceDate)]}</small><small>Phạm vi: {c.coverageScope || '—'}</small><small>Căn cứ: {c.basisNote || '—'}</small><small>Xác minh: {c.verifiedByName || '—'}</small>{reason && <small className="warning-text">{reason}</small>}</span></label>
 })}</div>}
 {item.classification === 'NOT_FREE' && <><div className="decision-options"><label><input type="radio" name={`service-${item.equipmentId}`} checked={item.serviceChoice === 'MANUFACTURER'} disabled={!manufacturer || !item.warranty?.manufacturerActive} onChange={() => onChange({ serviceChoice: 'MANUFACTURER', proposedProviderId: manufacturer })} />Liên hệ nhà sản xuất</label><label><input type="radio" name={`service-${item.equipmentId}`} checked={item.serviceChoice !== 'MANUFACTURER'} onChange={() => onChange({ serviceChoice: 'EXTERNAL', proposedProviderId: null })} />Bảo hành ngoài</label></div>{!manufacturer && <p className="muted">Chưa có nhà sản xuất / đại diện. Mở “Thông tin bảo hành” để cập nhật hồ sơ trước khi chọn liên hệ nhà sản xuất.</p>}{item.serviceChoice === 'MANUFACTURER' && <p className="retention-note">{item.warranty?.manufacturerName} · {item.warranty?.manufacturerContact || 'Chưa có thông tin liên hệ'}. Lựa chọn này chuẩn bị đề xuất cho BGĐ; VTYT chủ động liên hệ đơn vị.</p>}<div className="form-grid"><label>Đơn vị đề xuất {item.equipmentCode}<select disabled={item.serviceChoice === 'MANUFACTURER'} value={item.proposedProviderId || ''} onChange={e => onChange({ proposedProviderId: e.target.value ? Number(e.target.value) : null })}><option value="">Chọn đơn vị đang hoạt động</option>{item.proposedProviderId && !providers.some(p => p.id === item.proposedProviderId && p.active) && <option value={item.proposedProviderId} disabled>{item.proposedProviderName || `Đơn vị #${item.proposedProviderId}`} (ngừng hoạt động)</option>}{providers.filter(p => p.active).map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label><label className="wide-field">Căn cứ chọn đơn vị {item.equipmentCode}<textarea rows={3} maxLength={4000} value={item.rationale || ''} onChange={e => onChange({ rationale: e.target.value })} /></label><label className="wide-field">Ghi chú / ảnh hưởng bảo hành {item.equipmentCode}<textarea rows={2} maxLength={4000} value={item.warrantyImpactNote || ''} onChange={e => onChange({ warrantyImpactNote: e.target.value })} /></label><p className="muted wide-field">Đơn vị này mới được đề xuất. BGĐ quyết định đơn vị sau khi duyệt kế hoạch; hệ thống tự gửi đề xuất.</p></div></>}
 </fieldset><p className={`completeness ${missing ? 'incomplete' : ''}`}>{missing ? `⚠ ${missing}` : '✓ Đã đủ thông tin'}</p><WorkflowError error={error} onReload={() => setReload(v => v + 1)} /></article>
}
export function PlanFormPage({ mode }: { mode: 'create' | 'edit' }) {
 const { planId } = useParams(); const id = Number(planId); const navigate = useNavigate(); const location = useLocation()
 const [plan, setPlan] = useState<Plan | null>(null); const [title, setTitle] = useState(''); const [periodStart, setPeriodStart] = useState(''); const [periodEnd, setPeriodEnd] = useState('')
 const [items, setItems] = useState<DraftItem[]>([]); const [providers, setProviders] = useState<Provider[]>([])
 const [equipmentPage, setEquipmentPage] = useState(0); const [equipment, setEquipment] = useState<PageResponse<Equipment> | null>(null)
 const [equipmentWarranties, setEquipmentWarranties] = useState<Record<number, WarrantyInfo | null>>({})
 const [expanded, setExpanded] = useState<number | null>(null); const [warrantyItem, setWarrantyItem] = useState<{ equipmentId: number; plannedDate?: string | null } | null>(null); const [evidenceReload, setEvidenceReload] = useState(0)
 const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState<unknown>(null); const [validation, setValidation] = useState<string | null>(null); const [reloadKey, setReloadKey] = useState(0)
 const reload = useCallback(() => { setLoading(true); setReloadKey(v => v + 1) }, [])
 useEffect(() => {
  let active = true
  async function load() {
   const ps = await providersApi.list()
   if (!active) return
   setProviders(ps)
   if (mode === 'edit') {
    if (!Number.isInteger(id) || id <= 0) throw new ApiError(404, 'PLAN_NOT_FOUND', 'Không tìm thấy kế hoạch.')
    const [p, rows] = await Promise.all([plansApi.detail(id), loadAllItems(id)])
    if (!active) return
    setPlan(p); setTitle(p.title); setPeriodStart(p.periodStart); setPeriodEnd(p.periodEnd); setItems(rows)
   } else {
    const state = location.state as { suggestions?: MaintenanceSuggestion[] } | null
    const suggestions = Array.isArray(state?.suggestions) ? state.suggestions.filter(s => Number.isSafeInteger(s.equipmentId) && s.equipmentId > 0).slice(0, 100) : []
    const unique = [...new Map(suggestions.map(s => [s.equipmentId, s])).values()]
    if (unique.length) {
     const rows = await Promise.all(unique.map(async s => {
      const device = await apiRequest<Equipment>(`/api/equipment/${s.equipmentId}`)
      if (!device.active) throw new ApiError(409, 'EQUIPMENT_INACTIVE', `Thiết bị ${device.equipmentCode} đã ngừng hoạt động.`)
      return { equipmentId: device.id, equipmentCode: device.equipmentCode, equipmentName: device.name, departmentName: device.departmentName || '', plannedDate: s.suggestedDate,
       classification: s.classification === 'FREE' ? 'FREE' as const : 'NOT_FREE' as const, coverageId: s.classification === 'FREE' ? s.coverageId : null, proposedProviderId: s.serviceChoice === 'MANUFACTURER' ? s.manufacturerProviderId : null, serviceChoice: s.classification === 'FREE' ? null : s.serviceChoice || 'EXTERNAL' as const }
     }))
     if (!active) return
     setItems(rows); const dates = unique.map(s => s.suggestedDate || s.referenceDate).sort(); setPeriodStart(dates[0]); setPeriodEnd(dates[dates.length - 1])
    }
   }
   if (active) { setLoading(false); setError(null) }
  }
  load().catch(e => { if (active) { setError(e); setLoading(false) } }); return () => { active = false }
 }, [mode, id, reloadKey, location.state])
 useEffect(() => {
  let active = true
  equipmentApi.list(equipmentPage, 10).then(async result => {
   if (!active) return
   setEquipment(result); setEquipmentWarranties({})
   const summaries = await Promise.allSettled(result.content.map(row => equipmentApi.warranty(row.id)))
   if (active) setEquipmentWarranties(Object.fromEntries(result.content.map((row, index) => {
    const response = summaries[index]
    return [row.id, response.status === 'fulfilled' ? response.value : null]
   })))
  }).catch(e => { if (active) setError(e) })
  return () => { active = false }
 }, [equipmentPage, evidenceReload])
 const locked = mode === 'edit' && !!plan && !['DRAFT', 'REVISION_REQUIRED'].includes(plan.status)
 function patch(equipmentId: number, change: Partial<DraftItem>) { setItems(current => current.map(i => i.equipmentId === equipmentId ? { ...i, ...change } : i)) }
 function add(row: Equipment) { setItems(current => current.some(i => i.equipmentId === row.id) ? current : [...current, { equipmentId: row.id, equipmentCode: row.equipmentCode, equipmentName: row.name, departmentName: row.departmentName || '', plannedDate: null }]) }
 async function save(event: FormEvent<HTMLFormElement>) {
  event.preventDefault(); setValidation(null); setError(null)
  if (locked || busy) return
  if (!title.trim() || !periodStart || !periodEnd || periodEnd < periodStart) { setValidation('Nhập tiêu đề và khoảng thời gian hợp lệ.'); return }
  if (!items.length) { setValidation('Kế hoạch cần ít nhất một thiết bị.'); return }
  if (items.some(i => i.plannedDate && (i.plannedDate < periodStart || i.plannedDate > periodEnd))) { setValidation('Ngày dự kiến phải nằm trong kỳ kế hoạch.'); return }
  const invalidFree = items.find(i => i.classification === 'FREE' && itemCompleteness(i, periodStart))
  if (invalidFree) { setValidation(`Thiết bị ${invalidFree.equipmentCode}: ${itemCompleteness(invalidFree, periodStart)}`); return }
  const body = { title: title.trim(), periodStart, periodEnd, items: items.map(i => ({ equipmentId: i.equipmentId, plannedDate: i.plannedDate || null, classification: i.classification || null, coverageId: i.classification === 'FREE' ? i.coverageId : null, proposedProviderId: i.classification === 'NOT_FREE' ? i.proposedProviderId || null : null, rationale: i.classification === 'NOT_FREE' ? i.rationale?.trim() || null : null, warrantyImpactNote: i.warrantyImpactNote?.trim() || null, version: i.version, serviceChoice: i.classification === 'NOT_FREE' ? i.serviceChoice || 'EXTERNAL' : null })) }
  setBusy(true)
  try { const result = mode === 'create' ? await plansApi.create(body) : await plansApi.edit(id, { ...body, version: plan!.version }); navigate(`/plans/${result.id}`, { replace: true, state: { flash: 'Đã lưu kế hoạch. Chỉ gửi duyệt khi tất cả thiết bị đủ thông tin.' } }) } catch (e) { setError(e) } finally { setBusy(false) }
 }
 if (loading) return <p>Đang tải biểu mẫu…</p>
 if (mode === 'edit' && !plan) return <WorkflowError error={error} onReload={reload} />
 return <div className="page-stack"><div className="page-title-block"><h1>{mode === 'create' ? 'Kế hoạch bảo trì mới' : 'Chỉnh sửa kế hoạch'}</h1><p>Chuẩn bị hình thức và đơn vị cho từng thiết bị trước khi gửi phê duyệt.</p></div>
 {locked && <p className="retention-note">Kế hoạch đã gửi duyệt: thông tin và hình thức bảo trì chỉ được xem. BGĐ cần trả về hiệu chỉnh để thay đổi.</p>}
 <form className="plan-form" onSubmit={e => void save(e)}><fieldset disabled={locked || busy} className="plan-fields"><section className="panel business-panel"><h2>Thông tin kế hoạch</h2><div className="form-grid"><label>Tiêu đề<input required maxLength={255} value={title} onChange={e => setTitle(e.target.value)} /></label><label>Ngày bắt đầu<input required type="date" value={periodStart} onChange={e => setPeriodStart(e.target.value)} /></label><label>Ngày kết thúc<input required type="date" value={periodEnd} onChange={e => setPeriodEnd(e.target.value)} /></label></div></section></fieldset>
 <section className="panel business-panel"><h2>Thiết bị trong kế hoạch</h2><p className="muted">{items.length} thiết bị. Có thể lưu nháp khi chưa đủ thông tin; gửi duyệt sẽ kiểm tra từng hạng mục.</p>{mode === 'edit' && !locked && <p className="retention-note">Giữ hạng mục đã tạo để bảo toàn lịch sử. Có thể đổi ngày, hình thức, đơn vị đề xuất hoặc thêm thiết bị trong Nháp / Yêu cầu chỉnh sửa.</p>}
 {items.length > 0 ? <div className="table-scroll"><table className="data-table compact-table"><thead><tr><th>Thiết bị / khoa</th><th>Ngày dự kiến</th><th>Bảo hành</th><th>Hình thức</th><th>Đơn vị</th><th>Thông tin</th><th>Thao tác</th></tr></thead><tbody>{items.map(i => <Fragment key={i.equipmentId}><tr><td><strong>{i.equipmentCode} · {i.equipmentName}</strong><span className="row-sub">{i.departmentName}</span></td><td>{businessDate(i.plannedDate)}</td><td><WarrantySummary info={i.warranty} referenceDate={i.plannedDate || periodStart || businessToday()} coverageId={i.classification === 'FREE' ? i.coverageId : undefined} /></td><td>{i.classification === 'FREE' ? 'Theo hợp đồng' : i.classification === 'NOT_FREE' ? serviceChoiceLabels[i.serviceChoice || 'EXTERNAL'] : 'Chưa chọn'}</td><td>{i.classification === 'FREE' ? i.coverages?.find(c => c.id === i.coverageId)?.providerName || '—' : providers.find(p => p.id === i.proposedProviderId)?.name || i.proposedProviderName || 'Chưa chọn'}</td><td><StatusBadge label={itemCompleteness(i, periodStart) || 'Đã đủ thông tin'} tone={itemCompleteness(i, periodStart) ? 'amber' : 'teal'} /></td><td><div className="table-actions"><button className="button secondary compact" type="button" aria-expanded={expanded === i.equipmentId} aria-controls={`decision-${i.equipmentId}`} onClick={() => setExpanded(value => value === i.equipmentId ? null : i.equipmentId)}>{locked ? 'Xem chi tiết' : 'Thiết lập bảo trì'} {i.equipmentCode}</button><button className="table-link plain-button" type="button" onClick={() => setWarrantyItem(i)}>Thông tin bảo hành</button>{mode === 'create' && !locked && <button className="table-link plain-button" type="button" disabled={busy} onClick={() => setItems(current => current.filter(row => row.equipmentId !== i.equipmentId))}>Bỏ chọn</button>}</div></td></tr><tr hidden={expanded !== i.equipmentId} id={`decision-${i.equipmentId}`} className="expanded-table-row"><td colSpan={7}><ItemDecision key={`${i.equipmentId}-${evidenceReload}`} item={i} periodStart={periodStart} providers={providers} locked={locked || busy} onChange={change => patch(i.equipmentId, change)} /></td></tr></Fragment>)}</tbody></table></div> : <p className="empty-state">Chưa chọn thiết bị.</p>}</section>

 {!locked && <section className="panel business-panel"><h2>Chọn thêm thiết bị</h2>{equipment && <><div className="table-scroll"><table className="data-table"><thead><tr><th>Mã thiết bị</th><th>Tên</th><th>Khoa</th><th>Bảo hành</th><th></th></tr></thead><tbody>{equipment.content.map(row => <tr key={row.id}><td>{row.equipmentCode}</td><td>{row.name}</td><td>{row.departmentName}</td><td><WarrantySummary info={equipmentWarranties[row.id]} referenceDate={periodStart || businessToday()} /></td><td><div className="table-actions"><button type="button" className="table-link plain-button" onClick={() => setWarrantyItem({ equipmentId: row.id })}>Thông tin bảo hành</button><button className="button secondary" type="button" disabled={busy || items.some(i => i.equipmentId === row.id)} onClick={() => add(row)}>Thêm {row.equipmentCode}</button></div></td></tr>)}</tbody></table></div><Pagination data={equipment} onPage={setEquipmentPage} /></>}</section>}
 {validation && <p className="workflow-error" role="alert">{validation}</p>}<WorkflowError error={error} onReload={reload} /><div className="form-actions"><Link className="button secondary" to={mode === 'edit' ? `/plans/${id}` : '/plans'}>{locked ? 'Về chi tiết' : 'Hủy'}</Link>{!locked && <button type="submit" className="button primary" disabled={busy}>{busy ? 'Đang lưu…' : mode === 'create' ? 'Tạo kế hoạch' : 'Lưu chỉnh sửa'}</button>}</div></form>{warrantyItem && <WarrantyModal equipmentId={warrantyItem.equipmentId} referenceDate={warrantyItem.plannedDate || periodStart || undefined} onClose={() => setWarrantyItem(null)} onUpdated={() => setEvidenceReload(value => value + 1)} />}</div>
}
