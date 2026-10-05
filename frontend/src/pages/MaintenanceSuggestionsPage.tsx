import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { maintenanceSuggestionsApi } from '../api/maintenanceSuggestionsApi'
import type { EquipmentFilters } from '../api/maintenanceSuggestionsApi'
import { departmentsApi } from '../api/departmentsApi'
import { providersApi } from '../api/providersApi'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { WarrantyModal } from '../components/WarrantyModal'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { Department, Provider, MaintenanceSuggestion, PageResponse } from '../types/workflow'
import { businessDate, coverageLabels } from '../utils/workflowLabels'
import { dueLabels, intervalLabels } from '../utils/maintenanceSchedule'

export function MaintenanceSuggestionsPage() {
 const navigate = useNavigate()
 const [data, setData] = useState<PageResponse<MaintenanceSuggestion> | null>(null)
 const [departments, setDepartments] = useState<Department[]>([])
 const [providers, setProviders] = useState<Provider[]>([])
 const [page, setPage] = useState(0)
 const [filters, setFilters] = useState<EquipmentFilters>({ activity: 'active' })
 const [selected, setSelected] = useState<MaintenanceSuggestion[]>([])
 const [loading, setLoading] = useState(true)
 const [error, setError] = useState<unknown>(null)
 const [reload, setReload] = useState(0)
 const [warranty, setWarranty] = useState<MaintenanceSuggestion | null>(null)
 const [bulkBusy, setBulkBusy] = useState(false)
 useEffect(() => { let active = true; Promise.all([departmentsApi.list(), providersApi.list()]).then(([d, p]) => { if (active) { setDepartments(d); setProviders(p) } }).catch(e => { if (active) setError(e) }); return () => { active = false } }, [])
 useEffect(() => {
  let active = true; setLoading(true)
  maintenanceSuggestionsApi.list(page, 20, filters).then(result => { if (active) { setData(result); setError(null); setLoading(false) } }).catch(e => { if (active) { setError(e); setLoading(false) } })
  return () => { active = false }
 }, [page, filters, reload])
 function filter(key: keyof EquipmentFilters, value: string) { setFilters(current => ({ ...current, [key]: value })); setPage(0); setSelected([]) }
 function eligible(row: MaintenanceSuggestion) { return row.active !== false && !row.openPlanIds.length && row.contractStatus !== 'CONFLICT' }
 async function bulk(statuses: string[]) {
  setBulkBusy(true)
  try {
   const first = await maintenanceSuggestionsApi.list(0, 100, filters); const rows = [...first.content]
   for (let p = 1; p < first.totalPages; p++) rows.push(...(await maintenanceSuggestionsApi.list(p, 100, filters)).content)
   setSelected(current => [...new Map([...current, ...rows.filter(r => eligible(r) && statuses.includes(r.dueStatus || ''))].map(r => [r.equipmentId, r])).values()])
  } catch (e) { setError(e) } finally { setBulkBusy(false) }
 }
 const rows = data?.content || []
 return <div className="page-stack"><div className="page-title-row"><div className="page-title-block"><h1>Đề xuất bảo trì</h1><p>Hệ thống tính ngày đến hạn theo chu kỳ và lịch sử hoàn tất. Lọc theo khoa, chọn thiết bị và xem lại kế hoạch trước khi gửi BGĐ.</p></div><button className="button secondary" onClick={() => setReload(v => v + 1)}>Tải lại</button></div>
 <WorkflowError error={error} onReload={() => setReload(v => v + 1)} />
 <section className="panel business-panel summary-panel">{[['Quá hạn', rows.filter(r => r.dueStatus === 'OVERDUE').length], ['Đến hạn / Sắp đến hạn', rows.filter(r => ['DUE', 'DUE_SOON'].includes(r.dueStatus || '')).length], ['Theo hợp đồng', rows.filter(r => r.classification === 'FREE').length], ['Ngoài hợp đồng', rows.filter(r => r.classification === 'NOT_FREE').length]].map(([label, count]) => <div key={label}><span className="card-label">{label}</span><strong>{count}</strong></div>)}<p className="muted">Thống kê trang hiện tại</p></section>
 <section className="panel business-panel"><div className="form-grid">
 <label>Tìm theo mã, tên hoặc serial<input type="search" value={filters.search || ''} onChange={e => filter('search', e.target.value)} /></label>
 <label>Khoa/Phòng<select value={filters.departmentId || ''} onChange={e => filter('departmentId', e.target.value)}><option value="">Tất cả khoa/phòng</option>{departments.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
 <label>Từ ngày đến hạn<input type="date" value={filters.dueFrom || ''} onChange={e => filter('dueFrom', e.target.value)} /></label><label>Đến ngày đến hạn<input type="date" value={filters.dueTo || ''} onChange={e => filter('dueTo', e.target.value)} /></label>
 <label>Trạng thái đến hạn<select value={filters.dueStatus || ''} onChange={e => filter('dueStatus', e.target.value)}><option value="">Tất cả trạng thái</option>{Object.entries(dueLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
 <label>Hình thức<select value={filters.classification || ''} onChange={e => filter('classification', e.target.value)}><option value="">Tất cả hình thức</option><option value="FREE">Theo hợp đồng</option><option value="NOT_FREE">Ngoài hợp đồng</option></select></label>
 <label>Đơn vị bảo trì<select value={filters.providerId || ''} onChange={e => filter('providerId', e.target.value)}><option value="">Tất cả đơn vị</option>{providers.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label>
 <label>Kế hoạch hiện tại<select value={filters.notInOpenPlan || ''} onChange={e => filter('notInOpenPlan', e.target.value)}><option value="">Tất cả thiết bị</option><option value="true">Chưa có trong kế hoạch</option><option value="false">Đã có trong kế hoạch</option></select></label>
 <label>Hoạt động<select value={filters.activity || 'active'} onChange={e => filter('activity', e.target.value)}><option value="active">Đang hoạt động</option><option value="inactive">Ngừng hoạt động</option><option value="all">Tất cả thiết bị</option></select></label></div>
 <div className="form-actions"><button className="button secondary" disabled={bulkBusy} onClick={() => void bulk(['OVERDUE', 'DUE'])}>Chọn tất cả thiết bị đến hạn</button><button className="button secondary" disabled={bulkBusy} onClick={() => void bulk(['OVERDUE'])}>Chọn tất cả thiết bị quá hạn</button><button className="button secondary" disabled={bulkBusy} onClick={() => void bulk(['DUE_SOON'])}>Chọn thiết bị sắp đến hạn</button><button className="button primary" disabled={!selected.length || bulkBusy} onClick={() => navigate('/plans/new', { state: { suggestions: selected } })}>Tạo kế hoạch từ thiết bị đã chọn ({selected.length})</button></div>
 <p>{data?.totalElements || 0} thiết bị phù hợp · {selected.length} đã chọn</p>
 {loading ? <p>Đang tổng hợp dữ liệu…</p> : !rows.length ? <p className="empty-state">Không có thiết bị phù hợp với bộ lọc.</p> : <><div className="table-scroll"><table className="data-table compact-table"><thead><tr><th>Chọn</th><th>Thiết bị / khoa</th><th>Lần bảo trì gần nhất</th><th>Chu kỳ</th><th>Ngày đến hạn</th><th>Trạng thái</th><th>Hợp đồng / hình thức</th><th>Đơn vị bảo trì</th><th>Kế hoạch hiện tại</th><th>Thao tác</th></tr></thead><tbody>{rows.map(row => <tr key={row.equipmentId}>
 <td><input type="checkbox" aria-label={`Chọn ${row.equipmentCode}`} disabled={!eligible(row)} checked={selected.some(s => s.equipmentId === row.equipmentId)} onChange={e => setSelected(current => e.target.checked ? [...current, row] : current.filter(s => s.equipmentId !== row.equipmentId))} /></td>
 <td><strong>{row.equipmentCode} · {row.equipmentName}</strong><span className="row-sub">{row.departmentName}</span>{row.active === false && <span className="warning-text">Ngừng hoạt động</span>}</td><td>{businessDate(row.lastMaintenanceDate)}</td><td>{row.maintenanceIntervalValue ? `${row.maintenanceIntervalValue} ${intervalLabels[row.maintenanceIntervalUnit || '']}` : '—'}</td><td>{businessDate(row.nextMaintenanceDueDate)}</td>
 <td><StatusBadge label={dueLabels[row.dueStatus || 'NO_SCHEDULE']} tone={row.dueStatus === 'OVERDUE' ? 'amber' : 'neutral'} /></td>
 <td>{row.contractStatus === 'CONFLICT' ? <span className="warning-text">Xung đột: nhiều hợp đồng hợp lệ</span> : <>{coverageLabels[row.classification]}{row.contractId && <Link className="row-sub table-link" to={`/contracts/${row.contractId}`}>{row.contractReference}</Link>}<span className="row-sub">{row.contractId ? `${businessDate(row.contractStartDate)} – ${businessDate(row.contractEndDate)}` : 'Không có hợp đồng hợp lệ'}</span></>}</td>
 <td>{row.contractualProviderId ? <Link to={`/providers/${row.contractualProviderId}`}>{row.contractualProviderName}</Link> : '—'}</td><td>{row.openPlanIds.length ? <>Đã có trong kế hoạch {row.openPlanIds.map(id => <Link key={id} to={`/plans/${id}`}>#{id} </Link>)}</> : 'Chưa có'}</td>
 <td><button className="button secondary compact" onClick={() => setWarranty(row)}>Thông tin bảo hành</button><Link className="table-link" to={`/equipment/${row.equipmentId}/history`}>Xem lịch sử</Link></td>
 </tr>)}</tbody></table></div>{data && <Pagination data={data} onPage={setPage} />}</>}
 </section>{warranty && <WarrantyModal equipmentId={warranty.equipmentId} referenceDate={warranty.referenceDate} onClose={() => setWarranty(null)} onUpdated={() => setReload(v => v + 1)} />}</div>
}
