import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './useAuth'
import { LoadingState } from '../components/LoadingState'

export function ProtectedRoute() {
  const auth = useAuth()
  const location = useLocation()
  if (auth.status === 'initializing') return <LoadingState label="Đang xác minh phiên đăng nhập…" />
  if (auth.status === 'unavailable') {
    return <main className="center-screen"><section className="panel narrow-panel">
      <h1>Chưa thể kết nối</h1><p>{auth.message}</p>
      <button className="button primary" onClick={() => void auth.retryRestore()}>Thử lại</button>
    </section></main>
  }
  if (!auth.isAuthenticated) return <Navigate to="/login" state={{ from: location.pathname + location.search + location.hash }} replace />
  return <Outlet />
}
