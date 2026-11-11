import { Link, Outlet, matchPath, useLocation } from 'react-router'
import { navigation } from '@/navigation'
import { useEffect, useRef, useState } from 'react'
import { Icon, type IconName } from '@/components/Icon'
import { useTheme, type Theme } from '@/hooks/useTheme'

const themes: { value: Theme; label: string; icon: IconName }[] = [
  { value: 'system', label: '시스템 설정', icon: 'monitor' },
  { value: 'light', label: '라이트', icon: 'sun' },
  { value: 'dark', label: '다크', icon: 'moon' },
]

export default function ConsoleLayout() {
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [accountOpen, setAccountOpen] = useState(false)
  const [search, setSearch] = useState('')
  const { theme, setTheme } = useTheme()
  const accountRef = useRef<HTMLDivElement>(null)
  const accountButton = useRef<HTMLButtonElement>(null)
  const current = navigation.find((item) =>
    matchPath({ path: item.path, end: true }, location.pathname),
  )
  const title = current?.label ?? '404'

  useEffect(() => {
    document.title = `${title} · LLM Router`
  }, [title])

  useEffect(() => {
    const outside = (event: PointerEvent) => {
      if (!accountRef.current?.contains(event.target as Node))
        setAccountOpen(false)
    }
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (accountOpen) accountButton.current?.focus()
        setAccountOpen(false)
        setMobileOpen(false)
      }
    }
    document.addEventListener('pointerdown', outside)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('pointerdown', outside)
      document.removeEventListener('keydown', escape)
    }
  }, [accountOpen])

  function closeMenus() {
    setMobileOpen(false)
    setAccountOpen(false)
  }

  return (
    <div className={`console ${collapsed ? 'is-collapsed' : ''}`}>
      <a className="skip-link" href="#content">
        본문으로 이동
      </a>
      {mobileOpen && (
        <button
          className="sidebar-overlay"
          aria-label="메뉴 닫기"
          onClick={() => setMobileOpen(false)}
        />
      )}
      <aside
        className={`sidebar ${mobileOpen ? 'mobile-open' : ''}`}
        aria-label="사이드바"
      >
        <div className="workspace-row">
          <div className="workspace-name">
            <span className="workspace-logo">L</span>
            <span className="sidebar-label">LLM Router</span>
          </div>
          <button
            className="icon-button collapse-button"
            aria-label={collapsed ? '사이드바 펼치기' : '사이드바 접기'}
            onClick={() => {
              setCollapsed(!collapsed)
              setAccountOpen(false)
            }}
          >
            <Icon name="panel" />
          </button>
          <button
            className="icon-button mobile-close"
            aria-label="메뉴 닫기"
            onClick={() => setMobileOpen(false)}
          >
            <Icon name="close" />
          </button>
        </div>
        <div className="project-selector sidebar-label">
          <span className="project-dot" />
          프로젝트 미선택<span className="muted">—</span>
        </div>
        <label className="sidebar-search">
          <Icon name="search" />
          <input
            aria-label="메뉴 검색"
            placeholder="메뉴 검색"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
        <nav className="navigation" aria-label="주 메뉴">
          {['프로젝트', '관리'].map((group) => (
            <div className="nav-group" key={group}>
              <div className="nav-heading sidebar-label">{group}</div>
              {navigation
                .filter(
                  (item) => item.group === group && item.label.includes(search),
                )
                .map((item) => (
                  <Link
                    key={item.id}
                    to={item.path}
                    className={`nav-item ${current?.id === item.id ? 'active' : ''}`}
                    aria-current={current?.id === item.id ? 'page' : undefined}
                    title={item.label}
                    onClick={closeMenus}
                  >
                    <Icon name={item.icon} />
                    <span className="sidebar-label">{item.label}</span>
                  </Link>
                ))}
            </div>
          ))}
          {navigation.every((item) => !item.label.includes(search)) && (
            <p className="search-empty sidebar-label">
              일치하는 메뉴가 없습니다.
            </p>
          )}
        </nav>
        <div className="account-area" ref={accountRef}>
          {accountOpen && (
            <section
              className="account-popover"
              id="account-options"
              aria-label="계정 및 화면 설정"
            >
              <div className="account-summary">
                <strong>로컬 미리보기</strong>
                <span>계정이 연결되지 않았습니다.</span>
              </div>
              <div className="theme-switch" role="group" aria-label="화면 테마">
                {themes.map((item) => (
                  <button
                    key={item.value}
                    title={item.label}
                    aria-label={item.label}
                    aria-pressed={theme === item.value}
                    onClick={() => setTheme(item.value)}
                  >
                    <Icon name={item.icon} />
                  </button>
                ))}
              </div>
              <div className="popover-divider" />
              <Link
                className="popover-item"
                to="/settings"
                onClick={closeMenus}
              >
                <Icon name="settings" />
                설정
                <Icon name="arrow" />
              </Link>
              <div className="account-note">
                로그인과 계정 관리는 추후 제공됩니다.
              </div>
            </section>
          )}
          <button
            ref={accountButton}
            className="account-button"
            aria-label="계정 및 화면 설정"
            aria-expanded={accountOpen}
            aria-controls="account-options"
            onClick={() => setAccountOpen(!accountOpen)}
          >
            <span className="avatar">L</span>
            <span className="account-copy sidebar-label">
              <strong>로컬 워크스페이스</strong>
              <span>개발 환경</span>
            </span>
            <span className="sidebar-label">⌃</span>
          </button>
        </div>
      </aside>
      <div className="main-area">
        <header className="page-header">
          <div className="page-heading">
            <button
              className="icon-button mobile-toggle"
              aria-label="메뉴 열기"
              aria-expanded={mobileOpen}
              onClick={() => setMobileOpen(true)}
            >
              <Icon name="panel" />
            </button>
            <h1>{title}</h1>
          </div>
          <span className="preview-badge">미리보기</span>
        </header>
        <main id="content" tabIndex={-1}>
          <Outlet />
        </main>
        <footer className="page-footer">
          <span>LLM Router</span>
          <span>팀을 위한 AI 관리 콘솔</span>
        </footer>
      </div>
    </div>
  )
}
