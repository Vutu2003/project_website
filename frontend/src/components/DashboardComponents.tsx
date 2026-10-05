import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { StatusBadge } from './StatusBadge'
import { UiIcon } from './UiIcon'
import type { IconName } from './UiIcon'
import type { AttentionItem, DashboardAction, DashboardItem, DashboardReport } from '../types/dashboard'
import { businessDate, dateTime, itemStatusLabels } from '../utils/workflowLabels'
import { progressLabels } from '../utils/executionProgress'
import type { MaintenanceProgressStatus } from '../types/execution'
import type { PlanItemStatus } from '../types/workflow'
export function DashboardHeader({ title, context, action, generatedAt }: { title: string; context: string; action?: DashboardAction; generatedAt: string }) {
 return <header className="dashboard-header"><div><div className="dashboard-heading-meta"><span className="dashboard-overline"><span/>TỔNG QUAN CÔNG VIỆC</span><span className="dashboard-date"><UiIcon name="calendar"/>{new Intl.DateTimeFormat('vi-VN',{day:'2-digit',month:'2-digit',year:'numeric',timeZone:'Asia/Ho_Chi_Minh'}).format(new Date(generatedAt))}</span></div><h1>{title}</h1><p>{context}</p></div>{action&&<Link className="button primary dashboard-header-action" to={action.href}>{action.label}<span aria-hidden="true">↗</span></Link>}</header>
}
export function DashboardSection({ title, description, action, children, className = '', count }: { title: string; description?: string; action?: DashboardAction; children: ReactNode; className?: string; count?: number }) {
 return <section className={`panel dashboard-section ${className}`}><div className="dashboard-section-heading"><div><h2>{title}{count!==undefined&&count>0&&<span className="dashboard-section-count">{count}</span>}</h2>{description&&<p>{description}</p>}</div>{action&&<Link className="text-link" to={action.href}>{action.label} →</Link>}</div>{children}</section>
}
export function MetricCard({ label, value, href, detail, tone = 'neutral', icon='activity' }: { label: string; value: number; href: string; detail?: string; tone?: 'neutral' | 'warning' | 'success'; icon?: IconName }) {
 return <Link to={href} className={`dashboard-metric ${tone}`}><span className="dashboard-metric-label">{label}<UiIcon name={icon}/></span><strong>{value.toLocaleString('vi-VN')}</strong><small>{detail??'Xem chi tiết'}<span className="dashboard-metric-arrow" aria-hidden="true">↗</span></small></Link>
}
export function EmptyDashboardState({ children }: { children: ReactNode }) { return <p className="dashboard-empty"><UiIcon name="check"/><span>{children}</span></p> }
export function AttentionList({ items }: { items: AttentionItem[] }) {
 return items.length?<ul className="dashboard-attention-list">{items.map(item=><li key={item.href}><span className="dashboard-attention-icon"><UiIcon name={item.status==='REVISION_REQUIRED'?'alert':'clock'}/></span><div><strong>{item.title}</strong><p>{item.detail}{item.at&&<span> · {dateTime(item.at)}</span>}</p></div><Link className="button secondary compact" to={item.href}>{item.cta} →</Link></li>)}</ul>:<EmptyDashboardState>Không có kế hoạch nào cần xử lý ngay.</EmptyDashboardState>
}
export function QuickActions({ actions }: { actions: DashboardAction[] }) {
 return actions.length>0?<nav className="dashboard-shortcuts" aria-label="Truy cập nhanh"><span className="dashboard-shortcuts-label">Truy cập nhanh</span><div className="dashboard-quick-actions">{actions.map(a=><Link to={a.href} key={a.href}>{a.label}<span aria-hidden="true">↗</span></Link>)}</div></nav>:null
}
export interface ChartSegment {label:string;value:number;color:string}
export function DonutChart({ segments, label='thiết bị', centerLabel }: { segments:ChartSegment[]; label?:string; centerLabel?:string }) {
 const total=segments.reduce((n,s)=>n+s.value,0);const circumference=2*Math.PI*42;let offset=0
 return <svg className="dashboard-donut" viewBox="0 0 120 120" role="img" aria-label={`${total} ${label}. ${segments.map(s=>`${s.label}: ${s.value}`).join('; ')}`}><circle className="dashboard-donut-track" cx="60" cy="60" r="42" fill="none" strokeWidth="10"/>{segments.filter(s=>s.value>0).map(s=>{const length=s.value/total*circumference;const start=offset;offset+=length;return <circle key={s.label} className="dashboard-donut-segment" cx="60" cy="60" r="42" fill="none" stroke={s.color} strokeWidth="10" strokeDasharray={`${Math.max(length-2,0)} ${circumference}`} strokeDashoffset={-start} transform="rotate(-90 60 60)"><title>{s.label}: {s.value} ({Math.round(s.value/total*100)}%)</title></circle>})}<text x="60" y="59" textAnchor="middle" className="dashboard-donut-value">{total.toLocaleString('vi-VN')}</text><text x="60" y="75" textAnchor="middle" className="dashboard-donut-label">{centerLabel??label}</text></svg>
}
export function ChartLegend({ segments }: { segments:ChartSegment[] }) {
 return <ul className="dashboard-chart-legend">{segments.map(s=><li key={s.label}><i style={{background:s.color}}/><span>{s.label}</span><strong>{s.value.toLocaleString('vi-VN')}</strong></li>)}</ul>
}
export function ItemList({ items, mode, empty }: { items: DashboardItem[]; mode: 'progress' | 'handover' | 'active' | 'history'; empty: string }) {
 return items.length?<ul className={`dashboard-records dashboard-items-${mode}`}>{items.map((row,index)=><li key={`${row.itemId}-${index}`}><span className={`dashboard-record-icon ${row.status==='REPAIR_REQUIRED'||row.status==='DAMAGE_DETECTED'?'warning':''}`}><UiIcon name={mode==='history'?'history':mode==='handover'?'check':'equipment'}/></span><div><Link className="table-link" to={mode==='history'?`/equipment/${row.equipmentId}/history`:`/plans/${row.planId}/items/${row.itemId}/execution`}>{row.equipmentCode} · {row.equipmentName}</Link><p>{row.provider??'Chưa phân công'}{row.at&&` · ${dateTime(row.at)}`}</p>{mode==='progress'&&row.note&&<p className="dashboard-row-note">{row.actor&&`${row.actor}: `}{row.note.split('\n').slice(1).join(' ')||row.note}</p>}</div><div className="dashboard-record-result"><StatusBadge label={itemStatusLabels[row.status as PlanItemStatus]??progressLabels[row.status as MaintenanceProgressStatus]} tone={row.status==='COMPLETED'||row.status==='WORK_DONE'?'teal':row.status==='REPAIR_REQUIRED'||row.status==='DAMAGE_DETECTED'||mode==='handover'?'amber':'neutral'}/>{mode==='handover'&&<Link className="text-link" to={`/plans/${row.planId}/items/${row.itemId}/execution`}>Xác nhận bàn giao →</Link>}</div></li>)}</ul>:<EmptyDashboardState>{empty}</EmptyDashboardState>
}
export function ReportList({ reports }: { reports: DashboardReport[] }) {
 return reports.length?<ul className="dashboard-records dashboard-report-list">{reports.map(r=><li key={r.planId}><span className="dashboard-record-icon"><UiIcon name="report"/></span><div><Link className="table-link" to={`/plans/${r.planId}/report`}>{r.title}</Link><p>{r.quarter&&r.year?`${r.quarter} / ${r.year} · `:''}{businessDate(r.reportDate)}</p><div className="dashboard-report-results"><span><i className="success"/>{r.completed} hoàn thành</span><span><i className="warning"/>{r.repair} chuyển sửa chữa</span></div></div><StatusBadge label={r.status==='FINAL'?'Chính thức':'Bản nháp'} tone={r.status==='FINAL'?'teal':'neutral'}/></li>)}</ul>:<EmptyDashboardState>Chưa có báo cáo bảo trì trong phạm vi hiển thị.</EmptyDashboardState>
}
