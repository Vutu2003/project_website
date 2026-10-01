import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { adminAccountsApi } from '../api/adminAccountsApi'
import { departmentsApi } from '../api/departmentsApi'
import { adminCatalogsApi } from '../api/adminCatalogsApi'
import { useAuth } from '../auth/useAuth'
import { WorkflowError } from '../components/WorkflowFeedback'
import { PasswordInput } from '../components/PasswordInput'
import type { AccountDetail } from '../types/account'
import type { Department } from '../types/workflow'
import { roles } from '../types/auth'
import type { Role } from '../types/auth'
import { roleLabels } from '../utils/roleLabels'
import { UserInputError } from '../utils/UserInputError'

export function AdminAccountFormPage({ mode }: { mode: 'create' | 'edit' }) {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const { user, retryRestore } = useAuth()
  const [account, setAccount] = useState<AccountDetail | null>(null)
  const [departments, setDepartments] = useState<Department[]>([])
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('PHONG_VTYT')
  const [department, setDepartment] = useState('')
  const [active, setActive] = useState(true)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [refresh, setRefresh] = useState(0)
  useEffect(() => {
    let current = true
    if (mode === 'edit' && (!Number.isInteger(id) || id <= 0)) {
      return () => { current = false }
    }
    Promise.all([departmentsApi.list(), mode === 'edit' ? adminAccountsApi.detail(id) : Promise.resolve(null)])
      .then(async ([refs, existing]) => {
        if (!current) return
        let available = refs
        if (existing?.departmentId && !refs.some(row => row.id === existing.departmentId)) {
          const historical = await adminCatalogsApi.detail('departments', existing.departmentId)
          available = [...refs, historical]
        }
        if (!current) return
        setDepartments(available); setAccount(existing)
        if (existing) { setUsername(existing.username); setRole(existing.role); setDepartment(existing.departmentId == null ? '' : String(existing.departmentId)); setActive(existing.active) }
        setError(null); setLoading(false)
      }).catch(failure => { if (current) { setError(failure); setLoading(false) } })
    return () => { current = false }
  }, [mode, id, refresh])
  function reload() { setLoading(true); setPassword(''); setRefresh(value => value + 1) }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(null)
    if (mode === 'create' && (!username.trim() || !password.trim())) { setError(new UserInputError('Tên đăng nhập và mật khẩu không được để trống.')); return }
    if (username.trim().length > 100) { setError(new UserInputError('Tên đăng nhập tối đa 100 ký tự.')); return }
    if (role === 'KHOA_PHONG' && !department) { setError(new UserInputError('Vui lòng chọn Khoa/Phòng cho tài khoản Khoa/Phòng.')); return }
    if (busy) return
    setBusy(true)
    try {
      const assignment = { role, departmentId: department ? Number(department) : null }
      const result = mode === 'create' ? await adminAccountsApi.create({ username: username.trim(), password, ...assignment, active }) : await adminAccountsApi.edit(id, assignment)
      setPassword('')
      if (mode === 'edit' && result.id === user?.id && result.role !== user.role) await retryRestore()
      navigate(`/admin/accounts/${result.id}`, { replace: true, state: { flash: mode === 'create' ? 'Đã tạo tài khoản. Có thể đăng nhập bằng thông tin vừa cấp.' : 'Đã lưu vai trò và khoa/phòng.' } })
    } catch (failure) { setError(failure) } finally { setBusy(false) }
  }
  const validId = mode === 'create' || (Number.isInteger(id) && id > 0)
  return <div className="page-stack account-page">
    <div className="page-title-block"><p className="eyebrow">ADMIN · TÀI KHOẢN</p><h1>{mode === 'create' ? 'Tạo tài khoản' : 'Chỉnh sửa tài khoản'}</h1>
      <p>{mode === 'create' ? 'ADMIN nhập mật khẩu trực tiếp. Người dùng đăng nhập bằng đúng mật khẩu đã cấp.' : 'Tên đăng nhập cố định; trạng thái và mật khẩu được quản lý tại trang chi tiết.'}</p></div>
    {!validId ? <p className="workflow-error" role="alert">Đường dẫn tài khoản không hợp lệ.</p> : loading ? <p className="muted" role="status">Đang tải biểu mẫu…</p> :
      (error && departments.length === 0) || (mode === 'edit' && !account) ? <WorkflowError error={error} onReload={reload} /> :
        <form className="plan-form" onSubmit={save} noValidate><section className="panel business-panel"><div className="panel-heading"><h2>Thông tin tài khoản</h2></div>
          <div className="form-grid account-form-grid">
            <label>Tên đăng nhập<input autoComplete="username" value={username} readOnly={mode === 'edit'} maxLength={100} required onChange={event => setUsername(event.target.value)} /></label>
            {mode === 'create' && <PasswordInput label="Mật khẩu" autoComplete="new-password" value={password} required onChange={event => setPassword(event.target.value)} helpText="Không để trống. Tối đa 72 byte UTF-8; không yêu cầu độ phức tạp." />}
            <label>Vai trò<select value={role} onChange={event => setRole(event.target.value as Role)}>{roles.map(value => <option key={value} value={value}>{roleLabels[value]}</option>)}</select></label>
            <label>Khoa/Phòng {role === 'KHOA_PHONG' && <span className="warning-text">(bắt buộc)</span>}<select value={department} required={role === 'KHOA_PHONG'} onChange={event => setDepartment(event.target.value)}>
              <option value="">Không chọn khoa/phòng</option>{departments.filter(row => row.active || row.id === account?.departmentId).map(row => <option key={row.id} value={row.id}>{row.name}{row.active ? '' : ' (không hoạt động)'}</option>)}</select>
              <small>{role === 'KHOA_PHONG' ? 'Quyền truy cập được giới hạn theo khoa/phòng đã chọn.' : 'Các vai trò khác có thể chọn khoa/phòng hoặc để trống.'}</small></label>
            {mode === 'create' && <label>Trạng thái<select value={String(active)} onChange={event => setActive(event.target.value === 'true')}><option value="true">Hoạt động</option><option value="false">Không hoạt động</option></select></label>}
          </div>
        </section><WorkflowError error={error} />
          <div className="form-actions"><Link className="button secondary" to={mode === 'edit' ? `/admin/accounts/${id}` : '/admin/accounts'}>Hủy</Link>
            <button className="button primary" type="submit" disabled={busy}>{busy ? 'Đang lưu…' : mode === 'create' ? 'Tạo tài khoản' : 'Lưu chỉnh sửa'}</button></div>
        </form>}
    <Link className="text-link" to="/admin/accounts">← Danh sách tài khoản</Link>
  </div>
}
