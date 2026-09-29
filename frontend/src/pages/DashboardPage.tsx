import { useAuth } from '../auth/useAuth'
import { roleLabels } from '../utils/roleLabels'

export function DashboardPage() {
  const { user } = useAuth()
  if (!user) return null
  return <div className="page-stack">
    <div className="page-title-block"><p className="eyebrow">TỔNG QUAN</p><h1>Xin chào, {user.username}</h1>
      <p>Phiên làm việc đã được xác thực với hệ thống backend.</p></div>
    <section className="overview-grid" aria-label="Thông tin phiên làm việc">
      <article className="panel overview-card"><span className="card-label">TÀI KHOẢN HIỆN TẠI</span><strong>{user.username}</strong><p>Thông tin được xác minh qua /api/auth/me.</p></article>
      <article className="panel overview-card"><span className="card-label">VAI TRÒ</span><strong>{roleLabels[user.role]}</strong><p>Mã vai trò: {user.role}</p></article>
      <article className="panel overview-card"><span className="card-label">PHẠM VI KHOA/PHÒNG</span><strong>{user.departmentId == null ? 'Toàn hệ thống' : `Mã khoa/phòng ${user.departmentId}`}</strong><p>Quyền truy cập thực tế do backend kiểm soát.</p></article>
    </section>
    <section className="panel next-step"><span className="step-accent" aria-hidden="true" /><div><h2>Sẵn sàng cho luồng nghiệp vụ</h2>
      <p>Kế hoạch, phê duyệt, thực hiện, nghiệm thu, bàn giao, báo cáo và lịch sử thiết bị đã có thể sử dụng theo vai trò trên dữ liệu backend.</p></div></section>
  </div>
}
