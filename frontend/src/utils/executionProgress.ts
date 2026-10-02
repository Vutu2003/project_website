import type { MaintenanceProgressStatus, ProgressEvidence } from '../types/execution'

export const progressLabels: Record<MaintenanceProgressStatus, string> = {
  IN_PROGRESS: 'Đang thực hiện', PAUSED: 'Tạm dừng', WAITING_PARTS: 'Chờ linh kiện',
  WAITING_PROVIDER: 'Chờ đơn vị bảo trì', WORK_DONE: 'Đã xử lý xong',
}

export function progressDetails(log: ProgressEvidence) {
  const [first, ...rest] = log.workNote.split('\n')
  const entry = Object.entries(progressLabels).find(([, label]) => label === first)
  return { status: entry?.[0] as MaintenanceProgressStatus | undefined,
    label: entry?.[1] ?? 'Cập nhật tiến độ', note: entry ? rest.join('\n') : log.workNote }
}

export function orderedProgress(logs: ProgressEvidence[]) {
  return [...logs].sort((a, b) => Date.parse(a.eventAt) - Date.parse(b.eventAt) || a.id - b.id)
}
