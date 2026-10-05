import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { adminAccountsApi } from '../api/adminAccountsApi'
import { adminCatalogsApi } from '../api/adminCatalogsApi'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { WorkflowError, WorkflowSuccess } from '../components/WorkflowFeedback'
import type { AccountSummary } from '../types/account'
import type { Department, PageResponse } from '../types/workflow'
import { roles } from '../types/auth'
import type { Role } from '../types/auth'
import { roleLabels } from '../utils/roleLabels'

export function AdminAccountListPage() {
  const location = useLocation()
  const [page, setPage] = useState(0)
  const [input, setInput] = useState('')
  const [search, setSearch] = useState('')
  const [params] = useSearchParams()
  const [role, setRole] = useState<Role | ''>(roles.includes(params.get('role') as Role) ? params.get('role') as Role : '')
  const [department, setDepartment] = useState('')
  const [active, setActive] = useState(['true','false'].includes(params.get('active') ?? '') ? params.get('active')! : '')
  const [data, setData] = useState<PageResponse<AccountSummary> | null>(null)
  const [departments, setDepartments] = useState<Department[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [referenceError, setReferenceError] = useState<unknown>(null)
  const [refresh, setRefresh] = useState(0)
  const flash = (location.state as { flash?: string } | null)?.flash ?? null
  useEffect(() => {
    let current = true
    adminCatalogsApi.allDepartments().then(rows => { if (current) { setDepartments(rows); setReferenceError(null) } })
      .catch(failure => { if (current) setReferenceError(failure) })
    return () => { current = false }
  }, [refresh])
  useEffect(() => {
    let current = true
    adminAccountsApi.list(page, 10, { search, role: role || undefined,
      departmentId: department ? Number(department) : undefined, active: active === '' ? undefined : active === 'true' })
      .then(rows => { if (current) { setData(rows); setError(null); setLoading(false) } })
      .catch(failure => { if (current) { setError(failure); setLoading(false) } })
    return () => { current = false }
  }, [page, search, role, department, active, refresh])
  function reload() { setLoading(true); setRefresh(value => value + 1) }
  function find(event: FormEvent) { event.preventDefault(); setPage(0); setSearch(input.trim()); reload() }
  function clear() { setInput(''); setSearch(''); setRole(''); setDepartment(''); setActive(''); setPage(0); reload() }
  return <div className="page-stack account-page">
    <div className="page-title-row"><div className="page-title-block"><p className="eyebrow">ADMIN · TÀI KHOẢN</p><h1>Quản lý tài khoản</h1>
      <p>Tạo tài khoản đăng nhập và quản lý vai trò, khoa/phòng, trạng thái hoạt động.</p></div>
      <Link className="button primary" to="/admin/accounts/new">Tạo tài khoản</Link></div>
    <WorkflowSuccess message={flash} />
    <section className="panel business-panel"><div className="panel-heading"><h2>Danh sách tài khoản</h2>
      <button className="button secondary" type="button" onClick={reload}>Tải lại</button></div>
      <form className="account-filters" onSubmit={find}>
        <label className="account-search">Tìm theo tên đăng nhập<input type="search" maxLength={100} value={input} onChange={event => setInput(event.target.value)} placeholder="Nhập tên đăng nhập" /></label>
        <button className="button secondary" type="submit">Tìm kiếm</button>
        <label>Vai trò<select value={role} onChange={event => { setRole(event.target.value as Role | ''); setPage(0); setLoading(true) }}>
          <option value="">Tất cả vai trò</option>{roles.map(value => <option key={value} value={value}>{roleLabels[value]}</option>)}</select></label>
        <label>Khoa/Phòng<select value={department} onChange={event => { setDepartment(event.target.value); setPage(0); setLoading(true) }}>
          <option value="">Tất cả khoa/phòng</option>{departments.map(row => <option key={row.id} value={row.id}>{row.name}{row.active ? '' : ' (không hoạt động)'}</option>)}</select></label>
        <label>Trạng thái<select value={active} onChange={event => { setActive(event.target.value); setPage(0); setLoading(true) }}>
          <option value="">Tất cả trạng thái</option><option value="true">Hoạt động</option><option value="false">Không hoạt động</option></select></label>
        <button className="button secondary" type="button" onClick={clear}>Xóa bộ lọc</button>
      </form>
      <WorkflowError error={referenceError} onReload={reload} />
      {loading ? <p className="muted" role="status">Đang tải tài khoản…</p> : error ? <WorkflowError error={error} onReload={reload} /> :
        data && <>{data.content.length === 0 ? <p className="empty-state">Không có tài khoản phù hợp với bộ lọc.</p> : <div className="table-scroll"><table className="data-table account-table"><thead><tr>
          <th>Tên đăng nhập</th><th>Vai trò</th><th>Khoa/Phòng</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
          {data.content.map(account => <tr key={account.id}><td><Link className="table-link" to={`/admin/accounts/${account.id}`}>{account.username}</Link><span className="row-sub">#{account.id}</span></td>
            <td>{roleLabels[account.role]}</td><td>{account.departmentName || '—'}</td>
            <td><StatusBadge label={account.active ? 'Hoạt động' : 'Không hoạt động'} tone={account.active ? 'teal' : 'amber'} /></td>
            <td><Link className="table-link" to={`/admin/accounts/${account.id}`}>Xem</Link></td></tr>)}</tbody></table></div>}
          <Pagination data={data} onPage={next => { setPage(next); setLoading(true) }} /></>}
    </section>
  </div>
}
