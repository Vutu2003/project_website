import { useEffect, useId, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { notificationsApi, notificationsChanged } from '../api/notificationsApi'
import type { UserNotification } from '../types/workflow'
import { dateTime } from '../utils/workflowLabels'
import { UiIcon } from './UiIcon'
import '../notifications.css'
export function NotificationBell() {
 const navigate=useNavigate();const location=useLocation();const panelId=useId()
 const container=useRef<HTMLDivElement>(null);const trigger=useRef<HTMLButtonElement>(null);const request=useRef(0)
 const [count,setCount]=useState(0);const [open,setOpen]=useState(false);const [rows,setRows]=useState<UserNotification[]>([])
 const [error,setError]=useState('');const [countError,setCountError]=useState(false);const [loading,setLoading]=useState(false);const [busy,setBusy]=useState(false);const [revision,setRevision]=useState(0)
 useEffect(()=>{let active=true;const refresh=()=>{notificationsApi.unread().then(r=>{if(active){setCount(r.count);setCountError(false)}}).catch(()=>{if(active)setCountError(true)})};refresh();const timer=window.setInterval(refresh,30000);window.addEventListener(notificationsChanged,refresh);return()=>{active=false;clearInterval(timer);window.removeEventListener(notificationsChanged,refresh)}},[location.pathname])
 useEffect(()=>{if(!open)return;let active=true;setLoading(true);setError('');notificationsApi.list(0,5).then(r=>{if(active){setRows(r.content);setLoading(false)}}).catch(()=>{if(active){setError('Không tải được thông báo. Vui lòng thử lại.');setLoading(false)}});return()=>{active=false}},[open,revision])
 useEffect(()=>{if(!open)return;function outside(event:PointerEvent){if(!container.current?.contains(event.target as Node))setOpen(false)}function key(event:KeyboardEvent){if(event.key==='Escape'){setOpen(false);trigger.current?.focus()}}
  document.addEventListener('pointerdown',outside);document.addEventListener('keydown',key);const changed=()=>setRevision(v=>v+1);window.addEventListener(notificationsChanged,changed)
  return()=>{document.removeEventListener('pointerdown',outside);document.removeEventListener('keydown',key);window.removeEventListener(notificationsChanged,changed)}
 },[open])
 // Discard any late navigation after the bell is unmounted (for example, on logout).
 useEffect(()=>()=>{request.current++},[])
 async function visit(row:UserNotification){if(busy)return;const current=++request.current;setBusy(true);try{await notificationsApi.read(row.id);if(current!==request.current)return;setOpen(false);navigate(row.targetUrl.startsWith('/')&&!row.targetUrl.startsWith('//')?row.targetUrl:'/notifications')}catch{if(current===request.current)setError('Chưa đánh dấu được thông báo. Vui lòng thử lại.')}finally{if(current===request.current)setBusy(false)}}
 function close(){setOpen(false);trigger.current?.focus()}
 return <div className={`notification-bell${count>0?' has-unread':''}`} ref={container}><button ref={trigger} type="button" className={`button secondary notification-trigger${open?' is-open':''}`} aria-label={`Thông báo: ${count} chưa đọc`} aria-expanded={open} aria-controls={open?panelId:undefined} title={countError?'Chưa tải được số thông báo':'Thông báo'} onClick={()=>setOpen(v=>!v)}><span className="notification-icon"><UiIcon name="bell"/>{count>0&&<span className="notification-halo"/>}</span><span>Thông báo</span>{count>0&&<span key={count} className="notification-count">{count>99?'99+':count}</span>}</button>
 {open&&<section id={panelId} className="notification-preview" aria-label="Thông báo mới"><div className="notification-preview-heading"><div><strong>Thông báo</strong><small>{count>0?`${count} thông báo chưa đọc`:'Bạn đã đọc hết thông báo'}</small></div><button className="notification-close" aria-label="Đóng thông báo" onClick={close}><UiIcon name="close"/></button></div><div className="notification-preview-body" aria-busy={loading}>{loading?<div className="notification-loading" role="status"><span className="notification-loading-dot"/>Đang tải thông báo…</div>:<>{error&&<div className="notification-error" role="alert"><p>{error}</p><button onClick={()=>setRevision(v=>v+1)} disabled={busy}>Thử lại</button></div>}{rows.length===0&&!error?<div className="notification-empty"><UiIcon name="check"/><strong>Chưa có thông báo</strong><span>Các cập nhật công việc sẽ xuất hiện tại đây.</span></div>:rows.map((row,i)=><button key={row.id} disabled={busy} style={{animationDelay:`${i*35}ms`}} className={`notification-item ${row.readAt?'':'unread'}`} onClick={()=>void visit(row)}><span className="notification-row-icon"><UiIcon name={row.notificationType==='REPORT_SHARED'?'report':row.notificationType==='PLAN_SUBMITTED'?'contract':'bell'}/></span><span className="notification-row-content"><strong>{row.title}</strong><span>{row.message}</span><small><UiIcon name="clock"/>{dateTime(row.createdAt)}</small></span>{!row.readAt&&<span className="notification-unread-dot" aria-label="Chưa đọc"/>}</button>)}</>}</div><Link className="notification-view-all" to="/notifications" onClick={()=>setOpen(false)}>Xem tất cả <UiIcon name="arrow"/></Link></section>}
 </div>
}
