import type { Role } from '../types/auth'

export const roleLabels: Record<Role, string> = {
  PHONG_VTYT: 'Phòng Vật tư Y tế',
  BAN_GIAM_DOC: 'Ban Giám đốc',
  KHOA_PHONG: 'Khoa/Phòng',
  ADMIN: 'Quản trị hệ thống',
}
