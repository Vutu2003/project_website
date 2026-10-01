import { Fragment } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
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
      <div className="brand-block"><span className="brand-mark" aria-hidden="true">+</span><div>
        <strong>MEDMAINT</strong><small>QUẢN LÝ BẢO TRÌ THIẾT BỊ Y TẾ</small>
      </div></div>
      <nav className="side-nav" aria-label="Chức năng">
        <span className="nav-heading">KHÔNG GIAN LÀM VIỆC</span>
        <NavLink end to="/" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>Tổng quan</NavLink>
        {items.length > 0 && <span className="nav-heading nav-heading-gap">CHỨC NĂNG THEO VAI TRÒ</span>}
        {items.map(item => <Fragment key={item.path}>
          {user.role === 'ADMIN' && item.path === '/admin/accounts' && <span className="nav-heading nav-heading-gap">QUẢN TRỊ</span>}
          {user.role === 'ADMIN' && item.path === '/admin/catalogs/departments' && <span className="nav-heading nav-heading-gap">DANH MỤC HỆ THỐNG</span>}
          <NavLink to={item.path} className={({ isActive }) => `nav-link${(reportDetail ? item.path === '/reports' : executionDetail ? item.path === '/execution' : isActive) ? ' active' : ''}`} >
          {item.label}
        </NavLink></Fragment>)}
      </nav>
      <div className="sidebar-foot">Quy trình bảo trì V1 · Phase 4.4</div>
    </aside>
    <div className="app-column">
      <header className="app-header">
        <div><span className="header-eyebrow">HỆ THỐNG NỘI BỘ</span><strong>Quản lý bảo trì trang thiết bị y tế</strong></div>
        <div className="header-user"><div className="user-identity"><strong>{user.username}</strong><span className="role-badge">{roleLabels[user.role]}</span></div>
          <button className="button secondary logout-button" type="button" onClick={handleLogout}>Đăng xuất</button>
        </div>
      </header>
      <main className="app-content" id="main-content"><Outlet /></main>
    </div>
  </div>
}
