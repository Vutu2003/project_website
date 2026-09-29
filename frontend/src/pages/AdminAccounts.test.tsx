import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import { AppLayout } from '../layouts/AppLayout'
import { adminAccountsApi } from '../api/adminAccountsApi'
import { departmentsApi } from '../api/departmentsApi'
import { ApiError } from '../api/types'
import { AdminAccountListPage } from './AdminAccountListPage'
import { AdminAccountFormPage } from './AdminAccountFormPage'
import { AdminAccountDetailPage } from './AdminAccountDetailPage'
import type { Role } from '../types/auth'
import type { AccountDetail } from '../types/account'

const auth = vi.hoisted(() => ({ user: { id: 4, username: 'demo_admin', role: 'ADMIN' as Role, departmentId: 2 },
  status: 'authenticated', isAuthenticated: true, logout: vi.fn(), retryRestore: vi.fn() }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => auth }))
vi.mock('../api/adminAccountsApi', () => ({ adminAccountsApi: { list: vi.fn(), detail: vi.fn(), create: vi.fn(), edit: vi.fn(), activate: vi.fn(), deactivate: vi.fn(), resetPassword: vi.fn() } }))
vi.mock('../api/departmentsApi', () => ({ departmentsApi: { list: vi.fn() } }))
const account: AccountDetail = { id: 44, username: 'SMOKE-V2-ADMIN-UI', role: 'KHOA_PHONG', departmentId: 1, departmentCode: 'KHOA_NOI', departmentName: 'Khoa Nội', active: true }
const page = { content: [account], page: 0, size: 10, totalElements: 11, totalPages: 2, last: false }
const refs = [{ id: 1, code: 'KHOA_NOI', name: 'Khoa Nội', active: true }, { id: 8, code: 'KHOA_NGOAI', name: 'Khoa Ngoại', active: true }]
function form(mode: 'create' | 'edit' = 'create') {
  const path = mode === 'create' ? '/admin/accounts/new' : '/admin/accounts/44/edit'
  return render(<MemoryRouter initialEntries={[path]}><Routes><Route path="/admin/accounts/new" element={<AdminAccountFormPage mode="create" />} /><Route path="/admin/accounts/:id/edit" element={<AdminAccountFormPage mode="edit" />} /><Route path="/admin/accounts/:id" element={<p>Đã chuyển đến chi tiết</p>} /></Routes></MemoryRouter>)
}
function list() { return render(<MemoryRouter><AdminAccountListPage /></MemoryRouter>) }
function detail() { return render(<MemoryRouter initialEntries={['/admin/accounts/44']}><Routes><Route path="/admin/accounts/:id" element={<AdminAccountDetailPage />} /></Routes></MemoryRouter>) }
function input(label: string, value: string) { fireEvent.change(screen.getByLabelText(new RegExp('^' + label)), { target: { value } }) }
async function readyForm() { await screen.findByLabelText('Tên đăng nhập') }
afterEach(() => { cleanup(); vi.restoreAllMocks() })
beforeEach(() => {
  vi.clearAllMocks(); auth.user = { id: 4, username: 'demo_admin', role: 'ADMIN', departmentId: 2 }
  vi.mocked(adminAccountsApi.list).mockResolvedValue(page)
  vi.mocked(adminAccountsApi.detail).mockResolvedValue(account)
  vi.mocked(adminAccountsApi.create).mockResolvedValue(account)
  vi.mocked(adminAccountsApi.edit).mockResolvedValue(account)
  vi.mocked(adminAccountsApi.activate).mockResolvedValue({ ...account, active: true })
  vi.mocked(adminAccountsApi.deactivate).mockResolvedValue({ ...account, active: false })
  vi.mocked(adminAccountsApi.resetPassword).mockResolvedValue(account)
  vi.mocked(departmentsApi.list).mockResolvedValue(refs)
  vi.spyOn(window, 'confirm').mockReturnValue(true)
})

