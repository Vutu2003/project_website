import { StatusBadge } from './StatusBadge'
import type { AcceptanceEvidence, ExecutionAttempt } from '../types/execution'
import { orderedAttempts } from '../utils/attempts'
import { acceptanceResultLabels, dateTime } from '../utils/workflowLabels'

export function AttemptHistory({ attempts, currentExecutionId, latestLabel = 'LẦN THỰC HIỆN HIỆN TẠI' }: {
  attempts: ExecutionAttempt[]; currentExecutionId?: number | null; latestLabel?: string
}) {
  const ordered = orderedAttempts(attempts)
  if (ordered.length === 0) return <p className="empty-state">Chưa có lần thực hiện.</p>
  const latestId = currentExecutionId ?? ordered.at(-1)?.executionId
  return <div className="attempt-list">{ordered.map(attempt => <article className="attempt-card" key={attempt.executionId}>
    <div className="attempt-heading"><div><span className="card-label">{attempt.executionId === latestId ? latestLabel : 'LẦN THỰC HIỆN TRƯỚC'}</span><h3>Lần thực hiện {attempt.attemptNo}</h3></div>
      <StatusBadge label={attempt.handoverAcceptance ? acceptanceResultLabels[attempt.handoverAcceptance.result] : attempt.technicalAcceptance ? acceptanceResultLabels[attempt.technicalAcceptance.result] : attempt.endedAt ? 'Đã kết thúc công việc' : 'Đang thực hiện'} tone={attempt.handoverAcceptance?.result === 'PASS' ? 'teal' : attempt.technicalAcceptance?.result === 'FAIL' || attempt.handoverAcceptance?.result === 'FAIL' ? 'amber' : 'neutral'} /></div>
    <dl className="detail-list"><div><dt>Đơn vị thực tế</dt><dd>{attempt.actualProviderName}</dd></div><div><dt>Thời gian</dt><dd>{dateTime(attempt.startedAt)} → {dateTime(attempt.endedAt)}</dd></div>
      {attempt.resultNote && <div><dt>Ghi chú kết quả</dt><dd>{attempt.resultNote}</dd></div>}</dl>
    <div className="attempt-evidence"><div><h4>Tiến độ</h4>{attempt.progress.length ? <ol className="progress-list">{attempt.progress.map(log => <li key={log.id}><span>{dateTime(log.eventAt)} · người ghi #{log.recordedByUserId}</span><p>{log.workNote}</p>{log.damageNote && <p className="warning-text">Hư hỏng: {log.damageNote}</p>}</li>)}</ol> : <p className="muted">Chưa có cập nhật tiến độ.</p>}</div>
      <div><h4>Nghiệm thu kỹ thuật</h4>{attempt.technicalAcceptance ? <Evidence record={attempt.technicalAcceptance} /> : <p className="muted">Chưa có kết quả.</p>}</div>
      <div><h4>Bàn giao</h4>{attempt.handoverAcceptance ? <Evidence record={attempt.handoverAcceptance} /> : <p className="muted">Chưa có kết quả.</p>}</div></div>
  </article>)}</div>
}

function Evidence({ record }: { record: AcceptanceEvidence }) {
  return <div className="evidence-card"><StatusBadge label={acceptanceResultLabels[record.result]} tone={record.result === 'PASS' ? 'teal' : 'amber'} />
    <p>{record.conclusion}</p><small>{dateTime(record.observedAt)} · người ghi #{record.recordedByUserId}</small>
    {record.departmentSignerId && <small>Khoa/Phòng #{record.departmentSignerId} xác nhận: {dateTime(record.departmentConfirmedAt)}</small>}
    {record.vtytSignerId && <small>Phòng VTYT #{record.vtytSignerId} xác nhận: {dateTime(record.vtytConfirmedAt)}</small>}</div>
}
