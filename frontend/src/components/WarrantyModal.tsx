import { useEffect, useState } from 'react'
import { useAuth } from '../auth/useAuth'
import { equipmentApi } from '../api/equipmentApi'
import { providersApi } from '../api/providersApi'
import type { Provider, UpdateWarrantyInput, WarrantyInfo } from '../types/workflow'
import { businessDate } from '../utils/workflowLabels'
import { warrantyLabels } from '../utils/warranty'
import { StatusBadge } from './StatusBadge'
import { WorkflowError } from './WorkflowFeedback'
import { Modal } from './Modal'

export function WarrantyModal({ equipmentId, referenceDate, onClose, onUpdated }: {
  equipmentId: number; referenceDate?: string; onClose: () => void; onUpdated?: () => void
}) {
  const { user } = useAuth()
  const canEdit = user?.role === 'PHONG_VTYT' || user?.role === 'ADMIN'
  const [info, setInfo] = useState<WarrantyInfo | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [reload, setReload] = useState(0)
  const [editing, setEditing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [providers, setProviders] = useState<Provider[]>([])
  const [draft, setDraft] = useState<UpdateWarrantyInput>({ manufacturerProviderId: null, contracts: [] })
  useEffect(() => {
    let active = true
    equipmentApi.warranty(equipmentId, referenceDate).then(result => { if (active) { setInfo(result); setError(null) } }).catch(e => { if (active) setError(e) })
    return () => { active = false }
  }, [equipmentId, referenceDate, reload])
  async function edit() {
    if (!info || busy) return
    setError(null); setBusy(true)
    try {
      setProviders(await providersApi.list())
      setDraft({ manufacturerProviderId: info.manufacturerProviderId, contracts: info.contracts.map(c => ({ id: c.id, warrantyExpiresOn: c.warrantyExpiresOn })) })
      setEditing(true)
    } catch (e) { setError(e) } finally { setBusy(false) }
  }
  async function save() {
    setBusy(true); setError(null)
    try {
      await equipmentApi.updateWarranty(equipmentId, draft)
      setEditing(false); setReload(v => v + 1); onUpdated?.()
    } catch (e) { setError(e) } finally { setBusy(false) }
  }
  return <Modal title="Thông tin bảo hành" onClose={onClose}>
    <WorkflowError error={error} onReload={() => setReload(v => v + 1)} />
    {!info && !error && <p>Đang tải thông tin bảo hành…</p>}
    {info && <><h3>{info.equipmentCode} · {info.equipmentName}</h3><p className="muted">Trạng thái tính đến {businessDate(info.referenceDate)}. Bảo hành còn hiệu lực đến hết ngày cuối cùng.</p>
      <dl className="detail-list"><div><dt>Nhà sản xuất / đại diện</dt><dd>{info.manufacturerName || 'Chưa có thông tin'}{info.manufacturerActive === false && ' (ngừng hoạt động)'}</dd></div><div><dt>Thông tin liên hệ</dt><dd>{info.manufacturerContact || 'Chưa có thông tin liên hệ'}</dd></div></dl>
      {info.contracts.length === 0 ? <p className="retention-note">Chưa có hồ sơ hợp đồng hoặc ngày hết bảo hành để xác định thời hạn.</p> : <div className="table-scroll"><table className="data-table warranty-table"><thead><tr><th>Hợp đồng / đơn vị</th><th>Hiệu lực hợp đồng</th><th>Ngày hết bảo hành</th><th>Trạng thái</th></tr></thead><tbody>{info.contracts.map(c => <tr key={c.id}><td><strong>{c.contractReference || `Hồ sơ #${c.id}`}</strong><span className="row-sub">{c.providerName || '—'}</span><span className="row-sub">{c.providerContact}</span></td><td>{businessDate(c.effectiveFrom)} – {businessDate(c.effectiveTo)}<span className="row-sub">{c.coverageScope}</span></td><td>{businessDate(c.warrantyExpiresOn)}</td><td><StatusBadge label={warrantyLabels[c.warrantyStatus]} tone={c.warrantyStatus === 'ACTIVE' ? 'teal' : 'amber'} /></td></tr>)}</tbody></table></div>}
      <p className="muted">Khi hết bảo hành, chọn liên hệ nhà sản xuất hoặc bảo hành ngoài trong kế hoạch. Đơn vị thực hiện vẫn cần BGĐ phê duyệt.</p>
      {canEdit && !editing && <button type="button" className="button secondary" disabled={busy} onClick={() => void edit()}>Cập nhật hồ sơ bảo hành</button>}
      {editing && <form className="warranty-editor" onSubmit={event => { event.preventDefault(); void save() }}><fieldset disabled={busy}><legend>Cập nhật hồ sơ bảo hành</legend><div className="form-grid"><label>Nhà sản xuất / đại diện<select value={draft.manufacturerProviderId || ''} onChange={event => setDraft(v => ({ ...v, manufacturerProviderId: event.target.value ? Number(event.target.value) : null }))}><option value="">Chưa xác định</option>{providers.filter(p => p.active || p.id === draft.manufacturerProviderId).map(p => <option key={p.id} value={p.id} disabled={!p.active}>{p.name}{!p.active && ' (ngừng hoạt động)'}</option>)}</select></label>{info.contracts.map(c => <label key={c.id}>Ngày hết bảo hành · {c.contractReference || `Hồ sơ #${c.id}`}<input type="date" min={c.effectiveFrom || undefined} value={draft.contracts.find(v => v.id === c.id)?.warrantyExpiresOn || ''} onChange={event => setDraft(v => ({ ...v, contracts: v.contracts.map(row => row.id === c.id ? { ...row, warrantyExpiresOn: event.target.value || null } : row) }))} /></label>)}</div><p className="muted">Chọn nhà sản xuất hoặc đại diện có trong danh mục đơn vị. ADMIN quản lý thông tin liên hệ tại danh mục này.</p><div className="form-actions"><button type="button" className="button secondary" onClick={() => setEditing(false)}>Hủy chỉnh sửa</button><button type="submit" className="button primary">{busy ? 'Đang lưu…' : 'Lưu hồ sơ'}</button></div></fieldset></form>}
    </>}
  </Modal>
}
