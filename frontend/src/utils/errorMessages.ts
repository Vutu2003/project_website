import { ApiError, NetworkError } from '../api/types'
import { UserInputError } from './UserInputError'

export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'OPTIMISTIC_LOCK_CONFLICT')
      return 'Dữ liệu đã được thay đổi ở phiên khác. Vui lòng tải lại và kiểm tra trước khi tiếp tục.'
    if (error.status === 403) return 'Bạn không có quyền thực hiện thao tác này.'
    if (error.status === 404) return 'Không tìm thấy dữ liệu yêu cầu. Vui lòng tải lại.'
    return error.message
  }
  if (error instanceof NetworkError || error instanceof UserInputError) return error.message
  return 'Có lỗi xảy ra. Vui lòng thử lại.'
}
