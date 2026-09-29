import { Link, useParams } from 'react-router'
import { RoleGuard } from '../auth/RoleGuard'
import { navigationItems } from '../routes/navigation'

export function PlaceholderPage() {
  const { section } = useParams()
  const item = navigationItems.find(candidate => candidate.path === `/workspace/${section}`)
  if (!item) return <NotFoundPage />
  return <RoleGuard roles={item.roles}><div className="page-stack">
    <div className="page-title-block"><p className="eyebrow">CHỨC NĂNG THEO VAI TRÒ</p><h1>{item.section}</h1></div>
    <section className="panel placeholder-panel"><span className="placeholder-number">4.4</span>
      <h2>Chức năng sẽ được triển khai trong các giai đoạn frontend tiếp theo</h2>
      <p>Khung điều hướng đã sẵn sàng. Chưa có dữ liệu hay thao tác nghiệp vụ trên trang này.</p>
      <Link className="button secondary" to="/">Về tổng quan</Link>
    </section>
  </div></RoleGuard>
}

export function UnauthorizedPage() {
  return <div className="page-stack"><div className="page-title-block"><p className="eyebrow">403 · QUYỀN TRUY CẬP</p><h1>Không có quyền xem trang</h1></div>
    <section className="panel message-panel"><p>Vai trò hiện tại không được mở chức năng này trong giao diện.</p><Link className="button primary" to="/">Về tổng quan</Link></section></div>
}

export function NotFoundPage() {
  return <main className="center-screen"><section className="panel narrow-panel"><p className="eyebrow">404 · KHÔNG TÌM THẤY</p><h1>Không tìm thấy trang</h1>
    <p>Địa chỉ bạn mở không thuộc ứng dụng hiện tại.</p><Link className="button primary" to="/">Về tổng quan</Link></section></main>
}
