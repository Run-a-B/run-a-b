import { Route, Routes } from 'react-router-dom'

import { RootLayout } from '@/layouts/RootLayout'
import { HomePage } from '@/pages/HomePage'
import Login from '@/pages/Login'

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<RootLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<Login></Login>}></Route>
      </Route>
    </Routes>
  )
}
