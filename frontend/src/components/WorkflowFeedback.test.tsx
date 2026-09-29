import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/types'
import { UserInputError } from '../utils/UserInputError'
import { WorkflowError } from './WorkflowFeedback'

describe('optimistic conflict presentation', () => {
  it('shows intentional local form feedback but hides unexpected JavaScript errors', () => {
    const { rerender } = render(<WorkflowError error={new UserInputError('Cần người ký thứ hai.')} />)
    expect(screen.getByText('Cần người ký thứ hai.')).toBeTruthy()
    rerender(<WorkflowError error={new Error('internal stack detail')} />)
    expect(screen.queryByText('internal stack detail')).toBeNull()
  })

  it('offers reload without silently retrying the mutation', () => {
    const reload = vi.fn()
    render(<WorkflowError error={new ApiError(409, 'OPTIMISTIC_LOCK_CONFLICT', 'stale')} onReload={reload} />)
    expect(screen.getByText(/Dữ liệu đã được thay đổi/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại dữ liệu' }))
    expect(reload).toHaveBeenCalledTimes(1)
  })
})
