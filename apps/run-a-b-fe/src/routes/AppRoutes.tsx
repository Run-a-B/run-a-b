import { Route, Routes } from 'react-router-dom'

import { RootLayout } from '@/layouts/RootLayout'
import { HomePage } from '@/pages/HomePage'
import Login from '@/pages/Login'
import Signup from '@/pages/Signup'
import MyPage from '@/pages/MyPage'
import Policies from '@/pages/Policies'
import PolicyDetail from '@/pages/PolicyDetail'
import ReportList from '@/pages/ReportList'

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<RootLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<Signup />} />
        <Route path="/mypage/*" element={<MyPage />} />
        <Route path="/policies" element={<Policies />} />
        <Route path="/policies/:id" element={<PolicyDetail />} />
        <Route path="/reports" element={<ReportList />} />
      </Route>
    </Routes>
  )
}
