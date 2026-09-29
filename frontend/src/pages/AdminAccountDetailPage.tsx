import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { adminAccountsApi } from '../api/adminAccountsApi'
import { WorkflowError, WorkflowSuccess } from '../components/WorkflowFeedback'
import { StatusBadge } from '../components/StatusBadge'
import { PasswordInput } from '../components/PasswordInput'
import type { AccountDetail } from '../types/account'
import { roleLabels } from '../utils/roleLabels'
import { UserInputError } from '../utils/UserInputError'

export function AdminAccountDetailPage() {
  const id = Number(useParams().id)
  const location = useLocation()
  const [account, setAccount] = useState<AccountDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [notice, setNotice] = useState<string | null>((location.state as { flash?: string } | null)?.flash ?? null)
  const [refresh, setRefresh] = useState(0)
  const [resetOpen, setResetOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  useEffect(() => {
    let current = true
    if (!Number.isInteger(id) || id <= 0) return () => { current = false }
    adminAccountsApi.detail(id).then(result => { if (current) { setAccount(result); setError(null); setLoading(false) } })
      .catch(failure => { if (current) { setAccount(null); setError(failure); setLoading(false) } })
    return () => { current = false }
  }, [id, refresh])
  function clearPasswords() { setPassword(''); setConfirm('') }
  function reload() { clearPasswords(); setResetOpen(false); setLoading(true); setRefresh(value => value + 1) }
  async function changeStatus() {
    if (!account || busy) return
    const verb = account.active ? 'vô hiệu hóa' : 'kích hoạt'
    if (!window.confirm(`Xác nhận ${verb} tài khoản ${account.username}?`)) return
    setBusy(true); setError(null); setNotice(null)
    try {
      const result = account.active ? await adminAccountsApi.deactivate(id) : await adminAccountsApi.activate(id)
      setAccount(result); setNotice(result.active ? 'Đã kích hoạt tài khoản.' : 'Đã vô hiệu hóa tài khoản. Các request tiếp theo của tài khoản này sẽ bị từ chối.')
    } catch (failure) { setError(failure) } finally { setBusy(false) }
  }
  async function reset(event: FormEvent) {
    event.preventDefault(); setError(null); setNotice(null)
    if (!password.trim() || !confirm.trim()) { setError(new UserInputError('Vui lòng nhập và xác nhận mật khẩu mới.')); return }
    if (password !== confirm) { setError(new UserInputError('Xác nhận mật khẩu mới không khớp.')); return }
    if (busy || !window.confirm(`Đặt lại mật khẩu cho ${account?.username}?`)) return
    setBusy(true)
    try {
      await adminAccountsApi.resetPassword(id, { newPassword: password })
      clearPasswords(); setResetOpen(false); setNotice('Đã đặt lại mật khẩu. Người dùng đăng nhập bằng mật khẩu mới đã cấp.')
    } catch (failure) { clearPasswords(); setError(failure) } finally { setBusy(false) }
  }
  const validId = Number.isInteger(id) && id > 0
  return <div className="page-stack account-page">
    <div className="page-title-row"><div className="page-title-block"><p className="eyebrow">ADMIN · TÀI KHOẢN #{validId ? id : '—'}</p><h1>Chi tiết tài khoản</h1><p>Quản lý quyền truy cập và thông tin đăng nhập của tài khoản.</p></div>
      <Link className="button secondary" to="/admin/accounts">Về danh sách</Link></div>
    <WorkflowSuccess message={notice} /><WorkflowError error={error} onReload={reload} />
    {!validId ? <p className="workflow-error" role="alert">Đường dẫn tài khoản không hợp lệ.</p> : loading ? <p className="muted" role="status">Đang tải tài khoản…</p> : account && <>
      <section className="panel business-panel"><div className="panel-heading"><h2>{account.username}</h2><StatusBadge label={account.active ? 'Hoạt động' : 'Không hoạt động'} tone={account.active ? 'teal' : 'amber'} /></div>
        <dl className="detail-list"><div><dt>Tên đăng nhập</dt><dd>{account.username}</dd></div><div><dt>Vai trò</dt><dd>{roleLabels[account.role]}</dd></div>
          <div><dt>Khoa/Phòng</dt><dd>{account.departmentName || 'Không có khoa/phòng'}</dd></div><div><dt>Trạng thái</dt><dd>{account.active ? 'Hoạt động' : 'Không hoạt động'}</dd></div></dl>
        <div className="header-actions account-detail-actions"><Link className="button secondary" to={`/admin/accounts/${id}/edit`}>Chỉnh sửa</Link>
          <button className="button secondary" type="button" disabled={busy} onClick={() => void changeStatus()}>{busy ? 'Đang xử lý…' : account.active ? 'Vô hiệu hóa' : 'Kích hoạt'}</button>
          <button className="button secondary" type="button" disabled={busy} onClick={() => { clearPasswords(); setError(null); setResetOpen(value => !value) }}>Đặt lại mật khẩu</button>
          <button className="button secondary" type="button" disabled={busy} onClick={reload}>Tải lại</button></div>
      </section>
      {resetOpen && <form className="panel business-panel" onSubmit={reset} noValidate><div className="panel-heading"><h2>Đặt lại mật khẩu</h2></div>
        <p className="muted">ADMIN cấp mật khẩu trực tiếp, không cần mật khẩu cũ. Không để trống; tối đa 72 byte UTF-8.</p>
        <div className="form-grid account-form-grid"><PasswordInput label="Mật khẩu mới" autoComplete="new-password" value={password} onChange={event => setPassword(event.target.value)} required />
          <PasswordInput label="Xác nhận mật khẩu mới" autoComplete="new-password" value={confirm} onChange={event => setConfirm(event.target.value)} required /></div>
        <div className="form-actions account-detail-actions"><button className="button secondary" type="button" disabled={busy} onClick={() => { clearPasswords(); setResetOpen(false) }}>Hủy</button>
          <button className="button primary" type="submit" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu mật khẩu mới'}</button></div></form>}
    </>}
  </div>
}
