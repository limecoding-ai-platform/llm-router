import { useEffect, useState } from 'react'

export type Theme = 'system' | 'light' | 'dark'
function readTheme(): Theme {
  try {
    const stored = localStorage.getItem('router-theme')
    return stored === 'light' || stored === 'dark' ? stored : 'system'
  } catch {
    return 'system'
  }
}
export function useTheme() {
  const [theme, setTheme] = useState<Theme>(readTheme)
  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const apply = () => {
      document.documentElement.dataset.theme =
        theme === 'system' ? (media.matches ? 'dark' : 'light') : theme
    }
    apply()
    media.addEventListener('change', apply)
    try {
      localStorage.setItem('router-theme', theme)
    } catch {
      /* Storage can be unavailable. */
    }
    return () => media.removeEventListener('change', apply)
  }, [theme])
  return { theme, setTheme }
}
