export interface FieldErrorResponse {
  field: string
  message: string
}

export interface ErrorResponse {
  timestamp: string
  status: number
  error: string
  code: string
  message: string
  path: string
  fieldErrors: FieldErrorResponse[]
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: FieldErrorResponse[] = [],
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export class NetworkError extends Error {
  constructor() {
    super('Không thể kết nối tới máy chủ. Vui lòng kiểm tra backend rồi thử lại.')
    this.name = 'NetworkError'
  }
}
