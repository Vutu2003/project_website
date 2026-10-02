import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { notificationsApi } from '../api/notificationsApi'
import { WorkflowError } from '../components/WorkflowFeedback'
import { Pagination } from '../components/Pagination'
import type { PageResponse, UserNotification } from '../types/workflow'
import { dateTime } from '../utils/workflowLabels'
export function NotificationsPage() {
 const navigate = useNavigate(); const [data, setData] = useState<PageResponse<UserNotification> | null>(null)
 const [page, setPage] = useState(0); const [reload, setReload] = useState(0); const [error, setError] = useState<unknown>(null); const [busy, setBusy] = useState(false)
 useEffect(() => { let active = true; notificationsApi.list(page).then(r => { if (active) { setData(r); setError(null) } }).catch(e => { if (active) setError(e) }); return () => { active = false } }, [page, reload])
 async function all() { setBusy(true); try { await notificationsApi.readAll(); setReload(v => v + 1) } catch (e) { setError(e) } finally { setBusy(false) } }
 async function visit(row: UserNotification) { setBusy(true); try { await notificationsApi.read(row.id); navigate(row.targetUrl.startsWith('/') && !row.targetUrl.startsWith('//') ? row.targetUrl : '/notifications') } catch (e) { setError(e) } finally { setBusy(false) } }
 return <div className="page-stack"><div className="page-title-row"><h1>Thông báo</h1><div className="header-actions"><button className="button secondary" disabled={busy} onClick={() => void all()}>Đánh dấu tất cả đã đọc</button><button className="button secondary" onClick={() => setReload(v => v + 1)}>Tải lại</button></div></div><WorkflowError error={error} onReload={() => setReload(v => v + 1)} />
 {!data && !error ? <p>Đang tải thông báo…</p> : data?.content.length === 0 ? <p className="empty-state">Chưa có thông báo.</p> : data && <section className="panel business-panel">{data.content.map(row => <button className={`notification-item ${row.readAt ? '' : 'unread'}`} disabled={busy} key={row.id} onClick={() => void visit(row)}><strong>{!row.readAt && '● '}{row.title}</strong><span>{row.message}</span><small>{dateTime(row.createdAt)}</small></button>)}<Pagination data={data} onPage={setPage} /></section>}
 </div>
}
