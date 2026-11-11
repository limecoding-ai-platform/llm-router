// @vitest-environment jsdom
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import BackendConnection from './BackendConnection'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  vi.useRealTimers()
})
const modelResponse = {
  object: 'list',
  data: [
    {
      id: 'openai/test-model',
      object: 'model',
      owned_by: 'openai',
      created: null,
      shutdown_date: null,
    },
  ],
}

async function connect() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Router API 키'), 'test-key')
  await user.click(screen.getByRole('button', { name: '연결 확인' }))
  return user
}

it('does not fetch before submitting and rejects an empty key', async () => {
  const fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  render(<BackendConnection />)
  await userEvent.click(screen.getByRole('button', { name: '연결 확인' }))
  expect(screen.getByRole('alert').textContent).toContain('키를 입력')
  expect(fetchMock).not.toHaveBeenCalled()
})

it('shows loading then real models, with the API key in the header only', async () => {
  let resolve!: (response: Response) => void
  const fetchMock = vi.fn(
    () =>
      new Promise<Response>((done) => {
        resolve = done
      }),
  )
  vi.stubGlobal('fetch', fetchMock)
  render(<BackendConnection />)
  await connect()
  expect(screen.getByRole('status').textContent).toContain('확인 중')
  expect(
    (screen.getByRole('button', { name: '확인 중…' }) as HTMLButtonElement)
      .disabled,
  ).toBe(true)
  await act(async () => resolve(Response.json(modelResponse)))
  expect(await screen.findByText('openai/test-model')).toBeTruthy()
  expect(screen.getByRole('status').textContent).toContain('1개')
  const [url, init] = fetchMock.mock.calls[0] as unknown as [
    string,
    RequestInit,
  ]
  expect(url).toBe('/api/v1/models')
  expect(new Headers(init.headers).get('Authorization')).toBe('Bearer test-key')
  expect(localStorage.length).toBe(0)
  expect(sessionStorage.length).toBe(0)
})

it('distinguishes an empty model list from failure', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(Response.json({ object: 'list', data: [] })),
  )
  render(<BackendConnection />)
  await connect()
  expect(
    await screen.findByText('연결은 정상이며, 등록된 모델이 없습니다.'),
  ).toBeTruthy()
  expect(screen.queryByRole('alert')).toBeNull()
})

it.each([
  [401, '인증에 실패했습니다'],
  [403, '접근 권한이 없습니다'],
  [429, '요청이 너무 많습니다'],
  [502, '백엔드에 연결하지 못했습니다'],
])('handles HTTP %s and allows retry', async (status, message) => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValueOnce(
        Response.json({ error: { message: 'upstream details' } }, { status }),
      )
      .mockResolvedValueOnce(Response.json(modelResponse)),
  )
  render(<BackendConnection />)
  const user = await connect()
  expect((await screen.findByRole('alert')).textContent).toContain(message)
  await user.click(screen.getByRole('button', { name: '다시 시도' }))
  expect(await screen.findByText('openai/test-model')).toBeTruthy()
  expect(screen.queryByRole('alert')).toBeNull()
})

it('handles a network failure', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockRejectedValue(new TypeError('Failed to fetch')),
  )
  render(<BackendConnection />)
  await connect()
  expect((await screen.findByRole('alert')).textContent).toContain(
    '백엔드에 연결하지 못했습니다',
  )
})

it('handles malformed JSON data without rendering it as models', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(Response.json({ data: [{ id: 123 }] })),
  )
  render(<BackendConnection />)
  await connect()
  expect((await screen.findByRole('alert')).textContent).toContain(
    '모델 목록을 읽을 수 없습니다',
  )
})

it('clears key and results on reset', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(Response.json(modelResponse)),
  )
  render(<BackendConnection />)
  const user = await connect()
  await screen.findByText('openai/test-model')
  await user.click(screen.getByRole('button', { name: '초기화' }))
  expect(
    (screen.getByLabelText('Router API 키') as HTMLInputElement).value,
  ).toBe('')
  expect(screen.queryByText('openai/test-model')).toBeNull()
})

it('cancels an in-flight request on unmount', async () => {
  let requestSignal: AbortSignal | undefined
  vi.stubGlobal(
    'fetch',
    vi.fn((_url, init: RequestInit) => {
      requestSignal = init.signal as AbortSignal
      return new Promise<Response>((_resolve, reject) =>
        requestSignal!.addEventListener('abort', () =>
          reject(requestSignal!.reason),
        ),
      )
    }),
  )
  const view = render(<BackendConnection />)
  await connect()
  view.unmount()
  await waitFor(() => expect(requestSignal?.aborted).toBe(true))
})

it('shows a timeout and allows retry after 15 seconds', async () => {
  vi.useFakeTimers()
  vi.stubGlobal(
    'fetch',
    vi.fn(
      (_url, init: RequestInit) =>
        new Promise<Response>((_resolve, reject) => {
          init.signal!.addEventListener('abort', () =>
            reject(init.signal!.reason),
          )
        }),
    ),
  )
  render(<BackendConnection />)
  fireEvent.change(screen.getByLabelText('Router API 키'), {
    target: { value: 'test-key' },
  })
  fireEvent.click(screen.getByRole('button', { name: '연결 확인' }))
  await act(async () => {
    await vi.advanceTimersByTimeAsync(15000)
  })
  expect(screen.getByRole('alert').textContent).toContain('응답 시간이 초과')
  expect(screen.getByRole('button', { name: '다시 시도' })).toBeTruthy()
})

it('does not display a stale result after cancellation', async () => {
  let resolve!: (response: Response) => void
  vi.stubGlobal(
    'fetch',
    vi.fn(
      () =>
        new Promise<Response>((done) => {
          resolve = done
        }),
    ),
  )
  render(<BackendConnection />)
  const user = await connect()
  await user.click(screen.getByRole('button', { name: '취소' }))
  await act(async () => resolve(Response.json(modelResponse)))
  expect(screen.queryByText('openai/test-model')).toBeNull()
  expect(screen.getByText('아직 연결을 확인하지 않았습니다.')).toBeTruthy()
})
