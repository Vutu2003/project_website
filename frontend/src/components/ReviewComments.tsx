import { useEffect, useState } from 'react'
import { apiRequest } from '../api/client'
import { dateTime } from '../utils/workflowLabels'
import { WorkflowError } from './WorkflowFeedback'
interface ReviewComment { id: number; request_id: number; comment: string; action_at: string; reviewer: string }
export function ReviewComments({ planId }: { planId: number }) {
 const [rows, setRows] = useState<ReviewComment[]>([])
 const [error, setError] = useState<unknown>(null)
 useEffect(() => { let active = true; apiRequest<ReviewComment[]>(`/api/plans/${planId}/review-comments`).then(r => { if (active) setRows(r) }).catch(e => { if (active) setError(e) }); return () => { active = false } }, [planId])
 if (error) return <WorkflowError error={error} />
 if (!rows.length) return null
 function render(r: ReviewComment) { return <article key={r.id}><p>Người duyệt: <strong>{r.reviewer}</strong> · {dateTime(r.action_at)} · Yêu cầu #{r.request_id}</p><p style={{ whiteSpace: 'pre-wrap' }}>{r.comment}</p></article> }
 return <section className="panel business-panel retention-note"><h2>Ý kiến Ban Giám đốc</h2><p>Ý kiến yêu cầu chỉnh sửa mới nhất</p>{render(rows[0])}{rows.length > 1 && <details><summary>Ý kiến các lần trước ({rows.length - 1})</summary>{rows.slice(1).map(render)}</details>}</section>
}
