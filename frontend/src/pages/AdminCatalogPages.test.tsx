import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import { AppLayout } from '../layouts/AppLayout'
import { adminCatalogsApi } from '../api/adminCatalogsApi'
import { ApiError } from '../api/types'
import { AdminCatalogDetailPage, AdminCatalogFormPage, AdminCatalogListPage } from './AdminCatalogPages'
import type { CatalogItem, CatalogKind } from '../types/catalog'
import type { Role } from '../types/auth'

const auth = vi.hoisted(() => ({ user: { id: 4, username: 'demo_admin', role: 'ADMIN' as Role, departmentId: 2 },
  status: 'authenticated', isAuthenticated: true, logout: vi.fn(), retryRestore: vi.fn() }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => auth }))
vi.mock('../api/adminCatalogsApi', () => ({ adminCatalogsApi: {
  list: vi.fn(), detail: vi.fn(), create: vi.fn(), edit: vi.fn(), activate: vi.fn(), deactivate: vi.fn(),
} }))
const row: CatalogItem = { id: 71, code: 'SMOKE-V2-CATALOG', name: 'Đơn vị thử nghiệm', active: true, contactDetails: 'Nội bộ' }
const page = { content: [row], page: 0, size: 10, totalElements: 1, totalPages: 1, last: true }
const base = (kind: CatalogKind) => '/admin/catalogs/' + kind
function list(kind: CatalogKind) { return render(<MemoryRouter><AdminCatalogListPage kind={kind} /></MemoryRouter>) }
function detail(kind: CatalogKind) { return render(<MemoryRouter initialEntries={[base(kind) + '/71']}><Routes>
  <Route path={base(kind) + '/:id'} element={<AdminCatalogDetailPage kind={kind} />} /></Routes></MemoryRouter>) }
function form(kind: CatalogKind, mode: 'create' | 'edit') {
  const initial = base(kind) + (mode === 'create' ? '/new' : '/71/edit')
  return render(<MemoryRouter initialEntries={[initial]}><Routes>
    <Route path={base(kind) + '/new'} element={<AdminCatalogFormPage kind={kind} mode="create" />} />
    <Route path={base(kind) + '/:id/edit'} element={<AdminCatalogFormPage kind={kind} mode="edit" />} />
    <Route path={base(kind) + '/:id'} element={<p>Đã lưu</p>} />
  </Routes></MemoryRouter>)
}
beforeEach(() => {
  vi.clearAllMocks(); auth.user.role = 'ADMIN'
  vi.mocked(adminCatalogsApi.list).mockResolvedValue(page)
  vi.mocked(adminCatalogsApi.detail).mockResolvedValue(row)
  vi.mocked(adminCatalogsApi.create).mockResolvedValue(row)
  vi.mocked(adminCatalogsApi.edit).mockResolvedValue(row)
  vi.mocked(adminCatalogsApi.activate).mockResolvedValue({ ...row, active: true })
  vi.mocked(adminCatalogsApi.deactivate).mockResolvedValue({ ...row, active: false })
  vi.spyOn(window, 'confirm').mockReturnValue(true)
})
afterEach(() => { cleanup(); vi.restoreAllMocks() })
describe('ADMIN catalog navigation and guards', () => {
  it('shows both catalogs for ADMIN', () => {
    render(<MemoryRouter><AppLayout /></MemoryRouter>)
    expect(screen.getByRole('link', { name: 'Khoa / Phòng' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Đơn vị bảo trì' })).toBeTruthy()
  })
  it.each<Role>(['PHONG_VTYT', 'BAN_GIAM_DOC', 'KHOA_PHONG'])('hides navigation and rejects direct catalog route for %s', role => {
    auth.user.role = role
    const view = render(<MemoryRouter><AppLayout /></MemoryRouter>)
    expect(screen.queryByRole('link', { name: 'Khoa / Phòng' })).toBeNull()
    view.unmount()
    render(<MemoryRouter initialEntries={['/admin/catalogs/providers']}><App /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: 'Không có quyền xem trang' })).toBeTruthy()
    expect(adminCatalogsApi.list).not.toHaveBeenCalled()
  })
})
describe.each<CatalogKind>(['departments', 'providers'])('%s catalog', kind => {
  it('lists, searches, filters and renders an empty state', async () => {
    list(kind); await screen.findByText(row.code)
    fireEvent.change(screen.getByLabelText('Tìm theo mã hoặc tên'), { target: { value: '  smoke ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }))
    await waitFor(() => expect(vi.mocked(adminCatalogsApi.list).mock.calls.at(-1)?.[3]).toBe('smoke'))
    fireEvent.change(screen.getByLabelText('Trạng thái'), { target: { value: 'false' } })
    await waitFor(() => expect(vi.mocked(adminCatalogsApi.list).mock.calls.at(-1)?.[4]).toBe(false))
    vi.mocked(adminCatalogsApi.list).mockResolvedValueOnce({ ...page, content: [], totalElements: 0 })
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại' }))
    await screen.findByText('Không có mục phù hợp.')
  })
  it('validates required fields, trims input, and creates', async () => {
    form(kind, 'create')
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    await screen.findByText('Mã và tên là bắt buộc.')
    fireEvent.change(screen.getByLabelText('Mã'), { target: { value: ' CODE ' } })
    fireEvent.change(screen.getByLabelText(kind === 'departments' ? 'Tên Khoa/Phòng' : 'Tên đơn vị'), { target: { value: ' Tên ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    await waitFor(() => expect(adminCatalogsApi.create).toHaveBeenCalledWith(kind, expect.objectContaining({ code: 'CODE', name: 'Tên' })))
  })
  it('loads and edits existing item', async () => {
    form(kind, 'edit'); await screen.findByDisplayValue(row.code)
    fireEvent.change(screen.getByLabelText('Mã'), { target: { value: 'UPDATED' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    await waitFor(() => expect(adminCatalogsApi.edit).toHaveBeenCalledWith(kind, 71, expect.objectContaining({ code: 'UPDATED' })))
  })
  it('does not offer an empty edit form when detail is missing', async () => {
    vi.mocked(adminCatalogsApi.detail).mockRejectedValueOnce(new ApiError(404, 'RESOURCE_NOT_FOUND', 'missing'))
    form(kind, 'edit')
    await screen.findByRole('alert')
    expect(screen.queryByRole('button', { name: 'Lưu' })).toBeNull()
  })
  it('confirms status change and reloads detail', async () => {
    detail(kind); await screen.findByText(row.code)
    fireEvent.click(screen.getByRole('button', { name: 'Vô hiệu hóa' }))
    await waitFor(() => expect(adminCatalogsApi.deactivate).toHaveBeenCalledWith(kind, 71))
    expect(window.confirm).toHaveBeenCalled()
  })
  it('shows backend errors safely', async () => {
    vi.mocked(adminCatalogsApi.list).mockRejectedValueOnce(new ApiError(403, 'ACCESS_DENIED', 'internal'))
    list(kind)
    await screen.findByRole('alert')
    expect(screen.queryByText('internal')).toBeNull()
  })
})
