import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { notificationsApi, notificationsChanged } from '../api/notificationsApi'
import type { UserNotification } from '../types/workflow'
import { dateTime } from '../utils/workflowLabels'
export function NotificationBell() {
 const navigate = useNavigate(); const location = useLocation()
 const [count, setCount] = useState(0); const [open, setOpen] = useState(false)
 const [rows, setRows] = useState<UserNotification[]>([]); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
 const refresh = useCallback(() => { notificationsApi.unread().then(r => { setCount(r.count); setError('') }).catch(() => setError('Không tải được thông báo.')) }, [])
 useEffect(() => { refresh(); const timer = window.setInterval(refresh, 30000); window.addEventListener(notificationsChanged, refresh); return () => { clearInterval(timer); window.removeEventListener(notificationsChanged, refresh) } }, [refresh, location.pathname])
 async function toggle() { setOpen(!open); if (!open) { try { setRows((await notificationsApi.list(0, 5)).content); setError('') } catch { setError('Không tải được thông báo.') } } }
 async function visit(row: UserNotification) { setBusy(true); try { await notificationsApi.read(row.id); setOpen(false); navigate(row.targetUrl.startsWith('/') && !row.targetUrl.startsWith('//') ? row.targetUrl : '/notifications') } catch { setError('Chưa đánh dấu được thông báo.') } finally { setBusy(false) } }
 return <div className="notification-bell"><button type="button" className="button secondary" aria-label={`Thông báo: ${count} chưa đọc`} aria-expanded={open} onClick={() => void toggle()}>🔔 Thông báo {count > 0 && <span className="notification-count">{count}</span>}</button>
 {open && <section className="notification-preview" aria-label="Thông báo mới"><strong>Thông báo</strong>{error && <p role="alert">{error}</p>}{rows.length === 0 && !error && <p>Chưa có thông báo.</p>}{rows.map(row => <button key={row.id} disabled={busy} className={`notification-item ${row.readAt ? '' : 'unread'}`} onClick={() => void visit(row)}><strong>{row.title}</strong><span>{row.message}</span><small>{dateTime(row.createdAt)}</small></button>)}<Link to="/notifications" onClick={() => setOpen(false)}>Xem tất cả</Link></section>}
 </div>
}
