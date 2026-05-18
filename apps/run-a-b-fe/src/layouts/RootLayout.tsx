import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'

export function RootLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <div>
      <nav className='fixed flex w-full h-15 bg-white px-40 items-center border-b border-gray-200 z-50'>
        <Link to="/" className='flex items-center font-bold text-lg text-primary-600 mr-10'>
          <div className='mr-3 w-10 h-10 flex items-center justify-center rounded-xl font-bold text-lg text-white bg-[linear-gradient(135deg,var(--color-primary-400),var(--color-primary-600))]'>R</div>
          Run a B
        </Link>

        <Link to="" className='mr-5 text-sm font-medium text-gray-600'>정책 모아보기</Link>
        <Link to="" className='text-sm font-medium text-gray-600'>리포트 모아보기</Link>

        <div className='ml-auto flex items-center gap-2'>
          {user ? (
            <>
              <span className='text-sm text-gray-500 mr-1'>{user.name}님</span>
              <Link
                to="/mypage"
                className='inline-flex items-center justify-center bg-primary-300 p-2 rounded-full border border-gray-300 hover:bg-primary-400 hover:border-gray-400'
                aria-label="마이페이지"
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
              </Link>
              <button
                onClick={handleLogout}
                className='text-sm font-medium text-gray-500 hover:text-red-400 transition-colors px-2'
              >
                로그아웃
              </button>
            </>
          ) : (
            <>
              <Link
                to="/login"
                className='inline-flex items-center justify-center font-semibold text-sm border border-gray-300 py-2 px-4 rounded-2xl hover:bg-gray-50'
              >
                로그인
              </Link>
              <Link
                to="/signup"
                className='inline-flex items-center justify-center font-semibold text-sm bg-primary-600 text-white py-2 px-4 rounded-2xl hover:bg-primary-700'
              >
                회원가입
              </Link>
            </>
          )}
        </div>
      </nav>
      <Outlet />
    </div>
  )
}
