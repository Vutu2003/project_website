import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { MaintenanceSuggestionsPage } from './MaintenanceSuggestionsPage'
import { equipmentApi } from '../api/equipmentApi'
import { WarrantyModal } from '../components/WarrantyModal'
import { Pagination } from '../components/Pagination'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { Equipment, PageResponse } from '../types/workflow'

export function EquipmentListPage({ warrantyOnly = false }: { warrantyOnly?: boolean }) {
  const { user } = useAuth()
  return user?.role === 'PHONG_VTYT' && !warrantyOnly ? <MaintenanceSuggestionsPage /> : <EquipmentCatalogPage warrantyOnly={warrantyOnly} />
}
function EquipmentCatalogPage({ warrantyOnly }: { warrantyOnly: boolean }) {
  const [warranty, setWarranty] = useState<{ equipmentId: number; referenceDate?: string } | null>(null)
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
  return <div className="page-stack"><div className="page-title-block"><p className="eyebrow">UC12 · THIẾT BỊ</p><h1>{warrantyOnly ? 'Hồ sơ bảo hành thiết bị' : 'Thiết bị & lịch sử'}</h1>
    <p>{warrantyOnly ? 'Xem và cập nhật ngày hết bảo hành, nhà sản xuất hoặc đại diện cho từng thiết bị.' : 'Chọn thiết bị để xem các đợt bảo trì trong phạm vi backend cho phép.'}</p></div>
    <section className="panel business-panel"><div className="panel-heading"><h2>Danh sách thiết bị</h2><p>20 thiết bị mỗi trang</p></div>
      <form className="form-actions" onSubmit={event => { event.preventDefault(); setPage(0); setSearch(searchInput.trim()); setLoading(true); setRefresh(value => value + 1) }}>
        <label>Tìm theo mã, tên hoặc serial<input type="search" value={searchInput} onChange={event => setSearchInput(event.target.value)} /></label>
        <button className="button secondary" type="submit">Tìm kiếm</button>
      </form>
      {loading ? <p className="muted">Đang tải…</p> : error ? <WorkflowError error={error} onReload={() => { setLoading(true); setRefresh(value => value + 1) }} /> :
        data?.content.length === 0 ? <p className="empty-state">Không có thiết bị trong phạm vi hiển thị.</p> : data && <><div className="table-scroll"><table className="data-table"><thead><tr><th>Mã thiết bị</th><th>Tên / mẫu</th><th>Khoa hiện tại</th><th>Hoạt động</th><th></th></tr></thead><tbody>
          {data.content.map(item => <tr key={item.id}><td><strong>{item.equipmentCode}</strong></td><td>{item.name}<span className="row-sub">{item.model || '—'}</span></td>
            <td>{item.departmentName || '—'}</td><td>{item.active ? 'Có' : 'Không'}</td><td><div className="table-actions"><button type="button" className="button secondary compact" onClick={() => setWarranty({ equipmentId: item.id })}>Thông tin bảo hành</button>{!warrantyOnly && <Link className="table-link" to={`/equipment/${item.id}/history`}>Xem lịch sử</Link>}</div></td></tr>)}</tbody></table></div>
          <Pagination data={data} onPage={next => { setPage(next); setLoading(true) }} /></>}
    </section>{warranty && <WarrantyModal equipmentId={warranty.equipmentId} onClose={() => setWarranty(null)} />}</div>
}
