import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";

const EyeIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
    <circle cx="12" cy="12" r="3"/>
  </svg>
);

const EyeOffIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
    <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
    <line x1="1" y1="1" x2="23" y2="23"/>
  </svg>
);

const PASSWORD_REGEX = /^[A-Za-z0-9!@$*]{6,}$/;

export default function MyInfoPage() {
  const { user, login } = useAuth();

  const [name, setName] = useState(user?.name ?? "");
  const [age, setAge] = useState(user?.age ? String(user.age) : "");
  const [saved, setSaved] = useState(false);
  const [isPasswordOpen, setIsPasswordOpen] = useState(false);

  useEffect(() => {
    if (!saved) return;
    const t = setTimeout(() => setSaved(false), 2500);
    return () => clearTimeout(t);
  }, [saved]);
  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [pw, setPw] = useState({ current: "", next: "", confirm: "" });

  const pwError = pw.next.length > 0 && !PASSWORD_REGEX.test(pw.next)
    ? "영어·숫자·특수문자(I@$*) 6자 이상이어야 해요"
    : "";
  const pwMatchError = pw.confirm.length > 0 && pw.next !== pw.confirm
    ? "비밀번호가 일치하지 않아요"
    : "";

  function handleSave() {
    if (!user) return;
    login({ ...user, name, age: age ? Number(age) : undefined });
    setPw({ current: "", next: "", confirm: "" });
    setIsPasswordOpen(false);
    setSaved(true);
  }

  function handleCancel() {
    setName(user?.name ?? "");
    setAge(user?.age ? String(user.age) : "");
    setPw({ current: "", next: "", confirm: "" });
    setIsPasswordOpen(false);
  }

  const inputBase = "w-full border border-gray-200 rounded-xl px-4 py-3 focus:outline-none focus:border-primary-500 hover:border-primary-400 transition-colors text-sm";

  return (
    <div className="max-w-xl">
      {/* 저장 완료 토스트 */}
      <div className={`fixed bottom-8 right-8 z-50 flex items-center gap-3 bg-gray-900 text-white text-sm font-medium px-5 py-3.5 rounded-2xl shadow-xl transition-all duration-300 ${saved ? "opacity-100 translate-y-0" : "opacity-0 translate-y-3 pointer-events-none"}`}>
        <div className="w-5 h-5 rounded-full bg-green-400 flex items-center justify-center shrink-0">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
        </div>
        내 정보가 저장되었어요
      </div>
      <h2 className="text-2xl font-extrabold text-gray-900">내 정보</h2>
      <p className="text-sm text-gray-500 mt-1 mb-7">이름, 이메일, 비밀번호를 관리할 수 있어요</p>

      {/* 기본 계정 정보 */}
      <section className="border border-gray-200 rounded-xl overflow-hidden mb-4">
        <div className="flex items-center gap-2 px-5 py-4 border-b border-gray-100">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-500">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
          <span className="font-semibold text-gray-800 text-sm">기본 계정 정보</span>
        </div>

        <div className="px-5 py-5 flex flex-col gap-5">
          {/* 이름 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">이름 (닉네임)</label>
            <input
              type="text"
              value={name}
              onChange={e => setName(e.target.value)}
              className={inputBase}
            />
            <p className="text-xs text-gray-400 mt-1.5">한국어·영어 2~20자</p>
          </div>

          {/* 나이 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">나이</label>
            <input
              type="number"
              value={age}
              min={1}
              max={120}
              placeholder="예: 35"
              onChange={e => setAge(e.target.value)}
              className={inputBase}
            />
            <p className="text-xs text-gray-400 mt-1.5">AI 리포트 개인화에 활용돼요 (선택)</p>
          </div>

          {/* 이메일 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">이메일</label>
            <div className="relative">
              <input
                type="email"
                value={user?.email ?? ""}
                readOnly
                className={`${inputBase} bg-gray-50 text-gray-500 cursor-default pr-20`}
              />
              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs bg-primary-100 text-primary-600 font-semibold px-2.5 py-1 rounded-full flex items-center gap-1">
                인증됨
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </span>
            </div>
          </div>
        </div>
      </section>

      {/* 비밀번호 변경 */}
      <section className="border border-gray-200 rounded-xl overflow-hidden mb-6">
        <div className="flex items-center gap-2 px-5 py-4 border-b border-gray-100">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-500">
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
            <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
          </svg>
          <span className="font-semibold text-gray-800 text-sm">비밀번호 변경</span>
        </div>

        <div className="px-5 py-3">
          <button
            type="button"
            onClick={() => setIsPasswordOpen(v => !v)}
            className="w-full flex items-center justify-between py-2.5 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            비밀번호를 변경하려면 클릭하세요
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={`transition-transform ${isPasswordOpen ? "rotate-180" : ""}`}>
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>

          {isPasswordOpen && (
            <div className="flex flex-col gap-4 pb-4 pt-2 border-t border-gray-100 mt-1">
              {/* 현재 비밀번호 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">현재 비밀번호</label>
                <div className="relative">
                  <input
                    type={showCurrent ? "text" : "password"}
                    value={pw.current}
                    onChange={e => setPw(p => ({ ...p, current: e.target.value }))}
                    placeholder="현재 비밀번호를 입력해 주세요"
                    className={`${inputBase} pr-11`}
                  />
                  <button type="button" onClick={() => setShowCurrent(v => !v)} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                    {showCurrent ? <EyeOffIcon /> : <EyeIcon />}
                  </button>
                </div>
              </div>

              {/* 새 비밀번호 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">새 비밀번호</label>
                <div className="relative">
                  <input
                    type={showNew ? "text" : "password"}
                    value={pw.next}
                    onChange={e => setPw(p => ({ ...p, next: e.target.value }))}
                    placeholder="새 비밀번호를 입력해 주세요"
                    className={`${inputBase} pr-11 ${pwError ? "border-red-400 focus:border-red-500" : ""}`}
                  />
                  <button type="button" onClick={() => setShowNew(v => !v)} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                    {showNew ? <EyeOffIcon /> : <EyeIcon />}
                  </button>
                </div>
                {pwError
                  ? <p className="text-xs text-red-500 mt-1.5">{pwError}</p>
                  : <p className="text-xs text-gray-400 mt-1.5">영어·숫자·특수문자(I@$*) 6자 이상</p>
                }
              </div>

              {/* 새 비밀번호 확인 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">새 비밀번호 확인</label>
                <div className="relative">
                  <input
                    type={showConfirm ? "text" : "password"}
                    value={pw.confirm}
                    onChange={e => setPw(p => ({ ...p, confirm: e.target.value }))}
                    placeholder="새 비밀번호를 다시 입력해 주세요"
                    className={`${inputBase} pr-11 ${pwMatchError ? "border-red-400 focus:border-red-500" : ""}`}
                  />
                  <button type="button" onClick={() => setShowConfirm(v => !v)} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                    {showConfirm ? <EyeOffIcon /> : <EyeIcon />}
                  </button>
                </div>
                {pwMatchError && <p className="text-xs text-red-500 mt-1">{pwMatchError}</p>}
              </div>
            </div>
          )}
        </div>
      </section>

      {/* 저장 / 취소 */}
      <div className="flex items-center gap-3 mb-6">
        <button onClick={handleCancel} className="text-sm font-medium text-gray-500 hover:text-gray-700 px-4 py-2 transition-colors">
          취소
        </button>
        <button
          onClick={handleSave}
          disabled={name.trim().length < 2}
          className="bg-primary-600 text-white text-sm font-semibold px-6 py-2.5 rounded-full hover:bg-primary-700 hover:-translate-y-0.5 transition-all duration-150 disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0"
        >
          저장하기
        </button>
      </div>

      {/* 위험 구역 */}
      <section className="border border-red-200 bg-red-50 rounded-xl px-5 py-4">
        <div className="flex items-center gap-1.5 mb-1">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-red-500">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
            <line x1="12" y1="9" x2="12" y2="13"/>
            <line x1="12" y1="17" x2="12.01" y2="17"/>
          </svg>
          <p className="font-semibold text-red-500 text-sm">위험 구역</p>
        </div>
        <p className="text-xs text-gray-500 mb-3">계정을 삭제하면 모든 리포트, 저장된 정책 정보가 영구적으로 삭제돼요. 이 작업은 되돌릴 수 없어요.</p>
        <button className="text-sm font-semibold text-red-500 border border-red-300 px-4 py-2 rounded-full hover:bg-red-100 transition-colors">
          계정 삭제
        </button>
      </section>
    </div>
  );
}
