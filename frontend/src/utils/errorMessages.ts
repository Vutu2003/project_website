import { ApiError, NetworkError } from '../api/types'
import { UserInputError } from './UserInputError'

const coverageErrors: Record<string, string> = {
  PLAN_NOT_EDITABLE: 'Kế hoạch đã khóa chỉnh sửa. Chỉ Nháp hoặc Yêu cầu chỉnh sửa được thay đổi.',
  COVERAGE_REQUIRED: 'Vui lòng chọn hồ sơ hợp đồng đã xác minh. Ngoài hợp đồng cần đơn vị đề xuất và căn cứ.',
  COVERAGE_NOT_FOUND: 'Hồ sơ hợp đồng không còn tồn tại. Vui lòng tải lại dữ liệu.',
  COVERAGE_UNVERIFIED: 'Hồ sơ chưa có đầy đủ xác minh và căn cứ của Phòng VTYT.',
  COVERAGE_EQUIPMENT_MISMATCH: 'Hồ sơ hợp đồng không thuộc thiết bị đang chọn.',
  COVERAGE_NOT_APPLICABLE: 'Hồ sơ hợp đồng không có hiệu lực vào ngày bảo trì dự kiến.',
  COVERAGE_PROVIDER_MISSING: 'Hồ sơ theo hợp đồng chưa có đơn vị bảo trì. Cần bổ sung căn cứ hợp lệ.',
  PROVIDER_INACTIVE: 'Đơn vị bảo trì không còn hoạt động. Cần kiểm tra hồ sơ trước khi tiếp tục.',
  PLAN_NOT_ROUTABLE: 'Kế hoạch đã bước sang giai đoạn thực hiện hoặc kết thúc, không thể xác định lại hình thức bảo trì.',
  PLAN_ITEM_STATE_CONFLICT: 'Hạng mục đã thay đổi trạng thái hoặc đã có quyết định hình thức bảo trì. Vui lòng tải lại dữ liệu.',
}

export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'OPTIMISTIC_LOCK_CONFLICT')
      return 'Dữ liệu đã được thay đổi ở phiên khác. Vui lòng tải lại và kiểm tra trước khi tiếp tục.'
    if (error.status === 403) return 'Bạn không có quyền thực hiện thao tác này.'
    if (coverageErrors[error.code]) return coverageErrors[error.code]
    if (error.status === 404) return 'Không tìm thấy dữ liệu yêu cầu. Vui lòng tải lại.'
    return error.message
  }
  if (error instanceof NetworkError || error instanceof UserInputError) return error.message
  return 'Có lỗi xảy ra. Vui lòng thử lại.'
}
