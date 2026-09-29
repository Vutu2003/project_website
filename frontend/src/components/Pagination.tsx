import type { PageResponse } from '../types/workflow'

export function Pagination<T>({ data, onPage }: { data: PageResponse<T>; onPage: (page: number) => void }) {
  return <div className="pagination" aria-label="Phân trang">
    <span>Trang {data.page + 1}/{Math.max(data.totalPages, 1)} · {data.totalElements} bản ghi</span>
    <div><button className="button secondary" type="button" disabled={data.page === 0} onClick={() => onPage(data.page - 1)}>Trước</button>
      <button className="button secondary" type="button" disabled={data.last} onClick={() => onPage(data.page + 1)}>Sau</button></div>
  </div>
}
