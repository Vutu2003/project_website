import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { adminCatalogsApi } from '../api/adminCatalogsApi'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { WorkflowError, WorkflowSuccess } from '../components/WorkflowFeedback'
import type { CatalogItem, CatalogKind } from '../types/catalog'
import type { PageResponse } from '../types/workflow'
import { UserInputError } from '../utils/UserInputError'

const title = (kind: CatalogKind) => kind === 'departments' ? 'Khoa / Phòng' : 'Đơn vị bảo trì'
const nameLabel = (kind: CatalogKind) => kind === 'departments' ? 'Tên Khoa/Phòng' : 'Tên đơn vị'
const path = (kind: CatalogKind) => '/admin/catalogs/' + kind
function CatalogLinks() {
  return <div className="header-actions"><Link className="button secondary" to={path('departments')}>Khoa / Phòng</Link>
    <Link className="button secondary" to={path('providers')}>Đơn vị bảo trì</Link></div>
}
export function AdminCatalogListPage({ kind }: { kind: CatalogKind }) {
  const [page, setPage] = useState(0)
  const [input, setInput] = useState('')
  const [search, setSearch] = useState('')
  const [active, setActive] = useState('')
  const [data, setData] = useState<PageResponse<CatalogItem> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [refresh, setRefresh] = useState(0)
  const location = useLocation()
  const flash = (location.state as { flash?: string } | null)?.flash ?? null
  useEffect(() => {
    let current = true
    adminCatalogsApi.list(kind, page, 10, search, active === '' ? undefined : active === 'true')
      .then(rows => { if (current) { setData(rows); setError(null); setLoading(false) } })
      .catch(failure => { if (current) { setError(failure); setLoading(false) } })
    return () => { current = false }
  }, [kind, page, search, active, refresh])
  function reload() { setLoading(true); setRefresh(value => value + 1) }
  function find(event: FormEvent) { event.preventDefault(); setPage(0); setSearch(input.trim()); reload() }
  return <div className="page-stack account-page">
    <div className="page-title-row"><div className="page-title-block"><p className="eyebrow">ADMIN · DANH MỤC HỆ THỐNG</p>
      <h1>{title(kind)}</h1><p>Quản lý mã, tên và trạng thái sử dụng.</p></div>
      <Link className="button primary" to={path(kind) + '/new'}>Tạo {title(kind)}</Link></div>
    <CatalogLinks /><WorkflowSuccess message={flash} />
    <section className="panel business-panel"><div className="panel-heading"><h2>Danh sách {title(kind)}</h2>
      <button className="button secondary" type="button" onClick={reload}>Tải lại</button></div>
      <form className="account-filters" onSubmit={find}>
        <label className="account-search">Tìm theo mã hoặc tên<input type="search" maxLength={100} value={input} onChange={event => setInput(event.target.value)} /></label>
        <button className="button secondary" type="submit">Tìm kiếm</button>
        <label>Trạng thái<select value={active} onChange={event => { setActive(event.target.value); setPage(0); setLoading(true) }}>
          <option value="">Tất cả trạng thái</option><option value="true">Hoạt động</option><option value="false">Không hoạt động</option></select></label>
        <button className="button secondary" type="button" onClick={() => { setInput(''); setSearch(''); setActive(''); setPage(0); reload() }}>Xóa bộ lọc</button>
      </form>
      {loading ? <p className="muted" role="status">Đang tải danh mục…</p> : error ? <WorkflowError error={error} onReload={reload} /> : data && <>
        {data.content.length === 0 ? <p className="empty-state">Không có mục phù hợp.</p> : <div className="table-scroll"><table className="data-table account-table"><thead><tr>
          <th>Mã</th><th>{nameLabel(kind)}</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
          {data.content.map(row => <tr key={row.id}><td>{row.code}</td><td>{row.name}</td>
            <td><StatusBadge label={row.active ? 'Hoạt động' : 'Không hoạt động'} tone={row.active ? 'teal' : 'amber'} /></td>
            <td><Link className="table-link" to={path(kind) + '/' + row.id}>Xem</Link></td></tr>)}</tbody></table></div>}
        <Pagination data={data} onPage={next => { setPage(next); setLoading(true) }} /></>}
    </section>
  </div>
}
export function AdminCatalogDetailPage({ kind }: { kind: CatalogKind }) {
  const id = Number(useParams().id)
  const location = useLocation()
  const [item, setItem] = useState<CatalogItem | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [notice, setNotice] = useState<string | null>((location.state as { flash?: string } | null)?.flash ?? null)
  const [refresh, setRefresh] = useState(0)
  useEffect(() => {
    let current = true
    if (!Number.isInteger(id) || id <= 0) { setLoading(false); return () => { current = false } }
    adminCatalogsApi.detail(kind, id).then(row => { if (current) { setItem(row); setError(null); setLoading(false) } })
      .catch(failure => { if (current) { setError(failure); setLoading(false) } })
    return () => { current = false }
  }, [kind, id, refresh])
  function reload() { setLoading(true); setRefresh(value => value + 1) }
  async function changeStatus() {
    if (!item || busy) return
    if (!window.confirm('Xác nhận ' + (item.active ? 'vô hiệu hóa ' : 'kích hoạt ') + item.name + '?')) return
    setBusy(true); setError(null); setNotice(null)
    try {
      await (item.active ? adminCatalogsApi.deactivate(kind, id) : adminCatalogsApi.activate(kind, id))
      const fresh = await adminCatalogsApi.detail(kind, id)
      setItem(fresh); setNotice(fresh.active ? 'Đã kích hoạt.' : 'Đã vô hiệu hóa.')
    } catch (failure) { setError(failure) } finally { setBusy(false) }
  }
  return <div className="page-stack account-page"><div className="page-title-row"><div className="page-title-block">
    <p className="eyebrow">ADMIN · DANH MỤC HỆ THỐNG</p><h1>Chi tiết {title(kind)}</h1></div>
    <Link className="button secondary" to={path(kind)}>Về danh sách</Link></div>
    <WorkflowSuccess message={notice} /><WorkflowError error={error} onReload={reload} />
    {!Number.isInteger(id) || id <= 0 ? <p className="workflow-error" role="alert">Đường dẫn không hợp lệ.</p> :
      loading ? <p className="muted" role="status">Đang tải chi tiết…</p> : item && <section className="panel business-panel">
        <div className="panel-heading"><h2>{item.name}</h2><StatusBadge label={item.active ? 'Hoạt động' : 'Không hoạt động'} tone={item.active ? 'teal' : 'amber'} /></div>
        <dl className="detail-list"><div><dt>Mã</dt><dd>{item.code}</dd></div><div><dt>{nameLabel(kind)}</dt><dd>{item.name}</dd></div>
          {kind === 'providers' && <div><dt>Liên hệ</dt><dd>{item.contactDetails || '—'}</dd></div>}
          <div><dt>Trạng thái</dt><dd>{item.active ? 'Hoạt động' : 'Không hoạt động'}</dd></div></dl>
        <div className="header-actions account-detail-actions"><Link className="button secondary" to={path(kind) + '/' + id + '/edit'}>Chỉnh sửa</Link>
          <button className="button secondary" type="button" disabled={busy} onClick={() => void changeStatus()}>{item.active ? 'Vô hiệu hóa' : 'Kích hoạt'}</button>
          <button className="button secondary" type="button" onClick={reload}>Tải lại</button></div>
      </section>}
  </div>
}
export function AdminCatalogFormPage({ kind, mode }: { kind: CatalogKind; mode: 'create' | 'edit' }) {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [contactDetails, setContactDetails] = useState('')
  const [loading, setLoading] = useState(mode === 'edit')
  const [loaded, setLoaded] = useState(mode === 'create')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [refresh, setRefresh] = useState(0)
  useEffect(() => {
    if (mode === 'create' || !Number.isInteger(id) || id <= 0) return
    let current = true
    adminCatalogsApi.detail(kind, id).then(row => { if (current) { setCode(row.code); setName(row.name); setContactDetails(row.contactDetails ?? ''); setLoaded(true); setError(null); setLoading(false) } })
      .catch(failure => { if (current) { setLoaded(false); setError(failure); setLoading(false) } })
    return () => { current = false }
  }, [kind, mode, id, refresh])
  async function save(event: FormEvent) {
    event.preventDefault(); setError(null)
    if (!code.trim() || !name.trim()) { setError(new UserInputError('Mã và tên là bắt buộc.')); return }
    if (busy) return
    setBusy(true)
    try {
      const body = { code: code.trim(), name: name.trim(), ...(kind === 'providers' ? { contactDetails: contactDetails.trim() || null } : {}) }
      const result = mode === 'create' ? await adminCatalogsApi.create(kind, body) : await adminCatalogsApi.edit(kind, id, body)
      navigate(path(kind) + '/' + result.id, { replace: true, state: { flash: mode === 'create' ? 'Đã tạo danh mục.' : 'Đã lưu chỉnh sửa.' } })
    } catch (failure) { setError(failure) } finally { setBusy(false) }
  }
  return <div className="page-stack account-page"><div className="page-title-block"><p className="eyebrow">ADMIN · DANH MỤC HỆ THỐNG</p>
    <h1>{mode === 'create' ? 'Tạo' : 'Chỉnh sửa'} {title(kind)}</h1></div>
    {mode === 'edit' && (!Number.isInteger(id) || id <= 0) ? <p className="workflow-error" role="alert">Đường dẫn không hợp lệ.</p> :
      loading ? <p className="muted" role="status">Đang tải biểu mẫu…</p> :
      !loaded ? <WorkflowError error={error} onReload={() => { setLoading(true); setRefresh(value => value + 1) }} /> :
        <form className="plan-form" onSubmit={save} noValidate><section className="panel business-panel"><div className="panel-heading"><h2>Thông tin danh mục</h2></div>
          <div className="form-grid account-form-grid">
            <label>Mã<input value={code} maxLength={100} required onChange={event => setCode(event.target.value)} /></label>
            <label>{nameLabel(kind)}<input value={name} maxLength={100} required onChange={event => setName(event.target.value)} /></label>
            {kind === 'providers' && <label>Liên hệ<input value={contactDetails} maxLength={500} onChange={event => setContactDetails(event.target.value)} /></label>}
          </div></section><WorkflowError error={error} onReload={mode === 'edit' ? () => { setLoading(true); setRefresh(value => value + 1) } : undefined} />
          <div className="form-actions"><Link className="button secondary" to={mode === 'edit' ? path(kind) + '/' + id : path(kind)}>Hủy</Link>
            <button className="button primary" type="submit" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu'}</button></div></form>}
  </div>
}
