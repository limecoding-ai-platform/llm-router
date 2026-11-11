import type { IconName } from '@/components/Icon'

export const navigation: {
  id: string
  path: string
  label: string
  icon: IconName
  group: string
}[] = [
  { id: 'home', path: '/', label: '홈', icon: 'home', group: '프로젝트' },
  {
    id: 'projects',
    path: '/projects',
    label: '프로젝트',
    icon: 'folder',
    group: '프로젝트',
  },
  {
    id: 'keys',
    path: '/keys',
    label: 'API 키',
    icon: 'key',
    group: '프로젝트',
  },
  {
    id: 'usage',
    path: '/usage',
    label: '사용량',
    icon: 'chart',
    group: '프로젝트',
  },
  {
    id: 'logs',
    path: '/logs',
    label: '요청 기록',
    icon: 'logs',
    group: '프로젝트',
  },
  {
    id: 'providers',
    path: '/providers',
    label: '제공업체 연결',
    icon: 'plug',
    group: '관리',
  },
  {
    id: 'settings',
    path: '/settings',
    label: '설정',
    icon: 'settings',
    group: '관리',
  },
]
