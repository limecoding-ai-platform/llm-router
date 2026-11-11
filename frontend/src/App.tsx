import { useEffect, useRef, useState } from 'react'
import { Icon, type IconName } from '@/components/Icon'
import { useTheme, type Theme } from '@/hooks/useTheme'

const navigation: {
  id: string
  label: string
  icon: IconName
  group: string
}[] = [
  { id: 'home', label: '홈', icon: 'home', group: '프로젝트' },
  { id: 'projects', label: '프로젝트', icon: 'folder', group: '프로젝트' },
  { id: 'keys', label: 'API 키', icon: 'key', group: '프로젝트' },
  { id: 'usage', label: '사용량', icon: 'chart', group: '프로젝트' },
  { id: 'logs', label: '요청 기록', icon: 'logs', group: '프로젝트' },
  { id: 'providers', label: '제공업체 연결', icon: 'plug', group: '관리' },
  { id: 'settings', label: '설정', icon: 'settings', group: '관리' },
]
const themes: { value: Theme; label: string; icon: IconName }[] = [
  { value: 'system', label: '시스템 설정', icon: 'monitor' },
  { value: 'light', label: '라이트', icon: 'sun' },
  { value: 'dark', label: '다크', icon: 'moon' },
]

export default function App() {
  const [page, setPage] = useState('home')
  const [collapsed, setCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [accountOpen, setAccountOpen] = useState(false)
  const [search, setSearch] = useState('')
  const { theme, setTheme } = useTheme()
  const accountRef = useRef<HTMLDivElement>(null)
  const accountButton = useRef<HTMLButtonElement>(null)
  const current = navigation.find((item) => item.id === page) ?? navigation[0]

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

  function navigate(id: string) {
    setPage(id)
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
                  <button
                    key={item.id}
                    className={`nav-item ${page === item.id ? 'active' : ''}`}
                    aria-current={page === item.id ? 'page' : undefined}
                    title={item.label}
                    onClick={() => navigate(item.id)}
                  >
                    <Icon name={item.icon} />
                    <span className="sidebar-label">{item.label}</span>
                  </button>
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
              <button
                className="popover-item"
                onClick={() => navigate('settings')}
              >
                <Icon name="settings" />
                설정
                <Icon name="arrow" />
              </button>
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
            <h1>{current.label}</h1>
          </div>
          <span className="preview-badge">미리보기</span>
        </header>
        <main id="content" tabIndex={-1}>
          {page === 'home' ? (
            <>
              <section className="welcome-panel">
                <div className="eyebrow">YOUR AI WORKSPACE</div>
                <h2>모든 LLM을 한곳에서.</h2>
                <p>
                  프로젝트부터 API 키, 사용량까지.
                  <br />
                  팀의 AI 사용을 한눈에 관리하세요.
                </p>
                <button
                  className="primary-button"
                  onClick={() => navigate('projects')}
                >
                  프로젝트 살펴보기
                  <Icon name="arrow" />
                </button>
                <div className="welcome-mark" aria-hidden="true">
                  <span />
                  <span />
                  <span />
                </div>
              </section>
              <section
                className="overview-section"
                aria-labelledby="overview-title"
              >
                <div className="section-heading">
                  <h2 id="overview-title">프로젝트 개요</h2>
                  <span>프로젝트를 선택하면 표시됩니다</span>
                </div>
                <div className="metrics">
                  {['총 요청', '사용 토큰', '추정 비용'].map((metric) => (
                    <div className="metric" key={metric}>
                      <span>{metric}</span>
                      <strong>—</strong>
                      <small>연결된 데이터가 없습니다</small>
                    </div>
                  ))}
                </div>
              </section>
              <section className="start-section" aria-labelledby="start-title">
                <div className="section-heading">
                  <h2 id="start-title">시작하기</h2>
                </div>
                <div className="start-grid">
                  {navigation
                    .filter((item) =>
                      ['projects', 'keys', 'logs'].includes(item.id),
                    )
                    .map((item) => (
                      <button
                        className="start-card"
                        key={item.id}
                        onClick={() => navigate(item.id)}
                      >
                        <span className="card-icon">
                          <Icon name={item.icon} />
                        </span>
                        <strong>{item.label}</strong>
                        <p>
                          {item.id === 'projects'
                            ? '서비스와 팀별로 AI 사용 환경을 구분하세요.'
                            : item.id === 'keys'
                              ? '애플리케이션에서 사용할 Router 키를 관리하세요.'
                              : '요청 내역과 오류를 한곳에서 확인하세요.'}
                        </p>
                        <span className="card-arrow">
                          <Icon name="arrow" />
                        </span>
                      </button>
                    ))}
                </div>
              </section>
              <p className="preview-note">
                레이아웃 미리보기입니다. 실제 프로젝트와 사용량 데이터는 아직
                연결되지 않았습니다.
              </p>
            </>
          ) : (
            <section className="empty-state">
              <span className="empty-icon">
                <Icon name={current.icon} />
              </span>
              <h2>{current.label}</h2>
              <p>이 공간에 {current.label} 기능이 추가될 예정입니다.</p>
              <button
                className="secondary-button"
                onClick={() => navigate('home')}
              >
                홈으로 돌아가기
                <Icon name="arrow" />
              </button>
            </section>
          )}
        </main>
        <footer className="page-footer">
          <span>LLM Router</span>
          <span>팀을 위한 AI 관리 콘솔</span>
        </footer>
      </div>
    </div>
  )
}
