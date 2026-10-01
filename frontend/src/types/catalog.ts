export interface CatalogItem {
  id: number; code: string; name: string; active: boolean; contactDetails?: string | null
}
export interface CatalogInput { code: string; name: string; contactDetails?: string | null }
export type CatalogKind = 'departments' | 'providers'
