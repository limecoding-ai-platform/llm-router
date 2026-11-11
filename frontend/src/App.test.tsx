// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router'
import App from '@/App'

beforeEach(() => {
  window.history.replaceState(null, '', '/')
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  )
})
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  localStorage.clear()
})

function renderAt(path: string) {
  window.history.replaceState(null, '', path)
  return render(
    <BrowserRouter>
      <App />
    </BrowserRouter>,
  )
}

describe('console routing', () => {
  it.each([
    ['/', '홈'],
    ['/projects', '프로젝트'],
    ['/keys', 'API 키'],
    ['/usage', '사용량'],
    ['/logs', '요청 기록'],
    ['/providers', '제공업체 연결'],
    ['/settings', '설정'],
  ])(
    'opens %s directly with the matching title and active menu',
    (path, title) => {
      renderAt(path)
      expect(screen.getByRole('heading', { level: 1 }).textContent).toBe(title)
      const menu = within(screen.getByRole('navigation', { name: '주 메뉴' }))
      expect(
        menu.getByRole('link', { name: title }).getAttribute('aria-current'),
      ).toBe('page')
    },
  )

  it('navigates with history and restores the page on remount', async () => {
    const user = userEvent.setup()
    const view = renderAt('/')
    const menu = within(screen.getByRole('navigation', { name: '주 메뉴' }))
    await user.click(menu.getByRole('link', { name: 'API 키' }))
    expect(window.location.pathname).toBe('/keys')
    await user.click(menu.getByRole('link', { name: '요청 기록' }))
    expect(window.location.pathname).toBe('/logs')
    window.history.back()
    await waitFor(() =>
      expect(screen.getByRole('heading', { level: 1 }).textContent).toBe(
        'API 키',
      ),
    )
    window.history.forward()
    await waitFor(() =>
      expect(screen.getByRole('heading', { level: 1 }).textContent).toBe(
        '요청 기록',
      ),
    )
    view.unmount()
    render(
      <BrowserRouter>
        <App />
      </BrowserRouter>,
    )
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe(
      '요청 기록',
    )
  })

  it.each(['/missing', '/projects/missing'])(
    'shows 404 for %s and returns home',
    async (path) => {
      const user = userEvent.setup()
      renderAt(path)
      expect(
        screen.getByRole('heading', { name: '페이지를 찾을 수 없습니다' }),
      ).toBeTruthy()
      expect(
        screen.getByRole('navigation').querySelector('[aria-current="page"]'),
      ).toBeNull()
      await user.click(screen.getByRole('link', { name: '홈으로 돌아가기' }))
      expect(window.location.pathname).toBe('/')
      expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('홈')
    },
  )

  it('matches query strings and trailing slashes', () => {
    renderAt('/keys/?filter=active')
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('API 키')
    expect(
      within(screen.getByRole('navigation'))
        .getByRole('link', { name: 'API 키' })
        .getAttribute('aria-current'),
    ).toBe('page')
  })
})
