const paths = {
  home: 'm3 10 9-7 9 7v10H15v-7H9v7H3Z',
  folder: 'M3 7V5h6l2 2h10v13H3Z',
  key: 'M15 3a6 6 0 1 1-4 10l-7 7H2v-3l7-7a6 6 0 0 1 6-7Zm2 4h.01',
  chart: 'M4 20V10m6 10V4m6 16v-7m5 7H2',
  logs: 'M6 3h12v18H6ZM9 7h6M9 11h6M9 15h4',
  plug: 'M8 3v5m8-5v5M6 8h12v3a6 6 0 0 1-6 6v4m-6-10a6 6 0 0 0 6 6',
  settings:
    'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1Z',
  monitor: 'M3 3h18v13H3Zm9 13v5m-4 0h8',
  sun: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm0-6v2m0 16v2M2 12h2m16 0h2M5 5l1 1m12 12 1 1M5 19l1-1M18 6l1-1',
  moon: 'M20 15A9 9 0 0 1 9 4a9 9 0 1 0 11 11Z',
  panel: 'M3 4h18v16H3Zm5 0v16',
  arrow: 'M5 12h14m-5-5 5 5-5 5',
  search: 'M10 3a7 7 0 1 0 0 14 7 7 0 0 0 0-14Zm5 12 6 6',
  close: 'm6 6 12 12M6 18 18 6',
} as const

export type IconName = keyof typeof paths
export function Icon({ name }: { name: IconName }) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  )
}
