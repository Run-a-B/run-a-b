import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "@/contexts/AuthContext";

const EyeIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
    <circle cx="12" cy="12" r="3"/>
  </svg>
);

const EyeOffIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
    <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
    <line x1="1" y1="1" x2="23" y2="23"/>
  </svg>
);

const CheckIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="20 6 9 17 4 12"/>
  </svg>
);

const ChevronIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-400">
    <polyline points="6 9 12 15 18 9"/>
  </svg>
);

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_REGEX = /^[A-Za-z0-9!@$*]+$/;

const INDUSTRY_OPTIONS = ["음식점", "카페/베이커리", "의류/패션", "뷰티/미용", "교육", "IT/소프트웨어", "제조", "도소매", "기타"];
const REGION_OPTIONS = ["서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"];
const REVENUE_OPTIONS = ["1억 미만", "1억~3억", "3억~5억", "5억~10억", "10억 이상"];
const EMPLOYEE_OPTIONS = ["없음 (나 혼자)", "1~4명", "5~9명", "10~29명", "30명 이상"];

export default function Signup() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [step, setStep] = useState(1);

  // Step 1
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [form, setForm] = useState({ name: "", email: "", age: "", password: "", confirmPassword: "" });
  const [touched, setTouched] = useState({ name: false, email: false, age: false, password: false, confirmPassword: false });

  // Step 2
  const [biz, setBiz] = useState({ status: "사업 중", industry: "", region: "", revenue: "", employees: "" });

  // ── Step 1 validation ──────────────────────────────────────────────
  const errors = {
    name: form.name.length > 0 && (form.name.length < 2 || form.name.length > 20) ? "2~20자로 입력해 주세요" : "",
    email: form.email.length > 0 && !EMAIL_REGEX.test(form.email) ? "올바른 이메일 형식이 아니에요" : "",
    age: form.age.length > 0 && (isNaN(Number(form.age)) || Number(form.age) < 1 || Number(form.age) > 120) ? "올바른 나이를 입력해 주세요" : "",
    password: form.password.length > 0 && !PASSWORD_REGEX.test(form.password) ? "영어·숫자·특수문자(I@$*)만 사용 가능해요" : "",
    confirmPassword: form.confirmPassword.length > 0 && form.password !== form.confirmPassword ? "비밀번호가 일치하지 않아요" : "",
  };

  const step1Valid = Object.values(errors).every(e => e === "") &&
    form.name.length >= 2 && EMAIL_REGEX.test(form.email) &&
    PASSWORD_REGEX.test(form.password) && form.password === form.confirmPassword;

  function handleChange(field: keyof typeof form, value: string) {
    setForm(prev => ({ ...prev, [field]: value }));
  }

  function handleBlur(field: keyof typeof touched) {
    setTouched(prev => ({ ...prev, [field]: true }));
  }

  function handleStep1Submit(e: React.SyntheticEvent) {
    e.preventDefault();
    setTouched({ name: true, email: true, age: true, password: true, confirmPassword: true });
    if (!step1Valid) return;
    setStep(2);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function inputClass(field: keyof typeof errors) {
    const hasError = touched[field] && errors[field];
    return `border rounded-xl px-4 py-3 w-full focus:outline-none transition-colors ${
      hasError
        ? "border-red-400 focus:border-red-500"
        : "border-gray-300 hover:border-primary-500 focus:border-primary-500"
    }`;
  }

  // ── Step 2 ─────────────────────────────────────────────────────────
  const isRunning = biz.status === "사업 중";
  const step2Valid = biz.industry !== "" && biz.region !== "";

  function handleBizChange(field: keyof typeof biz, value: string) {
    setBiz(prev => ({ ...prev, [field]: value }));
  }

  function handleFinalSubmit(e: React.SyntheticEvent) {
    e.preventDefault();
    if (!step2Valid) return;
    login({
      name: form.name,
      email: form.email,
      age: form.age ? Number(form.age) : undefined,
      industry: biz.industry,
      region: biz.region,
    });
    navigate("/mypage");
  }

  // ── Step indicator ─────────────────────────────────────────────────
  const StepIndicator = () => (
    <div className="flex items-start gap-0 mt-8 mb-6">
      <div className="flex flex-col items-center">
        <div className={`w-10 h-10 rounded-full flex items-center justify-center font-bold text-sm ${
          step > 1
            ? "bg-primary-200 text-primary-600 border-2 border-primary-300"
            : "bg-primary-600 text-white"
        }`}>
          {step > 1 ? <CheckIcon /> : "1"}
        </div>
        <span className={`text-xs mt-1.5 font-semibold ${step > 1 ? "text-gray-400" : "text-primary-600"}`}>내 정보</span>
      </div>

      <div className={`w-24 h-0.5 mt-5 mx-1 ${step > 1 ? "bg-primary-400" : "bg-gray-300"}`} />

      <div className="flex flex-col items-center">
        <div className={`w-10 h-10 rounded-full flex items-center justify-center font-bold text-sm ${
          step === 2
            ? "bg-primary-600 text-white"
            : "bg-white border-2 border-gray-200 text-gray-400"
        }`}>
          2
        </div>
        <span className={`text-xs mt-1.5 font-semibold ${step === 2 ? "text-primary-600" : "text-gray-400"}`}>내 사업 정보</span>
      </div>
    </div>
  );

  // ── Select component ───────────────────────────────────────────────
  const SelectField = ({ value, onChange, placeholder, options, disabled }: {
    value: string;
    onChange: (v: string) => void;
    placeholder: string;
    options: string[];
    disabled?: boolean;
  }) => (
    <div className="relative mt-2">
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        disabled={disabled}
        className={`w-full border rounded-xl px-4 py-3 appearance-none focus:outline-none transition-colors pr-10 ${
          disabled
            ? "bg-gray-50 border-gray-200 text-gray-400 cursor-not-allowed"
            : value
              ? "border-gray-300 text-gray-800 hover:border-primary-500 focus:border-primary-500"
              : "border-gray-300 text-gray-400 hover:border-primary-500 focus:border-primary-500"
        }`}
      >
        <option value="" disabled>{placeholder}</option>
        {options.map(o => <option key={o} value={o}>{o}</option>)}
      </select>
      <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2">
        <ChevronIcon />
      </span>
    </div>
  );

  return (
    <div className="bg-primary-100 min-h-screen pt-20 flex flex-col items-center pb-16">

      <StepIndicator />

      {/* ── STEP 1 ── */}
      {step === 1 && (
        <>
          <div className="bg-white rounded-2xl w-110 shadow-lg">
            <form onSubmit={handleStep1Submit} className="flex flex-col p-8">
              <span className="text-xs font-bold text-primary-600 tracking-widest uppercase">Step 1 / 2</span>
              <h3 className="font-extrabold text-2xl mt-3 leading-snug">
                안녕하세요!<br /> 기본 정보를 입력해 주세요
              </h3>
              <p className="text-sm text-gray-500 mt-1.5">입력하신 정보는 AI 분석 리포트 개인화에 활용돼요</p>

              {/* Name */}
              <label htmlFor="name" className="text-sm font-semibold mt-6">
                이름 (닉네임) <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                id="name"
                value={form.name}
                placeholder="홍길동"
                onChange={e => handleChange("name", e.target.value)}
                onBlur={() => handleBlur("name")}
                className={`mt-2 ${inputClass("name")}`}
              />
              {touched.name && errors.name
                ? <p className="text-xs text-red-500 mt-1">{errors.name}</p>
                : <div className="flex gap-2 mt-2">
                    <span className="text-xs border border-gray-200 rounded-full px-3 py-1 text-gray-500">한국어 가능</span>
                    <span className="text-xs border border-gray-200 rounded-full px-3 py-1 text-gray-500">영어 가능</span>
                    <span className="text-xs border border-gray-200 rounded-full px-3 py-1 text-gray-500">2~20자</span>
                  </div>
              }

              {/* Age */}
              <label htmlFor="age" className="text-sm font-semibold mt-5">
                나이
              </label>
              <input
                type="number"
                id="age"
                value={form.age}
                placeholder="예: 35"
                min={1}
                max={120}
                onChange={e => handleChange("age", e.target.value)}
                onBlur={() => handleBlur("age")}
                className={`mt-2 ${inputClass("age")}`}
              />
              {touched.age && errors.age
                ? <p className="text-xs text-red-500 mt-1">{errors.age}</p>
                : <p className="text-xs text-gray-400 mt-1.5">AI 리포트 개인화에 활용돼요 (선택)</p>
              }

              {/* Email */}
              <label htmlFor="email" className="text-sm font-semibold mt-5">
                이메일 <span className="text-red-500">*</span>
              </label>
              <input
                type="email"
                id="email"
                value={form.email}
                placeholder="example@email.com"
                onChange={e => handleChange("email", e.target.value)}
                onBlur={() => handleBlur("email")}
                className={`mt-2 ${inputClass("email")}`}
              />
              {touched.email && errors.email && (
                <p className="text-xs text-red-500 mt-1">{errors.email}</p>
              )}

              {/* Password */}
              <label htmlFor="password" className="text-sm font-semibold mt-5">
                비밀번호 <span className="text-red-500">*</span>
              </label>
              <div className="relative mt-2">
                <input
                  type={showPassword ? "text" : "password"}
                  id="password"
                  value={form.password}
                  placeholder="비밀번호를 입력해 주세요"
                  onChange={e => handleChange("password", e.target.value)}
                  onBlur={() => handleBlur("password")}
                  className={`pr-12 ${inputClass("password")}`}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(prev => !prev)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  aria-label={showPassword ? "비밀번호 숨기기" : "비밀번호 보기"}
                >
                  {showPassword ? <EyeOffIcon /> : <EyeIcon />}
                </button>
              </div>
              {touched.password && errors.password
                ? <p className="text-xs text-red-500 mt-1.5">{errors.password}</p>
                : <p className="text-xs text-gray-400 mt-1.5">영어·숫자·특수문자(I@$*)만 사용 가능</p>
              }

              {/* Confirm password */}
              <label htmlFor="confirmPassword" className="text-sm font-semibold mt-5">
                비밀번호 확인 <span className="text-red-500">*</span>
              </label>
              <div className="relative mt-2">
                <input
                  type={showConfirmPassword ? "text" : "password"}
                  id="confirmPassword"
                  value={form.confirmPassword}
                  placeholder="비밀번호를 입력해 주세요"
                  onChange={e => handleChange("confirmPassword", e.target.value)}
                  onBlur={() => handleBlur("confirmPassword")}
                  className={`pr-12 ${inputClass("confirmPassword")}`}
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(prev => !prev)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  aria-label={showConfirmPassword ? "비밀번호 숨기기" : "비밀번호 보기"}
                >
                  {showConfirmPassword ? <EyeOffIcon /> : <EyeIcon />}
                </button>
              </div>
              {touched.confirmPassword && errors.confirmPassword && (
                <p className="text-xs text-red-500 mt-1">{errors.confirmPassword}</p>
              )}

              <button
                type="submit"
                className="bg-primary-600 py-3.5 mt-8 text-white rounded-full font-semibold hover:bg-primary-700 hover:-translate-y-0.5 transition-all duration-150 disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0"
                disabled={!step1Valid && Object.values(touched).some(Boolean)}
              >
                다음 단계로 →
              </button>
            </form>
          </div>

          <p className="mt-6 text-sm text-gray-500">
            이미 계정이 있으신가요?{" "}
            <Link to="/login" className="font-bold text-primary-600 hover:underline">로그인하기</Link>
          </p>
        </>
      )}

      {/* ── STEP 2 ── */}
      {step === 2 && (
        <div className="bg-white rounded-2xl w-110 shadow-lg">
          <form onSubmit={handleFinalSubmit} className="flex flex-col p-8">

            {/* Greeting banner */}
            <div className="bg-primary-50 rounded-xl px-4 py-3 flex items-center gap-3 mb-5">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="text-primary-500 shrink-0">
                <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
              </svg>
              <div>
                <p className="text-sm font-bold text-primary-700">{form.name}님, 거의 다 왔어요!</p>
                <p className="text-xs text-primary-500 mt-0.5">이제 사업 정보만 입력하면 맞춤 리포트를 받을 수 있어요</p>
              </div>
            </div>

            <span className="text-xs font-bold text-primary-600 tracking-widest uppercase">Step 2 / 2</span>
            <h3 className="font-extrabold text-2xl mt-3 leading-snug">
              내 사업 정보를<br />알려주세요
            </h3>
            <p className="text-sm text-gray-500 mt-1.5">
              정책 관련도 계산과 AI 리포트 생성에 활용됩니다.<br />
              나중에 마이페이지에서 언제든 수정할 수 있어요.
            </p>

            {/* 사업 상태 */}
            <label className="text-sm font-semibold mt-6">
              현재 사업 상태 <span className="text-red-500">*</span>
            </label>
            <div className="grid grid-cols-2 gap-0 mt-2 border border-gray-200 rounded-xl overflow-hidden">
              {["사업 중", "사업 준비 중"].map(status => (
                <button
                  key={status}
                  type="button"
                  onClick={() => handleBizChange("status", status)}
                  className={`py-3 text-sm font-semibold transition-colors ${
                    biz.status === status
                      ? "bg-white text-primary-600 border-2 border-primary-500 rounded-xl -m-px z-10"
                      : "bg-gray-50 text-gray-500 hover:bg-gray-100"
                  }`}
                >
                  {status}
                </button>
              ))}
            </div>

            {/* 업종 */}
            <label className="text-sm font-semibold mt-5">
              직군 (업종) <span className="text-red-500">*</span>
            </label>
            <SelectField
              value={biz.industry}
              onChange={v => handleBizChange("industry", v)}
              placeholder="업종을 선택해 주세요"
              options={INDUSTRY_OPTIONS}
            />

            {/* 지역 */}
            <label className="text-sm font-semibold mt-5">
              지역 <span className="text-red-500">*</span>
            </label>
            <SelectField
              value={biz.region}
              onChange={v => handleBizChange("region", v)}
              placeholder="지역을 선택해 주세요"
              options={REGION_OPTIONS}
            />

            {/* 연 매출 */}
            <label className="text-sm font-semibold mt-5 flex items-center gap-2">
              연 매출
              {isRunning && (
                <span className="text-xs bg-primary-100 text-primary-600 font-semibold px-2 py-0.5 rounded-full">사업 중 전용</span>
              )}
            </label>
            <SelectField
              value={biz.revenue}
              onChange={v => handleBizChange("revenue", v)}
              placeholder="연 매출 범위를 선택해 주세요"
              options={REVENUE_OPTIONS}
              disabled={!isRunning}
            />
            <p className="text-xs text-gray-400 mt-1.5">AI 분석 정확도 향상을 위해 입력해 주세요 (선택)</p>

            {/* 직원 수 */}
            <label className="text-sm font-semibold mt-5 flex items-center gap-2">
              직원 수 (본인 제외)
              {isRunning && (
                <span className="text-xs bg-primary-100 text-primary-600 font-semibold px-2 py-0.5 rounded-full">사업 중 전용</span>
              )}
            </label>
            <SelectField
              value={biz.employees}
              onChange={v => handleBizChange("employees", v)}
              placeholder="직원 수를 선택해 주세요"
              options={EMPLOYEE_OPTIONS}
              disabled={!isRunning}
            />
            <p className="text-xs text-gray-400 mt-1.5">최저임금 영향도 등 인건비 관련 분석에 사용돼요 (선택)</p>

            {/* Buttons */}
            <div className="flex gap-3 mt-8">
              <button
                type="button"
                onClick={() => setStep(1)}
                className="flex-1 py-3.5 rounded-full font-semibold text-gray-600 border border-gray-200 hover:bg-gray-50 transition-colors"
              >
                ← 이전
              </button>
              <button
                type="submit"
                disabled={!step2Valid}
                className="flex-2 flex-grow-[2] py-3.5 rounded-full font-semibold text-white bg-primary-600 hover:bg-primary-700 hover:-translate-y-0.5 transition-all duration-150 disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0"
              >
                가입 완료!
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
