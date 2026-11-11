import { Route, Routes } from 'react-router'
import ConsoleLayout from '@/layouts/ConsoleLayout'
import HomePage from '@/pages/HomePage'
import PlaceholderPage from '@/pages/PlaceholderPage'
import NotFoundPage from '@/pages/NotFoundPage'
import { navigation } from '@/navigation'

export default function App() {
  return (
    <Routes>
      <Route element={<ConsoleLayout />}>
        <Route index element={<HomePage />} />
        {navigation
          .filter((item) => item.id !== 'home')
          .map((item) => (
            <Route
              key={item.id}
              path={item.path}
              element={<PlaceholderPage title={item.label} icon={item.icon} />}
            />
          ))}
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
