import type { CoverageEvidence } from '../types/workflow'
export function freeCoverageReason(c: CoverageEvidence, equipmentId: number, date: string): string | null {
 if (c.classification !== 'FREE') return 'Hồ sơ ngoài hợp đồng'
 if (c.equipmentId !== equipmentId) return 'Hồ sơ khác thiết bị'
 if (!date) return 'Nhập ngày dự kiến hoặc ngày bắt đầu kế hoạch'
 if (!c.providerId || !c.providerActive) return 'Đơn vị hợp đồng không hoạt động'
 if (c.verifiedByRole !== 'PHONG_VTYT' || !c.verifiedAt || !c.basisNote?.trim()) return 'Thiếu căn cứ xác minh hợp đồng'
 if ((c.effectiveFrom && date < c.effectiveFrom) || (c.effectiveTo && date > c.effectiveTo)) return 'Hợp đồng không áp dụng vào ngày tham chiếu'
 if (c.warrantyExpiresOn && date > c.warrantyExpiresOn) return 'Đã hết hạn bảo hành vào ngày tham chiếu'
 return null
}
