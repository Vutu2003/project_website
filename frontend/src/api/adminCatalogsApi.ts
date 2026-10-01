import { apiRequest } from './client'
import type { PageResponse } from '../types/workflow'
import type { CatalogInput, CatalogItem, CatalogKind } from '../types/catalog'

const base = (kind: CatalogKind) => '/api/admin/' + kind
export const adminCatalogsApi = {
  list(kind: CatalogKind, page = 0, size = 10, search = '', active: boolean | undefined = undefined): Promise<PageResponse<CatalogItem>> {
    const params = new URLSearchParams({ page: String(page), size: String(size), sort: 'code,asc' })
    if (search.trim()) params.set('search', search.trim())
    if (active !== undefined) params.set('active', String(active))
    return apiRequest(base(kind) + '?' + params)
  },
  detail(kind: CatalogKind, id: number): Promise<CatalogItem> { return apiRequest(base(kind) + '/' + id) },
  create(kind: CatalogKind, body: CatalogInput): Promise<CatalogItem> { return apiRequest(base(kind), { method: 'POST', body }) },
  edit(kind: CatalogKind, id: number, body: CatalogInput): Promise<CatalogItem> { return apiRequest(base(kind) + '/' + id, { method: 'PATCH', body }) },
  activate(kind: CatalogKind, id: number): Promise<CatalogItem> { return apiRequest(base(kind) + '/' + id + '/activate', { method: 'POST' }) },
  deactivate(kind: CatalogKind, id: number): Promise<CatalogItem> { return apiRequest(base(kind) + '/' + id + '/deactivate', { method: 'POST' }) },
  async allDepartments(): Promise<CatalogItem[]> {
    const first = await this.list('departments', 0, 100)
    const rest = await Promise.all(Array.from({ length: Math.max(first.totalPages - 1, 0) }, (_, i) => this.list('departments', i + 1, 100)))
    return [first, ...rest].flatMap(page => page.content)
  },
}
