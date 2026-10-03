import type { WarrantyInfo, WarrantyStatus } from '../types/workflow'
export function businessToday(): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date())
  const part = (name: string) => parts.find(p => p.type === name)?.value
  return `${part('year')}-${part('month')}-${part('day')}`
}
export const warrantyLabels: Record<WarrantyStatus, string> = {
  ACTIVE: 'Còn bảo hành', EXPIRED: 'Hết bảo hành', NOT_STARTED: 'Chưa bắt đầu', UNKNOWN: 'Chưa rõ thời hạn',
}
export const serviceChoiceLabels = { MANUFACTURER: 'Liên hệ nhà sản xuất', EXTERNAL: 'Bảo hành ngoài' }
export function warrantyAt(deadline?: string | null, start?: string | null, referenceDate?: string): WarrantyStatus {
  if (!deadline || !referenceDate) return 'UNKNOWN'
  if (start && referenceDate < start) return 'NOT_STARTED'
  return referenceDate > deadline ? 'EXPIRED' : 'ACTIVE'
}
export function warrantyContract(info: WarrantyInfo, date: string, coverageId?: number | null) {
  if (coverageId) return info.contracts.find(c => c.id === coverageId) || null
  const ordered = [...info.contracts].sort((a, b) => (b.effectiveFrom || '').localeCompare(a.effectiveFrom || '') || b.id - a.id)
  const started = ordered.filter(c => !c.effectiveFrom || c.effectiveFrom <= date)
  return started.find(c => warrantyAt(c.warrantyExpiresOn, c.effectiveFrom, date) === 'ACTIVE') || started[0] || ordered[ordered.length - 1] || null
}
