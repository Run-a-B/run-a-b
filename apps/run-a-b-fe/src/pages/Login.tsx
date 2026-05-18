import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/contexts/AuthContext";

export default function Login(){
  const [showPassword, setShowPassword] = useState(false);
  const [saveEmail, setSaveEmail] = useState(false);
  const [email, setEmail] = useState("");
  const { login } = useAuth();
  const navigate = useNavigate();

  function handleSubmit(e: React.SyntheticEvent) {
    e.preventDefault();
    // 임시: 이메일에서 이름 추출, 나머지는 목업 데이터
    login({
      name: email.split("@")[0] || "사용자",
      email,
      industry: "음식업",
      region: "서울",
    });
    navigate("/mypage");
  }

  return(
    <div className="bg-primary-100 min-h-screen pt-25 flex flex-col items-center">
      <div className="flex items-center justify-center w-15 h-15 rounded-2xl font-bold text-2xl text-white bg-[linear-gradient(135deg,var(--color-primary-400),var(--color-primary-600))]">R</div>
      <h4 className="font-bold text-2xl text-primary-700 mt-3">Run a B</h4>
      <p className="text-sm mt-2 text-gray-500">소상공인 정책·지원금 AI 플랫폼</p>

      <div className="bg-white rounded-2xl w-110 mt-5 shadow-lg">
        <form onSubmit={handleSubmit} className="flex flex-col p-8 ">

          <h4 className="font-extrabold text-2xl">로그인</h4>
          <label htmlFor="email" className="text-sm font-normal mt-2">이메일</label>
          <input type="email" name="email" id="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="RunaB@email.com" className="border border-gray-300 rounded-xl px-4 py-3 mt-2 hover:border-primary-500" />

          <label htmlFor="password" className="text-sm font-normal mt-3">비밀번호</label>
          <div className="relative mt-2">
            <input
              type={showPassword ? "text" : "password"}
              name="password"
              id="password"
              placeholder="비밀번호를 입력해 주세요"
              className="border border-gray-300 rounded-xl px-4 py-3 w-full pr-12 hover:border-primary-500"
            />
            <button
              type="button"
              onClick={() => setShowPassword(prev => !prev)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              aria-label={showPassword ? "비밀번호 숨기기" : "비밀번호 보기"}
            >
              {showPassword ? (
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                  <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                </svg>
              ) : (
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
              )}
            </button>
          </div>

          <label className="flex items-center gap-2 mt-4 cursor-pointer text-sm text-gray-600">
            <input
              type="checkbox"
              checked={saveEmail}
              onChange={e => setSaveEmail(e.target.checked)}
              className="w-4 h-4 accent-primary-600"
            />
            이메일 저장
          </label>

          <button type="submit" className="bg-primary-600 py-2 mt-5 text-white rounded-full transition-transform duration-150 hover:-translate-y-0.5 hover:bg-primary-700">로그인</button>

          <div className="flex items-center gap-3 mt-5">
            <hr className="flex-1 border-gray-200" />
            <span className="text-sm text-gray-400">또는</span>
            <hr className="flex-1 border-gray-200" />
          </div>

          <button type="button" className="flex items-center justify-center gap-3 mt-4 border border-gray-200 rounded-xl py-3 hover:bg-gray-50 transition-colors duration-150">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 48 48">
              <path fill="#4285F4" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
              <path fill="#34A853" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
              <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
              <path fill="#EA4335" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.18 1.48-4.97 2.31-8.16 2.31-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
            </svg>
            <span className="text-sm font-medium text-gray-700">Google로 계속하기</span>
          </button>
        </form>
      </div>

      <p className="mt-6 text-sm text-gray-500">
        아직 계정이 없으신가요?{" "}
        <a href="/signup" className="font-bold text-primary-600 hover:underline">무료로 가입하기</a>
      </p>
    </div>
  )
}
