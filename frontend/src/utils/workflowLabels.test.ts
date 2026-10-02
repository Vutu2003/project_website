import { describe, expect, it } from 'vitest'
import { approvalTypeLabels, coverageLabels, itemStatusLabels, planStatusLabels } from './workflowLabels'
import { itemStatuses, planStatuses } from '../types/workflow'

describe('frozen workflow labels', () => {
  it('covers every wire status without changing its value', () => {
    expect(Object.keys(planStatusLabels)).toEqual([...planStatuses])
    expect(Object.keys(itemStatusLabels)).toEqual([...itemStatuses])
    expect(Object.keys(coverageLabels)).toEqual(['FREE', 'NOT_FREE'])
    expect(approvalTypeLabels.VENDOR_SELECTION).toBe('Duyệt đơn vị bảo trì')
  })
})
