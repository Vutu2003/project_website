import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { approvalsApi } from '../api/approvalsApi'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { WorkflowError, WorkflowSuccess } from '../components/WorkflowFeedback'
import type { ApprovalQueueItem, PageResponse } from '../types/workflow'
import { approvalTypeLabels, dateTime } from '../utils/workflowLabels'

export function ApprovalQueuePage() {
  const location = useLocation()
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<ApprovalQueueItem> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const flash = (location.state as { flash?: string } | null)?.flash || null
  useEffect(() => {
    let active = true
    approvalsApi.pending(page, 10, 'PLAN_APPROVAL').then(result => {
      if (active) { setData(result); setError(null); setLoading(false) }
    }).catch(failure => { if (active) { setError(failure); setLoading(false) } })
    return () => { active = false }
  }, [page, reloadKey])
  return <div className="page-stack"><div className="page-title-block"><p className="eyebrow">BAN GIÁM ĐỐC</p><h1>Hàng chờ phê duyệt</h1>
    <p>Phê duyệt kế hoạch bao gồm các đơn vị bảo trì đã đề xuất trong kế hoạch.</p></div>
    <WorkflowSuccess message={flash} />
    <section className="panel business-panel"><div className="panel-heading"><div><h2>Yêu cầu chờ quyết định</h2><p>Chọn một yêu cầu để xem đủ bối cảnh trước khi quyết định.</p></div></div>
      {loading ? <p className="muted">Đang tải hàng chờ…</p> : error ? <WorkflowError error={error} onReload={() => { setLoading(true); setReloadKey(value => value + 1) }} /> : data && data.content.length === 0 ? <p className="empty-state">Không có yêu cầu nào đang chờ trong bộ lọc này.</p> : data && <>
        <div className="table-scroll"><table className="data-table"><thead><tr><th>Kế hoạch</th><th>Quý / năm</th><th>Số thiết bị</th><th>Người gửi</th><th>Thời điểm</th><th>Trạng thái</th><th></th></tr></thead>
          <tbody>{data.content.map(request => <tr key={request.id}><td>{request.planTitle || (request.requestType === 'VENDOR_SELECTION' ? request.equipmentCode : `Kế hoạch #${request.planId}`)}<span className="row-sub">{approvalTypeLabels[request.requestType]}{request.requestType === 'VENDOR_SELECTION' && ` · ${request.equipmentCode}`}</span></td><td>{request.planQuarter || '—'} / {request.planYear || '—'}</td><td>{request.equipmentCount ?? '—'}</td>
            <td>{request.createdByName}</td><td>{dateTime(request.submittedAt)}</td><td><StatusBadge label="Chờ quyết định" tone="amber" /></td>
            <td><Link className="table-link" to={`/approvals/${request.id}`}>Xem & quyết định</Link></td></tr>)}</tbody></table></div>
        <Pagination data={data} onPage={next => { setPage(next); setLoading(true) }} />
      </>}
    </section>
  </div>
}
