import { Link } from 'react-router'
import { Icon, type IconName } from '@/components/Icon'

export default function PlaceholderPage({
  title,
  icon,
}: {
  title: string
  icon: IconName
}) {
  return (
    <section className="empty-state">
      <span className="empty-icon">
        <Icon name={icon} />
      </span>
      <h2>{title}</h2>
      <p>이 공간에 {title} 기능이 추가될 예정입니다.</p>
      <Link className="secondary-button" to="/">
        홈으로 돌아가기
        <Icon name="arrow" />
      </Link>
    </section>
  )
}
