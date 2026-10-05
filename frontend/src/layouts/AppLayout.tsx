import { NotificationBell } from '../components/NotificationBell'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { navigationItems } from '../routes/navigation'
import { roleLabels } from '../utils/roleLabels'

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const reportDetail = /^\/plans\/\d+\/report$/.test(location.pathname)
  const executionDetail = /^\/plans\/\d+\/items\/\d+\/execution$/.test(location.pathname)
  if (!user) return null
  const items = navigationItems.filter(item => item.roles.includes(user.role))
  function handleLogout() { logout(); navigate('/login', { replace: true }) }
  return <div className="app-frame">
    <aside className="sidebar" aria-label="Điều hướng chính">
      <Link className="brand-block" to="/dashboard"><span className="brand-mark" aria-hidden="true">+</span><strong>MEDMAINT</strong></Link>
      <nav className="side-nav" aria-label="Chức năng">
        {items.map(item => <NavLink key={item.path} end={item.path === '/dashboard'} to={item.path} className={({ isActive }) => `nav-link${(reportDetail ? item.path === '/reports' : executionDetail ? item.path === (user.role === 'PHONG_VTYT' ? '/maintenance-progress' : '/execution') : isActive) ? ' active' : ''}`} >
          {item.label}
        </NavLink>)}
      </nav>
    </aside>
    <div className="app-column">
      <header className="app-header">
        <div><span className="header-eyebrow">HỆ THỐNG NỘI BỘ</span><strong>Quản lý bảo trì trang thiết bị y tế</strong></div>
        <div className="header-user">{user.role !== 'ADMIN' && <NotificationBell />}<div className="user-identity"><strong>{user.username}</strong><span className="role-badge">{roleLabels[user.role]}</span></div>
          <button className="button secondary logout-button" type="button" onClick={handleLogout}>Đăng xuất</button>
        </div>
      </header>
      <main className="app-content" id="main-content"><Outlet /></main>
    </div>
  </div>
}