describe('ADMIN routes and navigation', () => {
  it('shows account navigation for ADMIN', () => {
    render(<MemoryRouter><AppLayout /></MemoryRouter>)
    expect(screen.getByRole('link', { name: 'Quản lý tài khoản' }).getAttribute('href')).toBe('/admin/accounts')
  })
  it.each<Role>(['PHONG_VTYT', 'BAN_GIAM_DOC', 'KHOA_PHONG'])('hides ADMIN navigation and rejects direct routes for %s', role => {
    auth.user.role = role
    const view = render(<MemoryRouter><AppLayout /></MemoryRouter>)
    expect(screen.queryByRole('link', { name: 'Quản lý tài khoản' })).toBeNull()
    view.unmount()
    render(<MemoryRouter initialEntries={['/admin/accounts']}><App /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: 'Không có quyền xem trang' })).toBeTruthy()
    expect(adminAccountsApi.list).not.toHaveBeenCalled()
  })
})
describe('Account list', () => {
  it('renders safe table, status and paginates', async () => {
    list(); await screen.findByRole('link', { name: account.username })
    expect(screen.getByText('Khoa Nội', { selector: 'td' })).toBeTruthy()
    expect(screen.getByText('Hoạt động', { selector: '.status-badge' })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() => expect(vi.mocked(adminAccountsApi.list).mock.calls.at(-1)?.[0]).toBe(1))
  })
  it('applies search and combined filters, resets page and clears filters', async () => {
    list(); await screen.findByRole('link', { name: account.username })
    input('Tìm theo tên đăng nhập', ' smoke ')
    fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }))
    await waitFor(() => expect(vi.mocked(adminAccountsApi.list).mock.calls.at(-1)?.[2]?.search).toBe('smoke'))
    input('Vai trò', 'KHOA_PHONG'); input('Khoa/Phòng', '1'); input('Trạng thái', 'false')
    await waitFor(() => expect(vi.mocked(adminAccountsApi.list).mock.calls.at(-1)?.[2]).toEqual({ search: 'smoke', role: 'KHOA_PHONG', departmentId: 1, active: false }))
    fireEvent.click(screen.getByRole('button', { name: 'Xóa bộ lọc' }))
    await waitFor(() => expect(vi.mocked(adminAccountsApi.list).mock.calls.at(-1)?.[2]).toEqual({ search: '', role: undefined, departmentId: undefined, active: undefined }))
  })
  it('renders empty and safe backend error states with reload', async () => {
    vi.mocked(adminAccountsApi.list).mockResolvedValueOnce({ ...page, content: [], totalElements: 0, totalPages: 0, last: true })
    list(); await screen.findByText('Không có tài khoản phù hợp với bộ lọc.')
    vi.mocked(adminAccountsApi.list).mockRejectedValueOnce(new ApiError(403, 'ACCESS_DENIED', 'internal'))
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại' }))
    await screen.findByText('Bạn không có quyền thực hiện thao tác này.')
    expect(screen.queryByText('internal')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại dữ liệu' }))
    await screen.findByRole('link', { name: account.username })
  })
})
describe('Create and edit accounts', () => {
  it('requires username and password and masks password', async () => {
    form(); await readyForm()
    expect((screen.getByLabelText(/^Mật khẩu/) as HTMLInputElement).type).toBe('password')
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))
    await screen.findByText('Tên đăng nhập và mật khẩu không được để trống.')
    expect(adminAccountsApi.create).not.toHaveBeenCalled()
  })
  it('toggles the create password without submitting or clearing it', async () => {
    form(); await readyForm()
    const password = screen.getByLabelText('Mật khẩu') as HTMLInputElement
    const raw = crypto.randomUUID()
    expect(password.type).toBe('password')
    expect(password.autocomplete).toBe('new-password')
    input('Mật khẩu', raw)
    fireEvent.click(screen.getByRole('button', { name: 'Hiện mật khẩu' }))
    expect(password.type).toBe('text'); expect(password.value === raw).toBe(true)
    expect(adminAccountsApi.create).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Ẩn mật khẩu' }))
    expect(password.type).toBe('password'); expect(password.value === raw).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))
    await screen.findByText('Tên đăng nhập và mật khẩu không được để trống.')
    expect(adminAccountsApi.create).not.toHaveBeenCalled()
  })
  it('requires department for KHOA and creates with exact chosen password', async () => {
    form(); await readyForm()
    const raw = crypto.randomUUID()
    input('Tên đăng nhập', '  SMOKE-V2-ADMIN-CREATE  '); input('Mật khẩu', raw); input('Vai trò', 'KHOA_PHONG')
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))
    await screen.findByText('Vui lòng chọn Khoa/Phòng cho tài khoản Khoa/Phòng.')
    expect(adminAccountsApi.create).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText(/Khoa\/Phòng/), { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))
    await screen.findByText('Đã chuyển đến chi tiết')
    const body = vi.mocked(adminAccountsApi.create).mock.calls[0]?.[0]
    expect(body?.password === raw).toBe(true)
    expect(body?.username).toBe('SMOKE-V2-ADMIN-CREATE')
    expect(body?.departmentId).toBe(1); expect(body?.role).toBe('KHOA_PHONG')
    expect(document.querySelector('input[type=password]')).toBeNull()
  })
  it('shows duplicate error without rendering HTML', async () => {
    vi.mocked(adminAccountsApi.create).mockRejectedValue(new ApiError(409, 'USERNAME_ALREADY_EXISTS', 'Tên đăng nhập đã tồn tại.'))
    form(); await readyForm(); input('Tên đăng nhập', 'demo_admin'); input('Mật khẩu', crypto.randomUUID())
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))
    await screen.findByText('Tên đăng nhập đã tồn tại.')
  })
  it('edits only role and department with username read-only', async () => {
    form('edit'); await readyForm()
    expect((screen.getByLabelText('Tên đăng nhập') as HTMLInputElement).readOnly).toBe(true)
    expect(screen.queryByLabelText('Mật khẩu')).toBeNull()
    input('Vai trò', 'BAN_GIAM_DOC'); input('Khoa/Phòng', '')
    fireEvent.click(screen.getByRole('button', { name: 'Lưu chỉnh sửa' }))
    await screen.findByText('Đã chuyển đến chi tiết')
    expect(adminAccountsApi.edit).toHaveBeenCalledWith(44, { role: 'BAN_GIAM_DOC', departmentId: null })
  })
})
describe('Detail status and password reset', () => {
  it('renders detail and activates/deactivates through confirmed actions', async () => {
    detail(); await screen.findByRole('heading', { name: account.username })
    expect(screen.getByRole('link', { name: 'Chỉnh sửa' }).getAttribute('href')).toBe('/admin/accounts/44/edit')
    fireEvent.click(screen.getByRole('button', { name: 'Vô hiệu hóa' }))
    await screen.findByRole('button', { name: 'Kích hoạt' })
    expect(adminAccountsApi.deactivate).toHaveBeenCalledWith(44)
    fireEvent.click(screen.getByRole('button', { name: 'Kích hoạt' }))
    await screen.findByRole('button', { name: 'Vô hiệu hóa' })
    expect(adminAccountsApi.activate).toHaveBeenCalledWith(44)
    expect(screen.queryByRole('button', { name: /xóa/i })).toBeNull()
  })
  it('does not mutate status when confirmation is cancelled', async () => {
    vi.mocked(window.confirm).mockReturnValue(false)
    detail(); await screen.findByRole('button', { name: 'Vô hiệu hóa' })
    fireEvent.click(screen.getByRole('button', { name: 'Vô hiệu hóa' }))
    expect(adminAccountsApi.deactivate).not.toHaveBeenCalled()
  })
  it('shows self-protection business error and preserves active badge', async () => {
    vi.mocked(adminAccountsApi.deactivate).mockRejectedValue(new ApiError(409, 'ACCOUNT_SELF_DEACTIVATION_FORBIDDEN', 'Không thể vô hiệu hóa chính mình.'))
    detail(); await screen.findByRole('button', { name: 'Vô hiệu hóa' })
    fireEvent.click(screen.getByRole('button', { name: 'Vô hiệu hóa' }))
    await screen.findByText('Không thể vô hiệu hóa chính mình.')
    expect(screen.getByText('Hoạt động', { selector: '.status-badge' })).toBeTruthy()
  })
  it('toggles reset fields independently and preserves both values', async () => {
    detail(); await screen.findByRole('button', { name: 'Đặt lại mật khẩu' })
    fireEvent.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu' }))
    const password = screen.getByLabelText('Mật khẩu mới') as HTMLInputElement
    const confirm = screen.getByLabelText('Xác nhận mật khẩu mới') as HTMLInputElement
    expect(password.type).toBe('password'); expect(confirm.type).toBe('password')
    expect(password.autocomplete).toBe('new-password'); expect(confirm.autocomplete).toBe('new-password')
    const raw = crypto.randomUUID()
    input('Mật khẩu mới', raw); input('Xác nhận mật khẩu mới', raw)
    fireEvent.click(within(password.closest('.password-field') as HTMLElement).getByRole('button', { name: 'Hiện mật khẩu' }))
    expect(password.type).toBe('text'); expect(confirm.type).toBe('password')
    fireEvent.click(within(confirm.closest('.password-field') as HTMLElement).getByRole('button', { name: 'Hiện mật khẩu' }))
    expect(password.type).toBe('text'); expect(confirm.type).toBe('text')
    expect(password.value === raw).toBe(true); expect(confirm.value === raw).toBe(true)
    fireEvent.click(within(password.closest('.password-field') as HTMLElement).getByRole('button', { name: 'Ẩn mật khẩu' }))
    expect(password.type).toBe('password'); expect(confirm.type).toBe('text')
    expect(password.value === raw).toBe(true); expect(confirm.value === raw).toBe(true)
    expect(adminAccountsApi.resetPassword).not.toHaveBeenCalled()
  })
  it('validates matching password confirmation, resets and clears fields', async () => {
    detail(); await screen.findByRole('button', { name: 'Đặt lại mật khẩu' })
    fireEvent.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu' }))
    fireEvent.click(screen.getByRole('button', { name: 'Lưu mật khẩu mới' }))
    await screen.findByText('Vui lòng nhập và xác nhận mật khẩu mới.')
    const raw = crypto.randomUUID()
    input('Mật khẩu mới', raw); input('Xác nhận mật khẩu mới', crypto.randomUUID())
    fireEvent.click(screen.getByRole('button', { name: 'Lưu mật khẩu mới' }))
    await screen.findByText('Xác nhận mật khẩu mới không khớp.')
    expect(adminAccountsApi.resetPassword).not.toHaveBeenCalled()
    input('Xác nhận mật khẩu mới', raw)
    fireEvent.click(screen.getByRole('button', { name: 'Lưu mật khẩu mới' }))
    await screen.findByText('Đã đặt lại mật khẩu. Người dùng đăng nhập bằng mật khẩu mới đã cấp.')
    expect(vi.mocked(adminAccountsApi.resetPassword).mock.calls[0]?.[1].newPassword === raw).toBe(true)
    expect(document.querySelector('input[type=password]')).toBeNull()
    expect(screen.queryByLabelText('Mật khẩu mới')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu' }))
    expect((screen.getByLabelText('Mật khẩu mới') as HTMLInputElement).value).toBe('')
    expect((screen.getByLabelText('Xác nhận mật khẩu mới') as HTMLInputElement).value).toBe('')
    expect((screen.getByLabelText('Mật khẩu mới') as HTMLInputElement).type).toBe('password')
    expect((screen.getByLabelText('Xác nhận mật khẩu mới') as HTMLInputElement).type).toBe('password')
  })
  it('clears both reset values on API failure even when visible', async () => {
    vi.mocked(adminAccountsApi.resetPassword).mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', 'Internal error'))
    detail(); await screen.findByRole('button', { name: 'Đặt lại mật khẩu' })
    fireEvent.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu' }))
    const raw = crypto.randomUUID()
    input('Mật khẩu mới', raw); input('Xác nhận mật khẩu mới', raw)
    for (const button of screen.getAllByRole('button', { name: 'Hiện mật khẩu' })) fireEvent.click(button)
    fireEvent.click(screen.getByRole('button', { name: 'Lưu mật khẩu mới' }))
    await waitFor(() => expect(adminAccountsApi.resetPassword).toHaveBeenCalledTimes(1))
    await waitFor(() => {
      expect((screen.getByLabelText('Mật khẩu mới') as HTMLInputElement).value).toBe('')
      expect((screen.getByLabelText('Xác nhận mật khẩu mới') as HTMLInputElement).value).toBe('')
    })
  })
  it('clears reset passwords when cancelled and renders missing-account error', async () => {
    detail(); await screen.findByRole('button', { name: 'Đặt lại mật khẩu' })
    fireEvent.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu' }))
    input('Mật khẩu mới', crypto.randomUUID()); input('Xác nhận mật khẩu mới', crypto.randomUUID())
    for (const button of screen.getAllByRole('button', { name: 'Hiện mật khẩu' })) fireEvent.click(button)
    fireEvent.click(screen.getByRole('button', { name: 'Hủy' }))
    fireEvent.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu' }))
    expect((screen.getByLabelText('Mật khẩu mới') as HTMLInputElement).value).toBe('')
    expect((screen.getByLabelText('Xác nhận mật khẩu mới') as HTMLInputElement).value).toBe('')
    expect((screen.getByLabelText('Mật khẩu mới') as HTMLInputElement).type).toBe('password')
    fireEvent.click(screen.getByRole('button', { name: 'Hủy' }))
    expect(document.querySelector('input[type=password]')).toBeNull()
    vi.mocked(adminAccountsApi.detail).mockRejectedValueOnce(new ApiError(404, 'ACCOUNT_NOT_FOUND', 'internal'))
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại' }))
    await screen.findByText('Không tìm thấy dữ liệu yêu cầu. Vui lòng tải lại.')
  })
})
