import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { equipmentApi } from '../api/equipmentApi'
import { Pagination } from '../components/Pagination'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { Equipment, PageResponse } from '../types/workflow'

export function EquipmentListPage() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<Equipment> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [refresh, setRefresh] = useState(0)
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  useEffect(() => {
    let active = true
    equipmentApi.list(page, 20, undefined, search).then(rows => { if (active) { setData(rows); setError(null); setLoading(false) } })
      .catch(failure => { if (active) { setError(failure); setLoading(false) } })
    return () => { active = false }
  }, [page, refresh, search])
  return <div className="page-stack"><div className="page-title-block"><p className="eyebrow">UC12 · THIẾT BỊ</p><h1>Thiết bị & lịch sử</h1>
    <p>Chọn thiết bị để xem các đợt bảo trì trong phạm vi backend cho phép.</p></div>
    <section className="panel business-panel"><div className="panel-heading"><h2>Danh sách thiết bị</h2><p>20 thiết bị mỗi trang</p></div>
      <form className="form-actions" onSubmit={event => { event.preventDefault(); setPage(0); setSearch(searchInput.trim()); setLoading(true); setRefresh(value => value + 1) }}>
        <label>Tìm theo mã, tên hoặc serial<input type="search" value={searchInput} onChange={event => setSearchInput(event.target.value)} /></label>
        <button className="button secondary" type="submit">Tìm kiếm</button>
      </form>
      {loading ? <p className="muted">Đang tải…</p> : error ? <WorkflowError error={error} onReload={() => { setLoading(true); setRefresh(value => value + 1) }} /> :
        data?.content.length === 0 ? <p className="empty-state">Không có thiết bị trong phạm vi hiển thị.</p> : data && <><div className="table-scroll"><table className="data-table"><thead><tr><th>Mã thiết bị</th><th>Tên / mẫu</th><th>Khoa hiện tại</th><th>Hoạt động</th><th></th></tr></thead><tbody>
          {data.content.map(item => <tr key={item.id}><td><strong>{item.equipmentCode}</strong></td><td>{item.name}<span className="row-sub">{item.model || '—'}</span></td>
            <td>{item.departmentName || '—'}</td><td>{item.active ? 'Có' : 'Không'}</td><td><Link className="table-link" to={`/equipment/${item.id}/history`}>Xem lịch sử</Link></td></tr>)}</tbody></table></div>
          <Pagination data={data} onPage={next => { setPage(next); setLoading(true) }} /></>}
    </section></div>
}
