import { ApiError } from '../api/types'
import { describeError } from '../utils/errorMessages'

export function WorkflowError({ error, onReload }: { error: unknown; onReload?: () => void }) {
  if (!error) return null
  const stale = error instanceof ApiError && error.code === 'OPTIMISTIC_LOCK_CONFLICT'
  return <div className="workflow-error" role="alert">
    <strong>{stale ? 'Xung đột phiên bản' : 'Không thể hoàn tất'}</strong>
    <p>{describeError(error)}</p>
    {error instanceof ApiError && error.fieldErrors.length > 0 && <ul>
      {error.fieldErrors.map((field, index) => <li key={`${field.field}-${index}`}>{field.field}: {field.message}</li>)}
    </ul>}
    {onReload && <button className="button secondary" type="button" onClick={onReload}>Tải lại dữ liệu</button>}
  </div>
}

export function WorkflowSuccess({ message }: { message: string | null }) {
  return message ? <div className="workflow-success" role="status">{message}</div> : null
}
