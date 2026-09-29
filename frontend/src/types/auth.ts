export const roles = ['PHONG_VTYT', 'BAN_GIAM_DOC', 'KHOA_PHONG', 'ADMIN'] as const
export type Role = (typeof roles)[number]

export interface AuthenticatedUser {
  id: number
  username: string
  role: Role
  departmentId: number | null
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: AuthenticatedUser
}
