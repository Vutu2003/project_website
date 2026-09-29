import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { historyApi } from '../api/historyApi'
import { useAuth } from '../auth/useAuth'
import { AttemptHistory } from '../components/AttemptHistory'
import { StatusBadge } from '../components/StatusBadge'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { EquipmentExecutionHistory, StateEvent } from '../types/execution'
import { UserInputError } from '../utils/UserInputError'
import { assignmentRouteLabels, businessDate, dateTime, itemStatusLabels, planStatusLabels } from '../utils/workflowLabels'

function StateTimeline({ title, events, kind }: { title: string; events: StateEvent[]; kind: 'item' | 'plan' }) {
  const labels: Record<string, string> = kind === 'item' ? itemStatusLabels : planStatusLabels
  const state = (value: string) => labels[value] ?? value
  return <div className="history-events"><h4>{title}</h4>{events.length ? <ol>{events.map(event => <li key={event.id}>
    <strong>{event.oldState ? `${state(event.oldState)} → ` : ''}{state(event.newState)}</strong>
    <span>{dateTime(event.at)} · {event.action} · người thực hiện #{event.actorUserId}</span>
    {event.reason && <p>Lý do: {event.reason}</p>}
  </li>)}</ol> : <p className="muted">Không có sự kiện trạng thái.</p>}</div>
}
export function EquipmentHistoryPage() {
  const id = Number(useParams().equipmentId)
  const { user } = useAuth()
  const [history, setHistory] = useState<EquipmentExecutionHistory | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [refresh, setRefresh] = useState(0)
  const reload = useCallback(() => { setLoading(true); setRefresh(value => value + 1) }, [])
  useEffect(() => {
    if (!Number.isInteger(id) || id <= 0) { setError(new UserInputError('Đường dẫn thiết bị không hợp lệ.')); setLoading(false); return }
    let active = true
    historyApi.get(id).then(rows => { if (active) { setHistory(rows); setError(null); setLoading(false) } })
      .catch(failure => { if (active) { setHistory(null); setError(failure); setLoading(false) } })
    return () => { active = false }
  }, [id, refresh])
  if (loading && !history) return <p className="muted">Đang tải lịch sử thiết bị…</p>
  if (!history) return <div className="page-stack"><WorkflowError error={error} onReload={reload} /><Link to="/equipment">Về danh sách thiết bị</Link></div>
  return <div className="page-stack history-page"><div className="page-title-row"><div className="page-title-block"><p className="eyebrow">UC12 · LỊCH SỬ THIẾT BỊ</p>
    <h1>{history.equipmentCode} · {history.equipmentName}</h1><p>Khoa hiện tại #{history.currentDepartmentId ?? '—'} · {history.campaigns.length} đợt bảo trì trong phạm vi được phép.</p></div>
    <div className="header-actions"><button className="button secondary" type="button" onClick={reload}>Tải lại</button><Link className="button secondary" to="/equipment">Về thiết bị</Link></div></div>
    <WorkflowError error={error} onReload={reload} />
    {history.campaigns.length === 0 ? <p className="empty-state">Thiết bị chưa có đợt bảo trì trong phạm vi được phép.</p> :
      <section className="campaign-list" aria-label="Các đợt bảo trì">{history.campaigns.map((campaign, index) => <article className="panel business-panel campaign-card" key={campaign.itemId}>
        <div className="campaign-heading"><div><span className="card-label">ĐỢT BẢO TRÌ {index + 1} · KẾ HOẠCH #{campaign.planId}</span><h2>{campaign.planTitle}</h2>
          <p>{businessDate(campaign.periodStart)} – {businessDate(campaign.periodEnd)} · Khoa tại kế hoạch #{campaign.departmentIdAtPlan}</p></div>
          <StatusBadge label={itemStatusLabels[campaign.itemStatus]} tone={campaign.itemStatus === 'COMPLETED' ? 'teal' : campaign.itemStatus === 'REPAIR_REQUIRED' ? 'amber' : 'neutral'} /></div>
        <dl className="detail-list"><div><dt>Trạng thái kế hoạch</dt><dd>{planStatusLabels[campaign.planStatus]}</dd></div>
          <div><dt>Tuyến bảo trì</dt><dd>{campaign.assignmentRoute ? assignmentRouteLabels[campaign.assignmentRoute] : 'Chưa phân tuyến'}</dd></div>
          <div><dt>Đơn vị được phân công</dt><dd>{campaign.assignedProviderId ? `#${campaign.assignedProviderId}` : '—'} · Đơn vị thực tế ghi theo từng lần bên dưới</dd></div>
          <div><dt>Hạng mục / coverage</dt><dd>#{campaign.itemId} / {campaign.coverageId ? `#${campaign.coverageId}` : '—'}</dd></div></dl>
        {campaign.itemStatus === 'REPAIR_REQUIRED' && <p className="retention-note">Chuyển sửa chữa là điểm bàn giao cuối của bảo trì V1; không có kết quả sửa chữa V2 trong lịch sử này.</p>}
        {campaign.report && <div className="history-report"><strong>Báo cáo: {campaign.report.status === 'FINAL' ? 'Chính thức' : 'Bản nháp'}</strong>
          <span>Ngày {businessDate(campaign.report.reportDate)} · Hoàn tất {dateTime(campaign.report.finalizedAt)}</span>
          {(user?.role === 'PHONG_VTYT' || user?.role === 'BAN_GIAM_DOC') && <Link className="text-link" to={`/plans/${campaign.planId}/report`}>Mở báo cáo</Link>}</div>}
        <details className="history-details" open={index === 0}><summary>Lần thực hiện ({campaign.attempts.length}) và bằng chứng</summary>
          <AttemptHistory attempts={campaign.attempts} latestLabel="LẦN THỰC HIỆN GẦN NHẤT" /></details>
        <details className="history-details"><summary>Dòng trạng thái hạng mục ({campaign.itemHistory.length}) và kế hoạch ({campaign.planHistory.length})</summary>
          <div className="history-timelines"><StateTimeline title="Hạng mục" events={campaign.itemHistory} kind="item" /><StateTimeline title="Kế hoạch" events={campaign.planHistory} kind="plan" /></div></details>
      </article>)}</section>}
  </div>
}
