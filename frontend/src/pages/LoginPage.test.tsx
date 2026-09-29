import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/types'
import { LoginPage } from './LoginPage'

const login = vi.hoisted(() => vi.fn())
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ status: 'anonymous', login, message: null }) }))

function page() {
  return render(<MemoryRouter initialEntries={['/login']}><Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/" element={<p>Đã đăng nhập</p>} />
  </Routes></MemoryRouter>)
}
afterEach(cleanup)
beforeEach(() => { login.mockReset(); login.mockResolvedValue(undefined) })

describe('Login password visibility', () => {
  it('masks by default, preserves the typed value and never submits when toggled', async () => {
    page()
    const password = screen.getByLabelText('Mật khẩu') as HTMLInputElement
    expect(password.type).toBe('password')
    expect(password.autocomplete).toBe('current-password')
    const raw = crypto.randomUUID()
    fireEvent.change(password, { target: { value: raw } })
    const show = screen.getByRole('button', { name: 'Hiện mật khẩu' })
    expect(show.getAttribute('type')).toBe('button')
    expect(show.getAttribute('aria-controls')).toBe(password.id)
    fireEvent.click(show)
    expect(password.type).toBe('text')
    expect(password.value === raw).toBe(true)
    expect(show.getAttribute('aria-pressed')).toBe('true')
    fireEvent.click(screen.getByRole('button', { name: 'Ẩn mật khẩu' }))
    expect(password.type).toBe('password')
    expect(password.value === raw).toBe(true)
    expect(login).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('Tên đăng nhập'), { target: { value: '  demo_vtyt  ' } })
    fireEvent.submit(password.closest('form')!)
    await screen.findByText('Đã đăng nhập')
    expect(login.mock.calls[0]?.[0].username).toBe('demo_vtyt')
    expect(login.mock.calls[0]?.[0].password === raw).toBe(true)
  })
  it('preserves the password and existing 401 feedback after a failed login', async () => {
    login.mockRejectedValue(new ApiError(401, 'INVALID_CREDENTIALS', 'Invalid credentials'))
    page()
    const password = screen.getByLabelText('Mật khẩu') as HTMLInputElement
    const raw = crypto.randomUUID()
    fireEvent.change(screen.getByLabelText('Tên đăng nhập'), { target: { value: 'demo_vtyt' } })
    fireEvent.change(password, { target: { value: raw } })
    fireEvent.click(screen.getByRole('button', { name: 'Hiện mật khẩu' }))
    fireEvent.submit(password.closest('form')!)
    await screen.findByText('Tên đăng nhập hoặc mật khẩu không đúng.')
    expect(password.value === raw).toBe(true)
    expect(password.type).toBe('text')
    fireEvent.click(screen.getByRole('button', { name: 'Ẩn mật khẩu' }))
    expect(password.value === raw).toBe(true)
    expect(password.type).toBe('password')
    await waitFor(() => expect(login).toHaveBeenCalledTimes(1))
  })
})
