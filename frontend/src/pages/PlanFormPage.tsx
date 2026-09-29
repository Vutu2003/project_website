import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/types'
import { equipmentApi } from '../api/equipmentApi'
import { plansApi } from '../api/plansApi'
import { Pagination } from '../components/Pagination'
import { WorkflowError } from '../components/WorkflowFeedback'
import type { Equipment, PageResponse, Plan } from '../types/workflow'
import { businessDate } from '../utils/workflowLabels'

interface DraftItem { equipmentId: number; equipmentCode: string; equipmentName: string; plannedDate: string }

async function loadAllItems(planId: number): Promise<DraftItem[]> {
  const first = await plansApi.items(planId, 0, 100)
  const content = [...first.content]
  for (let page = 1; page < first.totalPages; page++) {
    content.push(...(await plansApi.items(planId, page, 100)).content)
  }
  return content.map(item => ({ equipmentId: item.equipmentId, equipmentCode: item.equipmentCode,
    equipmentName: item.equipmentName, plannedDate: item.plannedDate || '' }))
}

export function PlanFormPage({ mode }: { mode: 'create' | 'edit' }) {
  const { planId } = useParams()
  const id = Number(planId)
  const navigate = useNavigate()
  const [plan, setPlan] = useState<Plan | null>(null)
  const [title, setTitle] = useState('')
  const [periodStart, setPeriodStart] = useState('')
  const [periodEnd, setPeriodEnd] = useState('')
  const [items, setItems] = useState<DraftItem[]>([])
  const [equipmentPage, setEquipmentPage] = useState(0)
  const [equipment, setEquipment] = useState<PageResponse<Equipment> | null>(null)
  const [loading, setLoading] = useState(mode === 'edit')
  const [equipmentLoading, setEquipmentLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [validation, setValidation] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const reload = useCallback(() => { setLoading(true); setReloadKey(value => value + 1) }, [])

  useEffect(() => {
    if (mode === 'create') return
    if (!Number.isInteger(id) || id <= 0) { setError(new ApiError(404, 'PLAN_NOT_FOUND', 'Không tìm thấy kế hoạch.')); setLoading(false); return }
    let active = true
    Promise.all([plansApi.detail(id), loadAllItems(id)]).then(([result, rows]) => {
      if (!active) return
      setPlan(result); setTitle(result.title); setPeriodStart(result.periodStart); setPeriodEnd(result.periodEnd)
      setItems(rows); setError(null); setLoading(false)
    }).catch(failure => { if (active) { setError(failure); setLoading(false) } })
    return () => { active = false }
  }, [mode, id, reloadKey])
  useEffect(() => {
    let active = true
    equipmentApi.list(equipmentPage, 10).then(result => {
      if (active) { setEquipment(result); setEquipmentLoading(false) }
    }).catch(failure => { if (active) { setError(failure); setEquipmentLoading(false) } })
    return () => { active = false }
  }, [equipmentPage])

  function add(row: Equipment) {
    setItems(current => current.some(item => item.equipmentId === row.id) ? current :
      [...current, { equipmentId: row.id, equipmentCode: row.equipmentCode, equipmentName: row.name, plannedDate: '' }])
  }
  function updateDate(equipmentId: number, date: string) {
    setItems(current => current.map(item => item.equipmentId === equipmentId ? { ...item, plannedDate: date } : item))
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setValidation(null); setError(null)
    if (!title.trim() || !periodStart || !periodEnd) { setValidation('Vui lòng nhập tiêu đề và khoảng thời gian.'); return }
    if (periodEnd < periodStart) { setValidation('Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.'); return }
    if (items.length === 0) { setValidation('Kế hoạch cần ít nhất một thiết bị.'); return }
    if (items.some(item => item.plannedDate && (item.plannedDate < periodStart || item.plannedDate > periodEnd))) {
      setValidation('Ngày dự kiến của thiết bị phải nằm trong thời gian kế hoạch.'); return
    }
    const body = { title: title.trim(), periodStart, periodEnd,
      items: items.map(item => ({ equipmentId: item.equipmentId, plannedDate: item.plannedDate || null })) }
    setBusy(true)
    try {
      const result = mode === 'create' ? await plansApi.create(body) : await plansApi.edit(id, { ...body, version: plan!.version })
      navigate(`/plans/${result.id}`, { replace: true, state: { flash: mode === 'create' ? 'Đã tạo kế hoạch.' : 'Đã lưu chỉnh sửa kế hoạch.' } })
    } catch (failure) { setError(failure) } finally { setBusy(false) }
  }

  if (loading) return <p className="muted">Đang tải biểu mẫu…</p>
  if (mode === 'edit' && !plan) return <div className="page-stack"><WorkflowError error={error} onReload={reload} /><Link to="/plans">Về danh sách</Link></div>
  return <div className="page-stack">
    <div className="page-title-block"><p className="eyebrow">{mode === 'create' ? 'UC01 · TẠO KẾ HOẠCH' : 'UC02 · CHỈNH SỬA KẾ HOẠCH'}</p>
      <h1>{mode === 'create' ? 'Kế hoạch bảo trì mới' : 'Chỉnh sửa kế hoạch'}</h1>
      <p>{mode === 'edit' ? `Kế hoạch #${id} · ${plan?.status} · phiên bản ${plan?.version}` : 'Chọn thiết bị từ danh sách thật của hệ thống.'}</p></div>
    {mode === 'edit' && plan?.status !== 'DRAFT' && plan?.status !== 'REVISION_REQUIRED' &&
      <div className="workflow-error" role="alert">Trạng thái hiện tại không cho phép chỉnh sửa. <Link to={`/plans/${id}`}>Xem chi tiết</Link></div>}
    <form className="plan-form" onSubmit={event => void submit(event)}>
      <section className="panel business-panel"><div className="panel-heading"><h2>Thông tin kế hoạch</h2></div>
        <div className="form-grid"><label>Tiêu đề<input value={title} onChange={event => setTitle(event.target.value)} maxLength={255} required /></label>
          <label>Ngày bắt đầu<input type="date" value={periodStart} onChange={event => setPeriodStart(event.target.value)} required /></label>
          <label>Ngày kết thúc<input type="date" value={periodEnd} onChange={event => setPeriodEnd(event.target.value)} required /></label></div>
      </section>
      <section className="panel business-panel"><div className="panel-heading"><div><h2>Thiết bị trong kế hoạch</h2><p>{items.length} thiết bị được chọn</p></div></div>
        {mode === 'edit' && <p className="retention-note">V1 giữ lại hạng mục đã tạo để bảo toàn dấu vết audit. Có thể sửa ngày hoặc thêm thiết bị; không có thao tác loại bỏ.</p>}
        {items.length === 0 ? <p className="empty-state">Chưa chọn thiết bị.</p> : <div className="table-scroll"><table className="data-table"><thead><tr><th>Thiết bị</th><th>Ngày dự kiến</th></tr></thead>
          <tbody>{items.map(item => <tr key={item.equipmentId}><td><strong>{item.equipmentCode}</strong><span className="row-sub">{item.equipmentName}</span></td>
            <td><input aria-label={`Ngày dự kiến ${item.equipmentCode}`} type="date" value={item.plannedDate} onChange={event => updateDate(item.equipmentId, event.target.value)} /></td></tr>)}</tbody></table></div>}
      </section>
      <section className="panel business-panel"><div className="panel-heading"><div><h2>Chọn thêm thiết bị</h2><p>Danh sách thiết bị đang hoạt động, có phân trang</p></div></div>
        {equipmentLoading ? <p className="muted">Đang tải thiết bị…</p> : equipment && <><div className="table-scroll"><table className="data-table"><thead><tr><th>Mã thiết bị</th><th>Tên / model</th><th>Khoa</th><th></th></tr></thead>
          <tbody>{equipment.content.map(row => <tr key={row.id}><td>{row.equipmentCode}</td><td>{row.name}<span className="row-sub">{row.model || '—'}</span></td>
            <td>{row.departmentName || '—'}</td><td><button className="button secondary compact" type="button" disabled={items.some(item => item.equipmentId === row.id)} onClick={() => add(row)}>{items.some(item => item.equipmentId === row.id) ? 'Đã chọn' : 'Thêm'}</button></td></tr>)}</tbody></table></div>
          <Pagination data={equipment} onPage={next => { setEquipmentPage(next); setEquipmentLoading(true) }} /></>}
      </section>
      {validation && <div className="workflow-error" role="alert">{validation}</div>}
      <WorkflowError error={error} onReload={mode === 'edit' ? reload : undefined} />
      <div className="form-actions"><Link className="button secondary" to={mode === 'edit' ? `/plans/${id}` : '/plans'}>Hủy</Link>
        <button className="button primary" type="submit" disabled={busy || (mode === 'edit' && plan?.status !== 'DRAFT' && plan?.status !== 'REVISION_REQUIRED')}>
          {busy ? 'Đang lưu…' : mode === 'create' ? 'Tạo kế hoạch' : 'Lưu chỉnh sửa'}</button></div>
    </form>
    {mode === 'edit' && <p className="muted">Thời gian hiện tại: {businessDate(plan?.periodStart)} – {businessDate(plan?.periodEnd)}.</p>}
  </div>
}
