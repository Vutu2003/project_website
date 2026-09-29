import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { approvalsApi } from '../api/approvalsApi'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { WorkflowError, WorkflowSuccess } from '../components/WorkflowFeedback'
import type { ApprovalQueueItem, ApprovalRequestType, PageResponse } from '../types/workflow'
import { approvalTypeLabels, dateTime } from '../utils/workflowLabels'

export function ApprovalQueuePage() {
  const location = useLocation()
  const [page, setPage] = useState(0)
  const [requestType, setRequestType] = useState<ApprovalRequestType | ''>('')
  const [data, setData] = useState<PageResponse<ApprovalQueueItem> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const flash = (location.state as { flash?: string } | null)?.flash || null
  useEffect(() => {
    let active = true
    approvalsApi.pending(page, 10, requestType || undefined).then(result => {
      if (active) { setData(result); setError(null); setLoading(false) }
    }).catch(failure => { if (active) { setError(failure); setLoading(false) } })
    return () => { active = false }
  }, [page, requestType, reloadKey])
  return <div className="page-stack"><div className="page-title-block"><p className="eyebrow">BAN GIÁM ĐỐC · UC04 / UC07</p><h1>Hàng chờ phê duyệt</h1>
    <p>Chỉ các yêu cầu còn PENDING từ backend được hiển thị.</p></div>
    <WorkflowSuccess message={flash} />
    <section className="panel business-panel"><div className="panel-heading"><div><h2>Yêu cầu chờ quyết định</h2><p>Chọn một yêu cầu để xem đủ bối cảnh trước khi quyết định.</p></div>
      <label className="inline-filter">Loại yêu cầu <select value={requestType} onChange={event => { setRequestType(event.target.value as ApprovalRequestType | ''); setPage(0); setLoading(true) }}>
        <option value="">Tất cả</option><option value="PLAN_APPROVAL">Duyệt kế hoạch</option><option value="VENDOR_SELECTION">Duyệt đơn vị bảo trì</option>
      </select></label></div>
      {loading ? <p className="muted">Đang tải hàng chờ…</p> : error ? <WorkflowError error={error} onReload={() => { setLoading(true); setReloadKey(value => value + 1) }} /> : data && data.content.length === 0 ? <p className="empty-state">Không có yêu cầu nào đang chờ trong bộ lọc này.</p> : data && <>
        <div className="table-scroll"><table className="data-table"><thead><tr><th>Loại</th><th>Đối tượng</th><th>Người gửi</th><th>Thời điểm</th><th>Trạng thái</th><th></th></tr></thead>
          <tbody>{data.content.map(request => <tr key={request.id}><td>{approvalTypeLabels[request.requestType]}<span className="row-sub">Yêu cầu #{request.id}</span></td>
            <td>{request.requestType === 'PLAN_APPROVAL' ? request.planTitle : request.equipmentCode}<span className="row-sub">{request.requestType === 'VENDOR_SELECTION' ? request.proposedProviderName || 'Chưa có đơn vị' : `Kế hoạch #${request.planId}`}</span></td>
            <td>{request.createdByName}</td><td>{dateTime(request.submittedAt)}</td><td><StatusBadge label="Chờ quyết định" tone="amber" /></td>
            <td><Link className="table-link" to={`/approvals/${request.id}`}>Xem & quyết định</Link></td></tr>)}</tbody></table></div>
        <Pagination data={data} onPage={next => { setPage(next); setLoading(true) }} />
      </>}
    </section>
  </div>
}
