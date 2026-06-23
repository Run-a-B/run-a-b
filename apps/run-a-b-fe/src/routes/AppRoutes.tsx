import { lazy, Suspense, useEffect } from 'react'
import { Route, Routes } from 'react-router-dom'
import { RootLayout } from '@/layouts/RootLayout'

const HomePage        = lazy(() => import('@/pages/HomePage').then(m => ({ default: m.HomePage })))
const Login           = lazy(() => import('@/pages/Login'))
const Signup          = lazy(() => import('@/pages/Signup'))
const MyPage          = lazy(() => import('@/pages/MyPage'))
const Policies        = lazy(() => import('@/pages/Policies'))
const PolicyDetail    = lazy(() => import('@/pages/PolicyDetail'))
const PolicyChecklist = lazy(() => import('@/pages/PolicyChecklist'))
const ReportList      = lazy(() => import('@/pages/ReportList'))

// 앱 로드 직후 백그라운드에서 모든 청크 미리 받아둠 → 이후 이동 즉각 반응
function usePrefetchRoutes() {
  useEffect(() => {
    const timer = setTimeout(() => {
      import('@/pages/Policies')
      import('@/pages/PolicyDetail')
      import('@/pages/MyPage')
      import('@/pages/Login')
      import('@/pages/Signup')
      import('@/pages/ReportList')
      import('@/pages/PolicyChecklist')
    }, 2000) // 초기 렌더 안정화 후 2초 뒤에 prefetch
    return () => clearTimeout(timer)
  }, [])
}

function PageSpinner() {
  return (
    <div className="flex items-center justify-center min-h-screen">
      <div className="w-8 h-8 rounded-full border-[3px] border-gray-200 border-t-primary-600 animate-spin" />
    </div>
  )
}

export function AppRoutes() {
  usePrefetchRoutes()

  return (
    <Suspense fallback={<PageSpinner />}>
      <Routes>
        <Route element={<RootLayout />}>
          <Route path="/"                       element={<HomePage />} />
          <Route path="/login"                  element={<Login />} />
          <Route path="/signup"                 element={<Signup />} />
          <Route path="/mypage/*"               element={<MyPage />} />
          <Route path="/policies"               element={<Policies />} />
          <Route path="/policies/:id"           element={<PolicyDetail />} />
          <Route path="/policies/:id/checklist" element={<PolicyChecklist />} />
          <Route path="/reports"                element={<ReportList />} />
        </Route>
      </Routes>
    </Suspense>
  )
}
