import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { ApiError, NetworkError } from '../api/types'
import { LoadingState } from '../components/LoadingState'
import { PasswordInput } from '../components/PasswordInput'

export function LoginPage() {
  const { status, login, message } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  if (status === 'initializing') return <LoadingState label="Đang xác minh phiên đăng nhập…" />
  const destination = typeof location.state?.from === 'string' && location.state.from.startsWith('/') && !location.state.from.startsWith('//')
    ? location.state.from : '/dashboard'
  if (status === 'authenticated') return <Navigate to={destination} replace />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!username.trim() || !password) { setError('Vui lòng nhập tên đăng nhập và mật khẩu.'); return }
    setBusy(true); setError(null)
    try {
      await login({ username: username.trim(), password })
      navigate(destination, { replace: true })
    } catch (failure) {
      if (failure instanceof NetworkError) setError(failure.message)
      else if (failure instanceof ApiError && failure.status === 401) setError('Tên đăng nhập hoặc mật khẩu không đúng.')
      else if (failure instanceof ApiError) setError(failure.message)
      else setError('Đăng nhập không thành công. Vui lòng thử lại.')
    } finally { setBusy(false) }
  }

  return <main className="login-screen"><section className="login-intro">
    <div className="login-intro-inner"><div className="intro-mark" aria-hidden="true">+</div>
      <p className="intro-overline">MEDICAL EQUIPMENT MAINTENANCE</p>
      <h1>Quản lý bảo trì<br />trang thiết bị y tế</h1>
      <p className="intro-copy">Không gian làm việc thống nhất cho hoạt động bảo trì, phê duyệt và theo dõi thiết bị.</p>
      <div className="intro-rule" /><p className="intro-foot">Hệ thống nội bộ · Phiên bản trình bày</p>
    </div>
  </section><section className="login-form-area"><div className="login-form-wrap">
    <p className="eyebrow">CHÀO MỪNG TRỞ LẠI</p><h2>Đăng nhập</h2>
    <p className="muted">Sử dụng tài khoản demo được cấp để truy cập hệ thống.</p>
    <form onSubmit={event => void submit(event)} noValidate>
      <label htmlFor="username">Tên đăng nhập</label>
      <input id="username" name="username" autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} required autoFocus placeholder="Nhập tên đăng nhập" />
      <PasswordInput label="Mật khẩu" id="password" name="password" autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} required placeholder="Nhập mật khẩu" />
      {(error || message) && <div className="form-error" role="alert">{error || message}</div>}
      <button className="button primary login-button" type="submit" disabled={busy}>{busy ? 'Đang đăng nhập…' : 'Đăng nhập'}</button>
    </form>
    <p className="login-support">Nếu chưa có tài khoản, liên hệ quản trị viên của đơn vị.</p>
  </div></section></main>
}
