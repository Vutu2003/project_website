import type { MaintenanceProgressStatus, ProgressEvidence } from '../types/execution'

export const progressLabels: Record<MaintenanceProgressStatus, string> = {
 IN_PROGRESS: 'Đang bảo trì', WORK_DONE: 'Bảo trì xong', DAMAGE_DETECTED: 'Có hỏng hóc',
}
const legacyLabels: Record<string, MaintenanceProgressStatus> = { 'Đang thực hiện':'IN_PROGRESS','Tạm dừng':'IN_PROGRESS','Chờ linh kiện':'IN_PROGRESS','Chờ đơn vị bảo trì':'IN_PROGRESS','Đã xử lý xong':'WORK_DONE' }
export function progressDetails(log: ProgressEvidence) {
 const [first,...rest]=log.workNote.split('\n')
 const entry=Object.entries(progressLabels).find(([,label])=>label===first)
 const status=entry?.[0] as MaintenanceProgressStatus|undefined ?? legacyLabels[first]
 return {status,label:entry?.[1] ?? (legacyLabels[first]?first:'Cập nhật tiến độ'),note:status?rest.join('\n'):log.workNote}
}

export function orderedProgress(logs: ProgressEvidence[]) {
  return [...logs].sort((a, b) => Date.parse(a.eventAt) - Date.parse(b.eventAt) || a.id - b.id)
}
