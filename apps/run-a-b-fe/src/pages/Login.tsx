import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/contexts/AuthContext";
import api from "@/lib/api";
import { GoogleLogin } from "@react-oauth/google";
import type { CredentialResponse } from "@react-oauth/google";
import { loginWithGoogleCredential } from "@/features/auth/google";
import { createEmailLoginSubmitter } from "@/features/auth/login";
import axios from "axios";

const GOOGLE_LOGIN_ENABLED = Boolean(import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim());

function GoogleLoginButton() {
  const { login } = useAuth();
  const navigate = useNavigate();
  async function handleGoogleSuccess(response: CredentialResponse) {
    try {
      const res = await loginWithGoogleCredential(api, response.credential);
      const { access_token, user } = res.data.data;
      localStorage.setItem("access_token", access_token);
      login(user);
      navigate("/mypage");
    } catch (err: unknown) {
      const message = axios.isAxiosError<{ message?: string }>(err)
        ? err.response?.data?.message
        : err instanceof Error ? err.message : undefined;
      alert(message || "구글 로그인에 실패했어요");
    }
  }

  return (
    <div className="flex justify-center mt-4">
      <GoogleLogin onSuccess={handleGoogleSuccess} onError={() => alert("구글 로그인에 실패했어요")}
        text="continue_with" shape="pill" size="large" />
    </div>
  );
}

export default function Login() {
  const [showPassword, setShowPassword] = useState(false);
  const [saveEmail, setSaveEmail] = useState(() => Boolean(localStorage.getItem("saved_login_email")));
  const [email, setEmail] = useState(() => localStorage.getItem("saved_login_email") ?? "");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitLogin] = useState(() => createEmailLoginSubmitter(api, localStorage));
  const { login } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(e: React.SyntheticEvent) {
    e.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    try {
      const res = await submitLogin(email, password, saveEmail);
      const { access_token, user } = res.data.data;
      localStorage.setItem("access_token", access_token);
      login(user);
      navigate("/mypage");
    } catch (err: unknown) {
      const message = axios.isAxiosError<{ message?: string }>(err)
        ? err.response?.data?.message
        : undefined;
      alert(message || "로그인에 실패했어요");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="bg-primary-100 min-h-screen pt-35 flex flex-col items-center">
      <h1 className="text-primary-600 text-5xl" style={{ fontFamily: '"Grand Hotel", cursive', lineHeight: 1 }}>Run a B</h1>
      <p className="text-sm mt-3 text-gray-500">소상공인 정책·지원금 AI 플랫폼</p>

      <div className="bg-white rounded-2xl w-110 mt-5 shadow-lg">
        <form onSubmit={handleSubmit} className="flex flex-col p-8 ">

          <h4 className="font-extrabold text-2xl">로그인</h4>
          <label htmlFor="email" className="text-sm font-normal mt-2">이메일</label>
          <input type="email" name="email" id="email" required autoComplete="username" disabled={submitting} value={email} onChange={e => setEmail(e.target.value)} placeholder="RunaB@email.com" className="border border-gray-300 rounded-xl px-4 py-3 mt-2 hover:border-primary-500" />

          <label htmlFor="password" className="text-sm font-normal mt-3">비밀번호</label>
          <div className="relative mt-2">
            <input
              type={showPassword ? "text" : "password"}
              name="password"
              id="password"
              required
              autoComplete="current-password"
              disabled={submitting}
              value={password}
              onChange={e => setPassword(e.target.value)}
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
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
                  <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
                  <line x1="1" y1="1" x2="23" y2="23" />
                </svg>
              ) : (
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
              )}
            </button>
          </div>

          <label className="flex items-center gap-2 mt-4 cursor-pointer text-sm text-gray-600">
            <input
              type="checkbox"
              checked={saveEmail}
              disabled={submitting}
              onChange={e => setSaveEmail(e.target.checked)}
              className="w-4 h-4 accent-primary-600"
            />
            이메일 저장
          </label>

          <button type="submit" disabled={submitting} className="bg-primary-600 py-2 mt-5 text-white rounded-full transition-transform duration-150 hover:-translate-y-0.5 hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed">{submitting ? "로그인 중..." : "로그인"}</button>

          {GOOGLE_LOGIN_ENABLED && (
            <>
              <div className="flex items-center gap-3 mt-5">
                <hr className="flex-1 border-gray-200" />
                <span className="text-sm text-gray-400">또는</span>
                <hr className="flex-1 border-gray-200" />
              </div>

              <GoogleLoginButton />
            </>
          )}
        </form>
      </div>

      <p className="mt-6 text-sm text-gray-500">
        아직 계정이 없으신가요?{" "}
        <a href="/signup" className="font-bold text-primary-600 hover:underline">무료로 가입하기</a>
      </p>
    </div>
  )
}
