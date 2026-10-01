import type { Role } from '../types/auth'

export interface NavigationItem {
  path: string
  label: string
  section: string
  roles: Role[]
}

export const navigationItems: NavigationItem[] = [
  { path: '/admin/accounts', label: 'Quản lý tài khoản', section: 'Quản trị', roles: ['ADMIN'] },
  { path: '/admin/catalogs/departments', label: 'Khoa / Phòng', section: 'Danh mục hệ thống', roles: ['ADMIN'] },
  { path: '/admin/catalogs/providers', label: 'Đơn vị bảo trì', section: 'Danh mục hệ thống', roles: ['ADMIN'] },
  { path: '/plans', label: 'Kế hoạch bảo trì', section: 'Kế hoạch bảo trì', roles: ['PHONG_VTYT'] },
  { path: '/execution', label: 'Thực hiện bảo trì', section: 'Thực hiện bảo trì', roles: ['PHONG_VTYT'] },
  { path: '/reports', label: 'Báo cáo', section: 'Báo cáo', roles: ['PHONG_VTYT', 'BAN_GIAM_DOC'] },
  { path: '/equipment', label: 'Thiết bị & lịch sử', section: 'Thiết bị & lịch sử', roles: ['PHONG_VTYT', 'BAN_GIAM_DOC', 'KHOA_PHONG'] },
  { path: '/approvals', label: 'Phê duyệt', section: 'Phê duyệt', roles: ['BAN_GIAM_DOC'] },
  { path: '/execution', label: 'Bàn giao', section: 'Bàn giao', roles: ['KHOA_PHONG'] },
]
