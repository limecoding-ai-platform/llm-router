import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, createApiClient } from './client'

afterEach(() => vi.unstubAllGlobals())

describe('JSON API client', () => {
  it('joins the base path and preserves query parameters', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(Response.json({ data: ['model'] }))
    vi.stubGlobal('fetch', fetchMock)
    const request = createApiClient('/api/')
    expect(await request('/v1/models?limit=2')).toEqual({ data: ['model'] })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/models?limit=2')
  })

  it('serializes JSON and forwards caller headers without installing a shared key', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementation(async () => Response.json({ id: 'new' }))
    vi.stubGlobal('fetch', fetchMock)
    await createApiClient('/api')('/projects', {
      method: 'POST',
      json: { name: 'test' },
      headers: { Authorization: 'Bearer test-key' },
    })
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.body).toBe('{"name":"test"}')
    expect(new Headers(init.headers).get('Content-Type')).toBe(
      'application/json',
    )
    expect(new Headers(init.headers).get('Authorization')).toBe(
      'Bearer test-key',
    )
    await createApiClient('/api')('/v1/models')
    expect(
      new Headers(fetchMock.mock.calls[1][1].headers).has('Authorization'),
    ).toBe(false)
  })

  it('handles 204 without trying to parse JSON', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(null, { status: 204 })),
    )
    expect(
      await createApiClient('/api')<void>('/projects/1', { method: 'DELETE' }),
    ).toBeUndefined()
  })

  it.each([401, 403, 500])(
    'retains HTTP %s and backend error details',
    async (status) => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(
          Response.json(
            {
              error: {
                message: 'Invalid or missing API key',
                code: 'invalid_api_key',
              },
            },
            { status },
          ),
        ),
      )
      await expect(createApiClient('/api')('/v1/models')).rejects.toMatchObject(
        {
          kind: 'http',
          status,
          code: 'invalid_api_key',
          message: 'Invalid or missing API key',
        },
      )
    },
  )

  it('reports non-JSON proxy errors without displaying HTML', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response('<html>gateway error</html>', { status: 502 }),
        ),
    )
    await expect(createApiClient('/api')('/v1/models')).rejects.toMatchObject({
      kind: 'http',
      status: 502,
      message: 'API 요청에 실패했습니다. (502)',
    })
  })

  it('rejects an HTML success response instead of treating an SPA fallback as data', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('<html>app</html>')),
    )
    await expect(createApiClient('/api')('/v1/models')).rejects.toMatchObject({
      kind: 'invalid_response',
      status: 200,
    })
  })

  it('distinguishes network failure from an HTTP error', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(new TypeError('Failed to fetch')),
    )
    await expect(createApiClient('/api')('/v1/models')).rejects.toMatchObject({
      kind: 'network',
      status: undefined,
    })
  })

  it('preserves request cancellation', async () => {
    const controller = new AbortController()
    controller.abort()
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(controller.signal.reason))
    await expect(
      createApiClient('/api')('/v1/models', { signal: controller.signal }),
    ).rejects.toBe(controller.signal.reason)
  })

  it('rejects absolute endpoint URLs', async () => {
    await expect(
      createApiClient('/api')('https://other.example/models'),
    ).rejects.toThrow('API 경로')
    expect(new ApiError('network', 'offline')).toBeInstanceOf(Error)
  })
})
