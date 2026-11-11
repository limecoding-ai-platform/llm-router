import { createServer as createHttpServer } from 'node:http'
import type { AddressInfo } from 'node:net'
import { fileURLToPath } from 'node:url'
import { createServer as createViteServer } from 'vite'
import { expect, it, vi } from 'vitest'
import { createApiClient } from './client'

it('proxies JSON requests, query strings and auth without swallowing API errors into the SPA', async () => {
  const backend = createHttpServer((request, response) => {
    response.setHeader('Content-Type', 'application/json')
    if (request.headers.authorization !== 'Bearer test-key') {
      response.statusCode = 401
      response.end(
        JSON.stringify({
          error: {
            message: 'Invalid or missing API key',
            code: 'invalid_api_key',
          },
        }),
      )
      return
    }
    response.end(JSON.stringify({ url: request.url, method: request.method }))
  })
  await new Promise<void>((resolve) => backend.listen(0, '127.0.0.1', resolve))
  const backendPort = (backend.address() as AddressInfo).port
  vi.stubEnv('BACKEND_URL', `http://127.0.0.1:${backendPort}`)
  let vite: Awaited<ReturnType<typeof createViteServer>> | undefined
  try {
    vite = await createViteServer({
      configFile: fileURLToPath(
        new URL('../../vite.config.ts', import.meta.url),
      ),
      server: { host: '127.0.0.1', port: 0, open: false },
      optimizeDeps: { noDiscovery: true, include: [] },
      logLevel: 'silent',
    })
    await vite.listen()
    const port = (vite.httpServer!.address() as AddressInfo).port
    const request = createApiClient(`http://127.0.0.1:${port}/api`)
    expect(
      await request('/v1/models?limit=2', {
        headers: { Authorization: 'Bearer test-key' },
      }),
    ).toEqual({ url: '/v1/models?limit=2', method: 'GET' })
    await expect(request('/v1/models')).rejects.toMatchObject({
      kind: 'http',
      status: 401,
      code: 'invalid_api_key',
    })
    const page = await fetch(`http://127.0.0.1:${port}/projects`, {
      headers: { Accept: 'text/html' },
    })
    expect(await page.text()).toContain('/src/main.tsx')
    const unrelatedPath = await fetch(`http://127.0.0.1:${port}/api-other`, {
      headers: { Accept: 'text/html' },
    })
    expect(await unrelatedPath.text()).toContain('/src/main.tsx')
  } finally {
    await vite?.close()
    backend.closeAllConnections()
    await new Promise<void>((resolve, reject) =>
      backend.close((error) => (error ? reject(error) : resolve())),
    )
    vi.unstubAllEnvs()
  }
}, 15000)
