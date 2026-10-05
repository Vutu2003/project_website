import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { StatusBadge } from './StatusBadge'
import type { AttentionItem, DashboardAction, DashboardItem, DashboardReport } from '../types/dashboard'
import { businessDate, dateTime, itemStatusLabels } from '../utils/workflowLabels'
import { progressLabels } from '../utils/executionProgress'
import type { MaintenanceProgressStatus } from '../types/execution'
import type { PlanItemStatus } from '../types/workflow'
export function DashboardHeader({ title, context, action, generatedAt }: { title: string; context: string; action?: DashboardAction; generatedAt: string }) {
 return <header className="dashboard-header"><div><p className="eyebrow">KHÔNG GIAN LÀM VIỆC</p><h1>{title}</h1><p>{context}</p><span className="dashboard-date">{new Intl.DateTimeFormat('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date(generatedAt))}</span></div>{action && <Link className="button primary" to={action.href}>{action.label} <span aria-hidden="true">↗</span></Link>}</header>
}
export function DashboardSection({ title, description, action, children, className = '' }: { title: string; description?: string; action?: DashboardAction; children: ReactNode; className?: string }) {
 return <section className={`panel dashboard-section ${className}`}><div className="dashboard-section-heading"><div><h2>{title}</h2>{description && <p>{description}</p>}</div>{action && <Link className="text-link" to={action.href}>{action.label} →</Link>}</div>{children}</section>
}
export function MetricCard({ label, value, href, detail, tone = 'neutral' }: { label: string; value: number; href: string; detail?: string; tone?: 'neutral' | 'warning' | 'success' }) {
 return <Link to={href} className={`dashboard-metric ${tone}`}><span>{label}</span><strong>{value.toLocaleString('vi-VN')}</strong><small>{detail ?? 'Xem chi tiết'} <span aria-hidden="true">→</span></small></Link>
}
export function EmptyDashboardState({ children }: { children: ReactNode }) { return <p className="dashboard-empty"><span aria-hidden="true">✓</span>{children}</p> }
export function AttentionList({ items }: { items: AttentionItem[] }) {
 return items.length ? <ul className="dashboard-attention-list">{items.map(item => <li key={item.href}><div><strong>{item.title}</strong><p>{item.detail}{item.at && <span> · {dateTime(item.at)}</span>}</p></div><Link className="button secondary compact" to={item.href}>{item.cta} →</Link></li>)}</ul> : <EmptyDashboardState>Không có kế hoạch nào cần xử lý ngay.</EmptyDashboardState>
}
export function QuickActions({ actions }: { actions: DashboardAction[] }) {
 return <DashboardSection title="Truy cập nhanh"><div className="dashboard-quick-actions">{actions.map(a => <Link to={a.href} key={a.href}>{a.label}<span aria-hidden="true">↗</span></Link>)}</div></DashboardSection>
}
export function ItemList({ items, mode, empty }: { items: DashboardItem[]; mode: 'progress' | 'handover' | 'active' | 'history'; empty: string }) {
 return items.length ? <ul className="dashboard-records">{items.map((row,index) => <li key={`${row.itemId}-${index}`}><div><Link className="table-link" to={mode === 'history' ? `/equipment/${row.equipmentId}/history` : `/plans/${row.planId}/items/${row.itemId}/execution`}>{row.equipmentCode} · {row.equipmentName}</Link><p>{row.provider ?? 'Chưa phân công'}{row.at && ` · ${dateTime(row.at)}`}</p>{mode === 'progress' && <p className="dashboard-row-note">{row.actor && `${row.actor}: `}{row.note?.split('\n').slice(1).join(' ') || row.note}</p>}</div><div className="dashboard-record-result"><StatusBadge label={itemStatusLabels[row.status as PlanItemStatus] ?? progressLabels[row.status as MaintenanceProgressStatus]} tone={row.status === 'COMPLETED' || row.status === 'WORK_DONE' ? 'teal' : row.status === 'REPAIR_REQUIRED' || row.status === 'DAMAGE_DETECTED' || mode === 'handover' ? 'amber' : 'neutral'} />{mode === 'handover' && <Link className="text-link" to={`/plans/${row.planId}/items/${row.itemId}/execution`}>Xác nhận bàn giao →</Link>}</div></li>)}</ul> : <EmptyDashboardState>{empty}</EmptyDashboardState>
}
export function ReportList({ reports }: { reports: DashboardReport[] }) {
 return reports.length ? <ul className="dashboard-records">{reports.map(r => <li key={r.planId}><div><Link className="table-link" to={`/plans/${r.planId}/report`}>{r.title}</Link><p>{r.quarter} / {r.year} · Ngày báo cáo {businessDate(r.reportDate)}</p><p>{r.completed} hoàn thành · {r.repair} chuyển sửa chữa</p></div><StatusBadge label={r.status === 'FINAL' ? 'Chính thức' : 'Bản nháp'} tone={r.status === 'FINAL' ? 'teal' : 'neutral'} /></li>)}</ul> : <EmptyDashboardState>Chưa có báo cáo bảo trì trong phạm vi hiển thị.</EmptyDashboardState>
}
