import { Link, Outlet } from 'react-router-dom'

export function RootLayout() {
  return (
    <div>
      <nav className='fixed flex w-full h-15 bg-white px-40 items-center border-b border-gray-200'>
        <Link to="/" className='flex items-center font-bold text-lg text-primary-600 mr-10'>
        <div className='mr-3 w-10 h-10 flex items-center justify-center rounded-xl font-bold text-lg text-white bg-[linear-gradient(135deg,var(--color-primary-400),var(--color-primary-600))]'>R</div>
          Run a B
        </Link>

        <Link to={""} className='mr-5 text-sm font-medium text-gray-600'>정책 모아보기</Link>
        <Link to={""} className='text-sm font-medium text-gray-600'>리포트 모아보기</Link>

        <Link to={""} className='ml-auto inline-flex items-center justify-center font-semibold text-sm border border-gray-300 py-2 px-4 rounded-2xl hover:bg-gray-50 hover:border-gray-300'>로그인</Link>
        <Link to={""} className='inline-flex items-center justify-center bg-primary-300 p-2 rounded-full border border-gray-300 ml-2 hover:bg-primary-400 hover:border-gray-400'>
          <img src="/user-run.svg" alt="user.svg" className='w-5'/>
        </Link>
      </nav>
        <Outlet />
    </div>
  )
}
