import { useState } from "react";
import { useAuth } from "@/contexts/AuthContext";

const INDUSTRY_OPTIONS = [
  "음식점업 (한식·양식·중식 등)", "카페/음료", "의류/패션", "뷰티/미용",
  "교육/학원", "IT/소프트웨어", "제조업", "도소매업", "기타",
];
const REGION_OPTIONS = [
  "서울특별시", "부산광역시", "대구광역시", "인천광역시", "광주광역시",
  "대전광역시", "울산광역시", "세종특별자치시", "경기도", "강원도",
  "충청북도", "충청남도", "전라북도", "전라남도", "경상북도", "경상남도", "제주특별자치도",
];
const REVENUE_OPTIONS = [
  "1,000만원 미만", "1,000만원~5,000만원", "5,000만원~1억원",
  "1억원~3억원", "3억원~5억원", "5억원 이상",
];
const EMPLOYEE_OPTIONS = ["없음 (나 혼자)", "1~4명", "5~9명", "10~29명", "30명 이상"];

const ChevronIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-400 shrink-0">
    <polyline points="6 9 12 15 18 9"/>
  </svg>
);

function SelectField({ value, onChange, placeholder, options, disabled }: {
  value: string; onChange: (v: string) => void;
  placeholder: string; options: string[]; disabled?: boolean;
}) {
  return (
    <div className="relative mt-2">
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        disabled={disabled}
        className={`w-full border rounded-xl px-4 py-3 appearance-none focus:outline-none transition-colors pr-10 text-sm ${
          disabled
            ? "bg-gray-50 border-gray-200 text-gray-400 cursor-not-allowed"
            : value
              ? "border-gray-200 text-gray-800 hover:border-primary-500 focus:border-primary-500"
              : "border-gray-200 text-gray-400 hover:border-primary-500 focus:border-primary-500"
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
}

export default function MyBusinessPage() {
  const { user, login } = useAuth();

  const [status, setStatus] = useState("사업 중");
  const [industry, setIndustry] = useState(user?.industry ?? "");
  const [region, setRegion] = useState(user?.region ?? "");
  const [revenue, setRevenue] = useState("");
  const [employees, setEmployees] = useState("");

  const isRunning = status === "사업 중";

  function handleSave() {
    if (!user) return;
    login({ ...user, industry, region });
  }

  function handleCancel() {
    setIndustry(user?.industry ?? "");
    setRegion(user?.region ?? "");
    setRevenue("");
    setEmployees("");
  }

  return (
    <div className="max-w-xl w-full">
      <h2 className="text-2xl font-extrabold text-gray-900">내 사업 정보</h2>
      <p className="text-sm text-gray-500 mt-1 mb-7">정책 관련도 계산과 AI 리포트 정확도에 직접 영향을 줘요</p>

      {/* 현재 상태 배너 */}
      <div className="bg-primary-50 border border-primary-100 rounded-xl px-5 py-4 flex items-center gap-4 mb-4">
        <div className="w-10 h-10 rounded-xl bg-primary-100 flex items-center justify-center shrink-0">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-primary-600">
            <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/>
            <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
          </svg>
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-xs font-semibold text-primary-500 mb-0.5">현재 사업 상태</p>
          <p className="font-bold text-gray-900 text-base">{status}</p>
          <p className="text-xs text-primary-400 mt-0.5">
            {isRunning ? "매출·직원 수 정보를 입력하면 AI 분석 정확도가 높아져요" : "사업 시작 전 단계예요. 준비되면 사업 중으로 변경해 주세요"}
          </p>
        </div>
        <span className={`text-xs font-semibold px-2.5 py-1 rounded-full border shrink-0 ${
          isRunning ? "text-primary-600 border-primary-300 bg-white" : "text-gray-500 border-gray-300 bg-white"
        }`}>
          {isRunning ? "활성" : "준비 중"}
        </span>
      </div>

      {/* 사업 상태 */}
      <section className="border border-gray-200 rounded-xl overflow-hidden mb-4">
        <div className="flex items-center gap-2 px-5 py-4 border-b border-gray-100">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-500">
            <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/>
            <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
          </svg>
          <span className="font-semibold text-gray-800 text-sm">사업 상태</span>
        </div>
        <div className="px-5 py-5">
          <p className="text-sm font-medium text-gray-700 mb-2">현재 상태</p>
          <div className="grid grid-cols-2 border border-gray-200 rounded-xl overflow-hidden">
            {["사업 중", "사업 준비 중"].map(s => (
              <button
                key={s}
                type="button"
                onClick={() => setStatus(s)}
                className={`py-3 text-sm font-semibold transition-colors ${
                  status === s
                    ? "bg-white text-primary-600 border-2 border-primary-500 rounded-xl -m-px z-10"
                    : "bg-gray-50 text-gray-500 hover:bg-gray-100"
                }`}
              >
                {s}
              </button>
            ))}
          </div>
        </div>
      </section>

      {/* 기본 사업 정보 */}
      <section className="border border-gray-200 rounded-xl overflow-hidden mb-4">
        <div className="flex items-center gap-2 px-5 py-4 border-b border-gray-100">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-500">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
          </svg>
          <span className="font-semibold text-gray-800 text-sm">기본 사업 정보</span>
        </div>
        <div className="px-5 py-5 flex flex-col gap-5">
          <div>
            <label className="text-sm font-medium text-gray-700">
              직군 (업종) <span className="text-red-500">*</span>
            </label>
            <SelectField value={industry} onChange={setIndustry} placeholder="업종을 선택해 주세요" options={INDUSTRY_OPTIONS} />
          </div>
          <div>
            <label className="text-sm font-medium text-gray-700">
              지역 <span className="text-red-500">*</span>
            </label>
            <SelectField value={region} onChange={setRegion} placeholder="지역을 선택해 주세요" options={REGION_OPTIONS} />
          </div>
        </div>
      </section>

      {/* 상세 사업 정보 */}
      <section className="border border-gray-200 rounded-xl overflow-hidden mb-6">
        <div className="flex items-center gap-2 px-5 py-4 border-b border-gray-100">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-500">
            <line x1="18" y1="20" x2="18" y2="10"/>
            <line x1="12" y1="20" x2="12" y2="4"/>
            <line x1="6" y1="20" x2="6" y2="14"/>
          </svg>
          <span className="font-semibold text-gray-800 text-sm">상세 사업 정보</span>
          {isRunning && (
            <span className="ml-auto text-xs bg-primary-100 text-primary-600 font-semibold px-2.5 py-1 rounded-full">사업 중 전용</span>
          )}
        </div>
        <div className="px-5 py-5 flex flex-col gap-5">
          {isRunning && (
            <div className="bg-primary-50 rounded-xl px-4 py-3 flex gap-3">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-primary-500 shrink-0 mt-0.5">
                <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
              </svg>
              <div>
                <p className="text-xs font-bold text-primary-600 mb-0.5">AI 분석 정확도를 높이는 정보예요.</p>
                <p className="text-xs text-primary-400">연 매출과 직원 수를 입력하면 최저임금 영향도, 지원금 수령 가능액 등을 더 정확하게 계산해 드려요.</p>
              </div>
            </div>
          )}
          <div>
            <label className="text-sm font-medium text-gray-700 flex items-center gap-1.5">
              연 매출 <span className="text-xs text-gray-400 font-normal">(선택)</span>
            </label>
            <SelectField value={revenue} onChange={setRevenue} placeholder="연 매출 범위를 선택해 주세요" options={REVENUE_OPTIONS} disabled={!isRunning} />
          </div>
          <div>
            <label className="text-sm font-medium text-gray-700 flex items-center gap-1.5">
              직원 수 (본인 제외) <span className="text-xs text-gray-400 font-normal">(선택)</span>
            </label>
            <SelectField value={employees} onChange={setEmployees} placeholder="직원 수를 선택해 주세요" options={EMPLOYEE_OPTIONS} disabled={!isRunning} />
          </div>
        </div>
      </section>

      {/* 저장 / 취소 */}
      <div className="flex items-center gap-3 mb-6">
        <button onClick={handleCancel} className="text-sm font-medium text-gray-500 hover:text-gray-700 px-4 py-2 transition-colors">
          취소
        </button>
        <button
          onClick={handleSave}
          disabled={!industry || !region}
          className="bg-primary-600 text-white text-sm font-semibold px-6 py-2.5 rounded-full hover:bg-primary-700 hover:-translate-y-0.5 transition-all duration-150 disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0"
        >
          저장하기
        </button>
      </div>

      {/* 활용 현황 */}
      <section className="border border-gray-200 rounded-xl overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100">
          <span className="font-semibold text-gray-800 text-sm">내 사업 정보 활용 현황</span>
        </div>
        <div className="divide-y divide-gray-100">
          {[
            { label: "생성된 AI 리포트", value: "4건" },
            { label: "정책 관련도 분석", value: "12건" },
            { label: "추천된 지원사업", value: "8건" },
            { label: "마지막 정보 업데이트", value: "2026.01.10" },
          ].map(({ label, value }) => (
            <div key={label} className="flex items-center justify-between px-5 py-3.5">
              <span className="text-sm text-gray-600">{label}</span>
              <span className="text-sm font-semibold text-gray-800">{value}</span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
