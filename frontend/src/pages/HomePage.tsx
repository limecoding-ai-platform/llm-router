import { Link } from 'react-router'
import { Icon } from '@/components/Icon'
import { navigation } from '@/navigation'

export default function HomePage() {
  return (
    <>
      <section className="welcome-panel">
        <div className="eyebrow">YOUR AI WORKSPACE</div>
        <h2>모든 LLM을 한곳에서.</h2>
        <p>
          프로젝트부터 API 키, 사용량까지.
          <br />
          팀의 AI 사용을 한눈에 관리하세요.
        </p>
        <Link className="primary-button" to="/projects">
          프로젝트 살펴보기
          <Icon name="arrow" />
        </Link>
        <div className="welcome-mark" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
      </section>
      <section className="overview-section" aria-labelledby="overview-title">
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
            .filter((item) => ['projects', 'keys', 'logs'].includes(item.id))
            .map((item) => (
              <Link className="start-card" key={item.id} to={item.path}>
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
              </Link>
            ))}
        </div>
      </section>
      <p className="preview-note">
        레이아웃 미리보기입니다. 실제 프로젝트와 사용량 데이터는 아직 연결되지
        않았습니다.
      </p>
    </>
  )
}
