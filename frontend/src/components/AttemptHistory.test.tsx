import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AttemptHistory } from './AttemptHistory'
import type { ExecutionAttempt } from '../types/execution'

function attempt(no: number, outcome: 'FAIL' | 'PASS'): ExecutionAttempt {
  return { executionId: no, attemptNo: no, actualProviderId: 1, actualProviderName: 'Đơn vị demo', startedAt: '2026-09-01T00:00:00Z', endedAt: '2026-09-02T00:00:00Z', resultNote: null,
    progress: [{ id: no, eventAt: '2026-09-01T01:00:00Z', workNote: `Ghi nhận lần ${no}`, damageNote: no === 1 ? 'Lỗi cần làm lại' : null, recordedByUserId: 1 }],
    technicalAcceptance: { id: no, type: 'TECHNICAL_ACCEPTANCE', result: outcome, observedAt: '2026-09-02T01:00:00Z', conclusion: `Nghiệm thu ${outcome}`, recordedByUserId: 1,
      departmentSignerId: null, departmentConfirmedAt: null, vtytSignerId: null, vtytConfirmedAt: null }, handoverAcceptance: null }
}
describe('shared execution and equipment history evidence', () => {
  it('keeps failed first attempt beside the later successful attempt', () => {
    render(<AttemptHistory attempts={[attempt(2, 'PASS'), attempt(1, 'FAIL')]} />)
    expect(screen.getByText('Lần thực hiện 1')).toBeTruthy()
    expect(screen.getByText('Lần thực hiện 2')).toBeTruthy()
    expect(screen.getByText('Nghiệm thu FAIL')).toBeTruthy()
    expect(screen.getByText('Nghiệm thu PASS')).toBeTruthy()
    expect(screen.getByText('Hư hỏng: Lỗi cần làm lại')).toBeTruthy()
    expect(screen.getAllByText('Không đạt').length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: /sửa|xóa/i })).toBeNull()
  })
})
