import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { maintenanceSuggestionsApi } from '../api/maintenanceSuggestionsApi'
import { Pagination } from '../components/Pagination'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { MaintenanceSuggestion, PageResponse } from '../types/workflow'
import { businessDate, coverageLabels, itemStatusLabels } from '../utils/workflowLabels'
export function MaintenanceSuggestionsPage() {
 const navigate = useNavigate()
 const [data, setData] = useState<PageResponse<MaintenanceSuggestion> | null>(null)
 const [page, setPage] = useState(0)
 const [selected, setSelected] = useState<MaintenanceSuggestion[]>([])
 const [error, setError] = useState<unknown>(null)
 const [reload, setReload] = useState(0)
 useEffect(() => { let active = true; maintenanceSuggestionsApi.list(page).then(result => { if (active) { setData(result); setError(null) } }).catch(e => { if (active) setError(e) }); return () => { active = false } }, [page, reload])
 function create(rows: MaintenanceSuggestion[]) { navigate('/plans/new', { state: { suggestions: rows } }) }
 return <div className="page-stack"><div className="page-title-row"><div className="page-title-block"><h1>Đề xuất bảo trì</h1><p>Tổng hợp từ hợp đồng, kế hoạch và lịch sử bảo trì thực tế. Chọn thiết bị để chuẩn bị kế hoạch.</p></div><button className="button secondary" onClick={() => setReload(v => v + 1)}>Tải lại</button></div>
 <WorkflowError error={error} onReload={() => setReload(v => v + 1)} />
 {selected.length > 0 && <button className="button primary" onClick={() => create(selected)}>Tạo kế hoạch từ {selected.length} thiết bị đã chọn</button>}
 {!data && !error ? <p>Đang tổng hợp dữ liệu…</p> : data?.content.length === 0 ? <p className="empty-state">Không có thiết bị đang hoạt động.</p> : data && <><div className="suggestion-grid">{data.content.map(row => <article className="panel business-panel suggestion-card" key={row.equipmentId}>
 <label className="check-line"><input type="checkbox" checked={selected.some(s => s.equipmentId === row.equipmentId)} onChange={e => setSelected(current => e.target.checked ? [...current, row] : current.filter(s => s.equipmentId !== row.equipmentId))} />{row.equipmentCode} · {row.equipmentName}</label><p>{row.departmentName}</p>
 <dl className="detail-list"><div><dt>Bảo trì hoàn tất gần nhất</dt><dd>{businessDate(row.lastMaintenanceDate)}</dd></div><div><dt>Kết quả / trạng thái mới nhất</dt><dd>{row.latestResult === 'PASS' ? 'Đạt' : row.latestResult === 'FAIL' ? 'Không đạt' : '—'} · {row.latestStatus ? itemStatusLabels[row.latestStatus] : 'Chưa có lịch sử'}</dd></div>
 <div><dt>Hình thức bảo trì</dt><dd>{coverageLabels[row.classification]}{row.contractualProviderName && <span className="row-sub">{row.contractualProviderName} · {row.contractReference}</span>}</dd></div><div><dt>Thời gian đề xuất</dt><dd>{row.suggestedDate ? businessDate(row.suggestedDate) : 'Chưa đủ dữ liệu để đề xuất thời gian'}</dd></div></dl>
 <p className="retention-note">{row.suggestionBasis}</p><p className="muted">Ngày xét hợp đồng: {businessDate(row.referenceDate)}. {row.coverageNote}</p>
 {row.lastExternalProviderName && <p className="muted">Đơn vị ngoài gần nhất: {row.lastExternalProviderName} (tham khảo).</p>}
 {row.openPlanIds.length > 0 && <p className="warning-text">Đang có trong kế hoạch: {row.openPlanIds.map(id => `#${id}`).join(', ')}. Kiểm tra trước khi lập thêm.</p>}
 <button className="button secondary" onClick={() => create([row])}>Đưa vào kế hoạch</button></article>)}</div><Pagination data={data} onPage={setPage} /></>}
 </div>
}
