import type { ExecutionAttempt, StateEvent } from '../types/execution'
import { dateTime } from '../utils/workflowLabels'
import { orderedProgress, progressDetails } from '../utils/executionProgress'

export function ExecutionTimeline({ attempt, history }: { attempt: ExecutionAttempt; history: StateEvent[] }) {
  function actorAt(at: string, actions: string[]) {
    return history.find(event => Date.parse(event.at) === Date.parse(at) && actions.includes(event.action))?.actorUserId
  }
  const rows = [{ key: 'start', at: attempt.startedAt, label: 'Bắt đầu bảo trì', note: '',
    actor: actorAt(attempt.startedAt, ['START_MAINTENANCE', 'START_REWORK']) },
    ...orderedProgress(attempt.progress).map(log => ({ key: `progress-${log.id}`, at: log.eventAt,
      ...progressDetails(log), actor: log.recordedByUserId }))]
  if (attempt.endedAt) rows.push({ key: 'finish', at: attempt.endedAt,
    label: history.some(event => event.action === 'REPAIR_HANDOFF' && Date.parse(event.at) === Date.parse(attempt.endedAt!)) ? 'Chuyển sửa chữa' : 'Hoàn thành kỹ thuật',
    note: attempt.resultNote ?? '', actor: actorAt(attempt.endedAt, ['FINISH_MAINTENANCE', 'REPAIR_HANDOFF']) })
  return <ol className="progress-list" aria-label="Timeline tiến độ">{rows.map(row => <li key={row.key}>
    <span>{dateTime(row.at)} · {row.actor ? `Người cập nhật #${row.actor}` : 'Phòng VTYT'}</span>
    <strong>{row.label}</strong>{row.note && <p style={{ whiteSpace: 'pre-wrap' }}>{row.note}</p>}
  </li>)}</ol>
}
