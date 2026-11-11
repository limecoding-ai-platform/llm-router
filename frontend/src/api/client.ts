export type ApiErrorKind = 'http' | 'network' | 'invalid_response'

export class ApiError extends Error {
  readonly kind: ApiErrorKind
  readonly status?: number
  readonly code?: string

  constructor(
    kind: ApiErrorKind,
    message: string,
    status?: number,
    code?: string,
    cause?: unknown,
  ) {
    super(message, { cause })
    this.name = 'ApiError'
    this.kind = kind
    this.status = status
    this.code = code
  }
}

export type ApiRequestOptions = Omit<RequestInit, 'body'> & { json?: unknown }

function errorDetails(body: unknown): { message?: string; code?: string } {
  if (!body || typeof body !== 'object' || !('error' in body)) return {}
  const error = body.error
  if (!error || typeof error !== 'object') return {}
  return {
    message:
      'message' in error && typeof error.message === 'string'
        ? error.message
        : undefined,
    code:
      'code' in error && typeof error.code === 'string'
        ? error.code
        : undefined,
  }
}

export function createApiClient(baseUrl: string) {
  const base = baseUrl.trim().replace(/\/+$/, '')
  if (base && !/^\/(?!\/)/.test(base) && !/^https?:\/\//.test(base)) {
    throw new Error(
      'API 기본 주소는 /로 시작하는 경로 또는 HTTP(S) URL이어야 합니다.',
    )
  }

  return async function request<T = unknown>(
    path: string,
    options: ApiRequestOptions = {},
  ): Promise<T> {
    if (!/^\/(?!\/)/.test(path))
      throw new Error('API 경로는 /로 시작하는 상대 경로여야 합니다.')
    const { json, headers: customHeaders, ...init } = options
    const headers = new Headers(customHeaders)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (json !== undefined && !headers.has('Content-Type'))
      headers.set('Content-Type', 'application/json')
    const body = json === undefined ? undefined : JSON.stringify(json)

    try {
      const response = await fetch(`${base}${path}`, { ...init, headers, body })
      if (response.status === 204 || response.status === 205)
        return undefined as T
      const text = await response.text()
      let payload: unknown
      try {
        payload = JSON.parse(text)
      } catch (cause) {
        if (response.ok) {
          throw new ApiError(
            'invalid_response',
            '서버 응답이 올바른 JSON 형식이 아닙니다.',
            response.status,
            undefined,
            cause,
          )
        }
      }
      if (!response.ok) {
        const details = errorDetails(payload)
        throw new ApiError(
          'http',
          details.message || `API 요청에 실패했습니다. (${response.status})`,
          response.status,
          details.code,
        )
      }
      return payload as T
    } catch (cause) {
      if (options.signal?.aborted) throw options.signal.reason
      if (cause instanceof ApiError) throw cause
      if (cause instanceof Error && cause.name === 'AbortError') throw cause
      throw new ApiError(
        'network',
        '서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.',
        undefined,
        undefined,
        cause,
      )
    }
  }
}

export const apiRequest = createApiClient(
  import.meta.env.VITE_API_BASE_URL?.trim() || '/api',
)
