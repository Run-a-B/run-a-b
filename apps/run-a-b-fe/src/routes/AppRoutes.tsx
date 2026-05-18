import { Route, Routes } from 'react-router-dom'

import { RootLayout } from '@/layouts/RootLayout'
import { HomePage } from '@/pages/HomePage'
import Login from '@/pages/Login'
import Signup from '@/pages/Signup'
import MyPage from '@/pages/MyPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<RootLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<Signup />} />
        <Route path="/mypage/*" element={<MyPage />} />
      </Route>
    </Routes>
  )
}
