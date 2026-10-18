import { lazy, Suspense, useEffect } from 'react'
import { Route, Routes, useLocation } from 'react-router-dom'
import { RootLayout } from '@/layouts/RootLayout'

const HomePage        = lazy(() => import('@/pages/HomePage').then(m => ({ default: m.HomePage })))
const Login           = lazy(() => import('@/pages/Login'))
const Signup          = lazy(() => import('@/pages/Signup'))
const MyPage          = lazy(() => import('@/pages/MyPage'))
const Policies        = lazy(() => import('@/pages/Policies'))
const PolicyDetail    = lazy(() => import('@/pages/PolicyDetail'))
const PolicyDiagnosis = lazy(() => import('@/pages/PolicyDiagnosis'))
const PolicyChecklist = lazy(() => import('@/pages/PolicyChecklist'))
const ReportList      = lazy(() => import('@/pages/ReportList'))
const ReportDetail    = lazy(() => import('@/pages/ReportDetail'))

// 앱 로드 직후 백그라운드에서 모든 청크 미리 받아둠 → 이후 이동 즉각 반응
function usePrefetchRoutes() {
  useEffect(() => {
    const timer = setTimeout(() => {
      import('@/pages/Policies')
      import('@/pages/PolicyDetail')
      import('@/pages/PolicyDiagnosis')
      import('@/pages/MyPage')
      import('@/pages/Login')
      import('@/pages/Signup')
      import('@/pages/ReportList')
      import('@/pages/ReportDetail')
      import('@/pages/PolicyChecklist')
    }, 2000) // 초기 렌더 안정화 후 2초 뒤에 prefetch
    return () => clearTimeout(timer)
  }, [])
}

function PageSpinner() {
  const { pathname } = useLocation()
  if (/^\/policies\/[^/]+\/diagnosis$/.test(pathname)) {
    return (
      <div className="bg-gray-50 min-h-screen pt-15 px-40">
        <div className="py-20 max-w-3xl mx-auto">
          <h1 className="text-2xl font-bold text-gray-900">60초 정책 진단</h1>
          <div className="mt-6 bg-white rounded-2xl border border-gray-200 px-6 py-12 text-center">
            <div className="mx-auto w-9 h-9 rounded-full border-[3px] border-gray-200 border-t-primary-600 animate-spin" />
            <p className="mt-4 text-sm text-gray-500">정책 정보를 불러오고 있어요</p>
          </div>
        </div>
      </div>
    )
  }
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
          <Route path="/policies/:id/diagnosis" element={<PolicyDiagnosis />} />
          <Route path="/policies/:id/checklist" element={<PolicyChecklist />} />
          <Route path="/reports"                element={<ReportList />} />
          <Route path="/reports/:id"            element={<ReportDetail />} />
        </Route>
      </Routes>
    </Suspense>
  )
}
