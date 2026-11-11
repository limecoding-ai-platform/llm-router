import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '@/api/client'
import { listModels, type RouterModel } from '@/api/models'
import { Icon } from '@/components/Icon'

type ConnectionState =
  | { status: 'idle' | 'loading' }
  | { status: 'success'; models: RouterModel[] }
  | { status: 'error'; message: string }

function errorMessage(error: unknown) {
  if (
    error &&
    typeof error === 'object' &&
    'name' in error &&
    error.name === 'TimeoutError'
  )
    return '응답 시간이 초과되었습니다. 백엔드 상태를 확인하고 다시 시도해 주세요.'
  if (error instanceof ApiError) {
    if (error.status === 401)
      return '인증에 실패했습니다. Router API 키를 확인해 주세요.'
    if (error.status === 403)
      return '접근 권한이 없습니다. API 키의 권한을 확인해 주세요.'
    if (error.status === 429)
      return '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.'
    if (error.kind === 'invalid_response')
      return '모델 목록을 읽을 수 없습니다. API 주소와 서버 응답 형식을 확인해 주세요.'
    if (error.kind === 'network' || (error.status && error.status >= 500))
      return '백엔드에 연결하지 못했습니다. 서버 실행 상태와 연결 주소를 확인해 주세요.'
    return `모델 조회에 실패했습니다.${error.status ? ` (HTTP ${error.status})` : ''} 다시 시도해 주세요.`
  }
  return '모델 조회 중 오류가 발생했습니다. 다시 시도해 주세요.'
}

export default function BackendConnection() {
  const [apiKey, setApiKey] = useState('')
  const [state, setState] = useState<ConnectionState>({ status: 'idle' })
  const activeRequest = useRef<AbortController | null>(null)

  useEffect(
    () => () => {
      const controller = activeRequest.current
      activeRequest.current = null
      controller?.abort()
    },
    [],
  )

  function cancel(clearKey = false) {
    const controller = activeRequest.current
    activeRequest.current = null
    controller?.abort()
    setState({ status: 'idle' })
    if (clearKey) setApiKey('')
  }

  async function connect(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (activeRequest.current) return
    if (!apiKey.trim()) {
      setState({ status: 'error', message: 'Router API 키를 입력해 주세요.' })
      return
    }
    const controller = new AbortController()
    activeRequest.current = controller
    setState({ status: 'loading' })
    const timeout = window.setTimeout(
      () =>
        controller.abort(new DOMException('Request timed out', 'TimeoutError')),
      15000,
    )
    try {
      const models = await listModels(apiKey.trim(), controller.signal)
      if (activeRequest.current === controller)
        setState({ status: 'success', models })
    } catch (error) {
      if (activeRequest.current === controller)
        setState({ status: 'error', message: errorMessage(error) })
    } finally {
      window.clearTimeout(timeout)
      if (activeRequest.current === controller) activeRequest.current = null
    }
  }

  return (
    <section className="connection-section" aria-labelledby="connection-title">
      <div className="section-heading">
        <h2 id="connection-title">백엔드 연결 확인</h2>
        <span>모델 목록 조회</span>
      </div>
      <div className="connection-panel">
        <p className="connection-description">
          Router API 키로 연결을 확인하고 사용 가능한 모델 목록을 조회합니다.
          모델 생성 요청은 보내지 않습니다.
        </p>
        <form onSubmit={connect} className="connection-form">
          <div className="connection-field">
            <label htmlFor="router-api-key">Router API 키</label>
            <input
              id="router-api-key"
              type="password"
              autoComplete="off"
              spellCheck={false}
              aria-describedby="key-help"
              value={apiKey}
              disabled={state.status === 'loading'}
              onChange={(event) => {
                setApiKey(event.target.value)
                setState({ status: 'idle' })
              }}
              placeholder="Router API 키 입력"
            />
          </div>
          <div className="connection-actions">
            <button
              type="submit"
              className="primary-button"
              disabled={state.status === 'loading'}
            >
              {state.status === 'loading'
                ? '확인 중…'
                : state.status === 'error'
                  ? '다시 시도'
                  : '연결 확인'}
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => cancel(state.status !== 'loading')}
            >
              {state.status === 'loading' ? '취소' : '초기화'}
            </button>
          </div>
        </form>
        <p id="key-help" className="key-help">
          입력한 키는 저장하지 않으며, 페이지를 벗어나거나 새로고침하면
          지워집니다. 콘솔 로그인용 비밀번호가 아닙니다.
        </p>
        {state.status === 'idle' && (
          <p className="connection-idle">아직 연결을 확인하지 않았습니다.</p>
        )}
        {state.status === 'loading' && (
          <p role="status" className="connection-status">
            <span className="loading-spinner" aria-hidden="true" />
            백엔드 연결 확인 중…
          </p>
        )}
        {state.status === 'error' && (
          <p role="alert" className="connection-error">
            {state.message}
          </p>
        )}
        {state.status === 'success' && (
          <>
            <p role="status" className="connection-status">
              <Icon name="plug" />
              {state.models.length
                ? `연결 성공 · 모델 ${state.models.length}개`
                : '연결은 정상이며, 등록된 모델이 없습니다.'}
            </p>
            {state.models.length > 0 && (
              <div className="model-table-wrapper">
                <table className="model-table">
                  <caption className="visually-hidden">
                    백엔드에 등록된 모델
                  </caption>
                  <thead>
                    <tr>
                      <th scope="col">모델 ID</th>
                      <th scope="col">소유자</th>
                      <th scope="col">종료 예정일</th>
                    </tr>
                  </thead>
                  <tbody>
                    {state.models.map((model) => (
                      <tr key={model.id}>
                        <td>
                          <code>{model.id}</code>
                        </td>
                        <td>{model.ownedBy ?? '—'}</td>
                        <td>{model.shutdownDate ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </div>
    </section>
  )
}
