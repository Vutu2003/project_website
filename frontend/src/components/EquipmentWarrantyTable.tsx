import { Link } from 'react-router'
import { StatusBadge } from './StatusBadge'
import { businessDate } from '../utils/workflowLabels'
import type { CatalogEquipment } from '../types/equipmentCatalog'
export function WarrantyPeriod({ row }: { row: CatalogEquipment }) {
 return row.warranty_end_date ? <div className="warranty-period"><strong>Đến {businessDate(row.warranty_end_date)}</strong>{row.warranty_start_date && <span>Từ {businessDate(row.warranty_start_date)}</span>}</div> : <span className="muted">Chưa cập nhật thời hạn</span>
}
export function WarrantyBadge({ row }: { row: CatalogEquipment }) {
 if(!row.warranty_end_date) return <StatusBadge label="Chưa cập nhật" />
 return <div className="warranty-period"><StatusBadge label={row.warranty_status === 'VALID' ? 'Còn hạn' : 'Hết hạn'} tone={row.warranty_status === 'VALID' ? 'teal' : 'amber'} />{row.warranty_status === 'VALID' && row.warranty_days_remaining != null && row.warranty_days_remaining <= 90 && <span className="warning-text">Còn {row.warranty_days_remaining} ngày</span>}</div>
}
export function EquipmentWarrantyTable({ rows, withContracts = false }: { rows: CatalogEquipment[]; withContracts?: boolean }) {
 return <div className="table-scroll"><table className="data-table catalog-table"><thead><tr><th>Mã thiết bị</th><th>Tên thiết bị</th><th>Khoa/Phòng</th><th>Thời hạn bảo hành</th><th>Trạng thái bảo hành</th><th>Lịch bảo trì</th>{withContracts && <th>Hợp đồng bảo trì</th>}</tr></thead><tbody>{rows.map(r=><tr key={r.equipment_id}><td><Link className="equipment-code-link" to={`/equipment/${r.equipment_id}`}>{r.equipment_code}</Link></td><td><Link className="table-link" to={`/equipment/${r.equipment_id}`}>{r.equipment_name}</Link></td><td>{r.department_name}</td><td><WarrantyPeriod row={r}/></td><td><WarrantyBadge row={r}/></td><td><div className="quarter-badges">{r.quarters?.split(', ').filter(Boolean).map(q=><span key={q}>{q}</span>)}</div></td>{withContracts && <td>{r.contracts_label || '—'}</td>}</tr>)}</tbody></table></div>
}
