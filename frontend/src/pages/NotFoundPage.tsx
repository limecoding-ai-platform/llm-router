import { Link } from 'react-router'
import { Icon } from '@/components/Icon'

export default function NotFoundPage() {
  return (
    <section className="empty-state">
      <span className="not-found-code" aria-hidden="true">
        404
      </span>
      <h2>페이지를 찾을 수 없습니다</h2>
      <p>주소가 올바른지 확인하거나 홈으로 돌아가 주세요.</p>
      <Link className="secondary-button" to="/">
        홈으로 돌아가기
        <Icon name="arrow" />
      </Link>
    </section>
  )
}
