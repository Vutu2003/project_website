import { useEffect, useState } from 'react'
import { Fragment } from 'react'
import { Link, useNavigate } from 'react-router'
import { maintenanceSuggestionsApi } from '../api/maintenanceSuggestionsApi'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { WarrantyModal } from '../components/WarrantyModal'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { MaintenanceSuggestion, PageResponse } from '../types/workflow'
import { businessDate, coverageLabels, itemStatusLabels } from '../utils/workflowLabels'
import { businessToday, warrantyLabels } from '../utils/warranty'

export function MaintenanceSuggestionsPage() {
  const navigate = useNavigate()
  const [data, setData] = useState<PageResponse<MaintenanceSuggestion> | null>(null)
  const [page, setPage] = useState(0)
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [activity, setActivity] = useState<'all' | 'active' | 'inactive'>('all')
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<MaintenanceSuggestion[]>([])
  const [error, setError] = useState<unknown>(null)
  const [reload, setReload] = useState(0)
  const [warranty, setWarranty] = useState<MaintenanceSuggestion | null>(null)
  const [expanded, setExpanded] = useState<number | null>(null)
  useEffect(() => {
    let active = true
    setLoading(true)
    maintenanceSuggestionsApi.list(page, 20, { search, activity, referenceDate: businessToday() }).then(result => {
      if (active) {
        setData(result); setError(null); setLoading(false)
        setSelected(current => current.map(row => result.content.find(fresh => fresh.equipmentId === row.equipmentId) || row).filter(row => row.active !== false))
      }
    }).catch(e => { if (active) { setError(e); setLoading(false) } })
    return () => { active = false }
  }, [page, reload, search, activity])
  function create(rows: MaintenanceSuggestion[]) { navigate('/plans/new', { state: { suggestions: rows } }) }
  return <div className="page-stack"><div className="page-title-row"><div className="page-title-block"><h1>Thiết bị & bảo trì</h1><p>Thông tin thiết bị, bảo hành, lịch sử và đề xuất bảo trì trong cùng một danh sách. Chọn thiết bị đang hoạt động để lập kế hoạch.</p></div><button className="button secondary" onClick={() => setReload(v => v + 1)}>Tải lại</button></div>
    <WorkflowError error={error} onReload={() => setReload(v => v + 1)} />
    <section className="panel business-panel"><div className="panel-heading"><div><h2>Danh sách thiết bị</h2><p>{data?.totalElements || 0} thiết bị · {selected.length} đã chọn</p></div>{selected.length > 0 && <button className="button primary" onClick={() => create(selected)}>Tạo kế hoạch từ {selected.length} thiết bị đã chọn</button>}</div>
      <form className="equipment-filters" onSubmit={event => { event.preventDefault(); setPage(0); setSearch(searchInput.trim()); setReload(v => v + 1) }}><label>Tìm theo mã, tên hoặc serial<input type="search" value={searchInput} onChange={event => setSearchInput(event.target.value)} /></label><label>Hoạt động<select value={activity} onChange={event => { setActivity(event.target.value as typeof activity); setPage(0) }}><option value="all">Tất cả thiết bị</option><option value="active">Đang hoạt động</option><option value="inactive">Ngừng hoạt động</option></select></label><button type="submit" className="button secondary">Tìm kiếm</button></form>
      {loading && !error ? <p>Đang tổng hợp dữ liệu…</p> : data?.content.length === 0 ? <p className="empty-state">Không có thiết bị phù hợp với bộ lọc.</p> : data && <><div className="table-scroll"><table className="data-table compact-table"><thead><tr><th>Chọn</th><th>Thiết bị / khoa</th><th>Bảo trì gần nhất</th><th>Ngày đề xuất</th><th>Hết bảo hành</th><th>Trạng thái bảo hành</th><th>Thao tác</th></tr></thead><tbody>{data.content.map(row => <Fragment key={row.equipmentId}><tr>
        <td className="selection-cell"><input type="checkbox" aria-label={`Chọn ${row.equipmentCode}`} disabled={row.active === false} checked={selected.some(s => s.equipmentId === row.equipmentId)} onChange={e => setSelected(current => e.target.checked ? [...current, row] : current.filter(s => s.equipmentId !== row.equipmentId))} /></td>
        <td><strong>{row.equipmentCode} · {row.equipmentName}</strong><span className="row-sub">{row.departmentName}</span><span className="row-sub">Mẫu: {row.model || '—'} · Serial: {row.serialNumber || '—'}</span>{row.active === false && <StatusBadge label="Ngừng hoạt động" tone="amber" />}{row.openPlanIds.length > 0 && <span className="row-sub warning-text">Đang có trong kế hoạch: {row.openPlanIds.map(id => `#${id}`).join(', ')}</span>}</td>
        <td>{businessDate(row.lastMaintenanceDate)}</td><td>{row.suggestedDate ? businessDate(row.suggestedDate) : <span className="muted">Chưa đủ dữ liệu để đề xuất thời gian</span>}</td><td>{businessDate(row.warrantyExpiresOn)}</td>
        <td><StatusBadge label={warrantyLabels[row.warrantyStatus || 'UNKNOWN']} tone={row.warrantyStatus === 'ACTIVE' ? 'teal' : 'amber'} /><span className="row-sub">Tính đến {businessDate(row.referenceDate)}</span></td>
        <td><div className="table-actions"><button type="button" className="button secondary compact" onClick={() => setWarranty(row)}>Thông tin bảo hành</button>{row.active !== false && (row.classification === 'NOT_FREE' ? <><button className="button secondary compact" disabled={!row.manufacturerProviderId} title={!row.manufacturerProviderId ? 'Cập nhật nhà sản xuất trong thông tin bảo hành' : undefined} onClick={() => create([{ ...row, serviceChoice: 'MANUFACTURER' }])}>Liên hệ nhà sản xuất</button><button className="button secondary compact" onClick={() => create([{ ...row, serviceChoice: 'EXTERNAL' }])}>Bảo hành ngoài</button></> : <button className="button secondary compact" onClick={() => create([row])}>Đưa vào kế hoạch</button>)}<Link className="table-link" to={`/equipment/${row.equipmentId}/history`}>Xem lịch sử</Link><button type="button" className="table-link plain-button" aria-expanded={expanded === row.equipmentId} aria-controls={`suggestion-${row.equipmentId}`} onClick={() => setExpanded(v => v === row.equipmentId ? null : row.equipmentId)}>{expanded === row.equipmentId ? 'Ẩn chi tiết' : 'Chi tiết thiết bị'}</button></div></td>
      </tr>{expanded === row.equipmentId && <tr className="expanded-table-row" id={`suggestion-${row.equipmentId}`}><td colSpan={7}><dl className="detail-list"><div><dt>Thông số kỹ thuật</dt><dd>{row.technicalSpec || 'Chưa có thông tin'}</dd></div><div><dt>Nhà sản xuất / đại diện</dt><dd>{row.manufacturerName || 'Chưa có thông tin'}<span className="row-sub">{row.manufacturerContact}</span></dd></div><div><dt>Căn cứ đề xuất</dt><dd>{row.suggestionBasis}</dd></div><div><dt>Hình thức</dt><dd>{coverageLabels[row.classification]}<span className="row-sub">{row.contractualProviderName} {row.contractReference}</span></dd></div><div><dt>Kết quả mới nhất</dt><dd>{row.latestResult === 'PASS' ? 'Đạt' : row.latestResult === 'FAIL' ? 'Không đạt' : '—'} · {row.latestStatus ? itemStatusLabels[row.latestStatus] : 'Chưa có lịch sử'}</dd></div><div><dt>Ghi chú hợp đồng</dt><dd>{row.coverageNote}</dd></div></dl>{row.lastExternalProviderName && <p className="muted">Đơn vị ngoài gần nhất: {row.lastExternalProviderName} (tham khảo).</p>}</td></tr>}</Fragment>)}</tbody></table></div><Pagination data={data} onPage={setPage} /></>}
    </section>
    {warranty && <WarrantyModal equipmentId={warranty.equipmentId} referenceDate={warranty.referenceDate} onClose={() => setWarranty(null)} onUpdated={() => setReload(v => v + 1)} />}
  </div>
}
