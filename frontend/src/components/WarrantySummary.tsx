import type { WarrantyInfo } from '../types/workflow'
import { businessDate } from '../utils/workflowLabels'
import { warrantyAt, warrantyContract, warrantyLabels } from '../utils/warranty'
import { StatusBadge } from './StatusBadge'

export function WarrantySummary({ info, referenceDate, coverageId }: {
  info: WarrantyInfo | null | undefined; referenceDate: string; coverageId?: number | null
}) {
  if (info === undefined) return <span className="muted">Đang tải bảo hành…</span>
  if (info === null) return <span className="warning-text">Không tải được bảo hành</span>
  const contract = warrantyContract(info, referenceDate, coverageId)
  const status = warrantyAt(contract?.warrantyExpiresOn, contract?.effectiveFrom, referenceDate)
  return <div className="warranty-summary"><StatusBadge label={warrantyLabels[status]} tone={status === 'ACTIVE' ? 'teal' : 'amber'} />
    <span className="row-sub">Hết hạn: {businessDate(contract?.warrantyExpiresOn)}</span>
    <span className="row-sub">Tính đến {businessDate(referenceDate)}</span>
    {contract?.contractReference && <span className="row-sub">{contract.contractReference}</span>}
  </div>
}
