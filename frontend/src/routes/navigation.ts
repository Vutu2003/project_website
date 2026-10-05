import type { Role } from '../types/auth'

export interface NavigationItem {
  path: string
  label: string
  section: string
  roles: Role[]
}

export const navigationItems: NavigationItem[] = [
  { path: '/dashboard', label: 'Tổng quan', section: 'Tổng quan', roles: ['PHONG_VTYT', 'BAN_GIAM_DOC', 'KHOA_PHONG', 'ADMIN'] },
  { path: '/contracts', label: 'Danh sách hợp đồng', section: 'Danh mục', roles: ['PHONG_VTYT'] },
  { path: '/equipment', label: 'Danh sách thiết bị', section: 'Thiết bị', roles: ['PHONG_VTYT'] },
  { path: '/plans', label: 'Kế hoạch bảo trì', section: 'Kế hoạch', roles: ['PHONG_VTYT'] },
  { path: '/maintenance-progress', label: 'Theo dõi tiến độ bảo trì', section: 'Tiến độ', roles: ['PHONG_VTYT'] },
  { path: '/reports', label: 'Báo cáo bảo trì', section: 'Báo cáo', roles: ['PHONG_VTYT'] },
  { path: '/approvals', label: 'Phê duyệt', section: 'Phê duyệt', roles: ['BAN_GIAM_DOC'] },
  { path: '/reports', label: 'Báo cáo', section: 'Báo cáo', roles: ['BAN_GIAM_DOC'] },
  { path: '/admin/accounts', label: 'Quản lý tài khoản', section: 'Quản trị', roles: ['ADMIN'] },
  { path: '/admin/equipment', label: 'Thiết bị', section: 'Danh mục hệ thống', roles: ['ADMIN'] },
  { path: '/admin/catalogs/departments', label: 'Khoa / Phòng', section: 'Danh mục hệ thống', roles: ['ADMIN'] },
  { path: '/admin/catalogs/providers', label: 'Đơn vị bảo trì', section: 'Danh mục hệ thống', roles: ['ADMIN'] },
  { path: '/contracts', label: 'Hợp đồng', section: 'Danh mục hệ thống', roles: ['ADMIN'] },
  { path: '/execution', label: 'Bàn giao', section: 'Bàn giao', roles: ['KHOA_PHONG'] },
  { path: '/equipment', label: 'Thiết bị & lịch sử', section: 'Thiết bị & lịch sử', roles: ['KHOA_PHONG'] },
  { path: '/reports', label: 'Báo cáo đã nhận', section: 'Báo cáo', roles: ['KHOA_PHONG'] },
]
