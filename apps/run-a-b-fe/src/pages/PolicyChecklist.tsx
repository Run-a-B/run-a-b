import { useState, useEffect } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import { MOCK_POLICIES, POLICY_DETAILS } from "@/data/policies";
import api from "@/lib/api";

const CATEGORY_COLORS: Record<string, string> = {
  최저임금: "bg-gray-100 text-gray-600",
  "노동·복지": "bg-blue-100 text-blue-700",
  "대출·자금": "bg-indigo-100 text-indigo-700",
  에너지: "bg-orange-100 text-orange-700",
  디지털: "bg-violet-100 text-violet-700",
  세금: "bg-green-100 text-green-700",
  창업지원: "bg-emerald-100 text-emerald-700",
  임차료: "bg-pink-100 text-pink-700",
  교육: "bg-sky-100 text-sky-700",
};

export default function PolicyChecklist() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const policyId = Number(id);

  const mockPolicy = MOCK_POLICIES.find((p) => p.id === policyId);
  const mockDetail = POLICY_DETAILS[policyId];

  // 실 API 정책 제목 (mock에 없을 때 fallback)
  const [apiTitle, setApiTitle] = useState<string | null>(null);
  const [apiUrl, setApiUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!mockPolicy) {
      api.get(`/api/v1/policies/${policyId}`)
        .then((res) => {
          setApiTitle(res.data.data.title);
          setApiUrl(res.data.data.detailUrl || res.data.data.applicationUrl || null);
        })
        .catch(() => {});
    }
  }, [policyId, mockPolicy]);

  const [checked, setChecked] = useState<Record<string, boolean>>({});

  // 실 API 정책 — mock 체크리스트 없음, 준비 중 UI 표시
  if (!mockPolicy || !mockDetail) {
    const title = apiTitle ?? "정책 로딩 중...";
    return (
      <div className="bg-gray-50 min-h-screen">
        <div className="px-40 py-4">
          <nav className="flex items-center gap-1.5 text-sm text-gray-500">
            <Link to="/" className="hover:text-gray-700 transition-colors">홈</Link>
            <span className="text-gray-300">›</span>
            <Link to="/policies" className="hover:text-gray-700 transition-colors">정책 모아보기</Link>
            <span className="text-gray-300">›</span>
            <Link to={`/policies/${policyId}`} className="hover:text-gray-700 transition-colors truncate max-w-xs">{title}</Link>
            <span className="text-gray-300">›</span>
            <span className="text-gray-700 font-medium">신청 준비</span>
          </nav>
        </div>
        <div className="flex flex-col items-center justify-center py-24 gap-4">
          <div className="w-14 h-14 rounded-2xl bg-primary-50 flex items-center justify-center">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="text-primary-400">
              <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>
              <rect x="8" y="2" width="8" height="4" rx="1" ry="1"/>
              <line x1="9" y1="12" x2="15" y2="12"/>
              <line x1="9" y1="16" x2="13" y2="16"/>
            </svg>
          </div>
          <div className="text-center">
            <p className="text-base font-bold text-gray-700 mb-1">신청 체크리스트 준비 중이에요</p>
            <p className="text-sm text-gray-400 leading-6">
              이 정책의 맞춤 체크리스트가 곧 제공될 예정이에요.<br />
              지금은 원문 공고에서 직접 신청 서류를 확인해 주세요.
            </p>
          </div>
          <div className="flex gap-3 mt-2">
            <button
              onClick={() => navigate(`/policies/${policyId}`)}
              className="px-5 py-2.5 text-sm font-semibold text-gray-600 border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors"
            >
              정책 상세 보기
            </button>
            {apiUrl && (
              <a
                href={apiUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="px-5 py-2.5 text-sm font-semibold text-white bg-primary-600 hover:bg-primary-700 rounded-xl transition-colors"
              >
                원문 공고 보기
              </a>
            )}
          </div>
        </div>
      </div>
    );
  }

  const policy = mockPolicy;
  const detail = mockDetail;

  const required = detail.applicationChecklist.filter((i) => i.required);
  const optional = detail.applicationChecklist.filter((i) => !i.required);
  const requiredChecked = required.filter((i) => checked[i.id]).length;
  const totalChecked = detail.applicationChecklist.filter((i) => checked[i.id]).length;
  const allRequiredDone = requiredChecked === required.length;
  const categoryColor = CATEGORY_COLORS[policy.category] ?? "bg-gray-100 text-gray-600";

  function toggle(id: string) {
    setChecked((prev) => ({ ...prev, [id]: !prev[id] }));
  }

  return (
    <div className="bg-gray-50 min-h-screen pt-15">
      {/* Breadcrumb */}
      <div className="px-40 py-4">
        <nav className="flex items-center gap-1.5 text-sm text-gray-500">
          <Link to="/" className="hover:text-gray-700 transition-colors">홈</Link>
          <span className="text-gray-300">›</span>
          <Link to="/policies" className="hover:text-gray-700 transition-colors">정책 모아보기</Link>
          <span className="text-gray-300">›</span>
          <Link to={`/policies/${policyId}`} className="hover:text-gray-700 transition-colors truncate max-w-xs">{policy.title}</Link>
          <span className="text-gray-300">›</span>
          <span className="text-gray-700 font-medium">신청 준비</span>
        </nav>
      </div>

      <div className="px-40 py-8 flex gap-6 items-start">
        {/* Left: Checklist */}
        <div className="flex-1 min-w-0 flex flex-col gap-4">
          {/* Policy header card */}
          <div className="bg-white rounded-2xl border border-gray-200 px-6 py-5">
            <div className="flex items-center gap-2 mb-3">
              <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${categoryColor}`}>{policy.category}</span>
              <span className="text-xs text-gray-400">{detail.applicationPeriod}</span>
            </div>
            <h1 className="text-lg font-bold text-gray-900 leading-snug mb-1">{policy.title}</h1>
            <p className="text-sm text-gray-500">{detail.department}</p>
          </div>

          {/* Required documents */}
          <div className="bg-white rounded-2xl border border-gray-200 overflow-hidden">
            <div className="flex items-center gap-2 px-6 py-4 border-b border-gray-100">
              <div className="w-2 h-2 rounded-full bg-red-400" />
              <span className="text-sm font-bold text-gray-800">필수 서류</span>
              <span className="ml-auto text-xs font-semibold text-primary-600">{requiredChecked}/{required.length} 완료</span>
            </div>
            <div className="divide-y divide-gray-100">
              {required.map((item) => (
                <ChecklistRow key={item.id} item={item} checked={!!checked[item.id]} onToggle={() => toggle(item.id)} required />
              ))}
            </div>
          </div>

          {/* Optional documents */}
          {optional.length > 0 && (
            <div className="bg-white rounded-2xl border border-gray-200 overflow-hidden">
              <div className="flex items-center gap-2 px-6 py-4 border-b border-gray-100">
                <div className="w-2 h-2 rounded-full bg-gray-300" />
                <span className="text-sm font-bold text-gray-800">선택 서류</span>
                <span className="text-xs text-gray-400 ml-1">(해당 시 제출)</span>
              </div>
              <div className="divide-y divide-gray-100">
                {optional.map((item) => (
                  <ChecklistRow key={item.id} item={item} checked={!!checked[item.id]} onToggle={() => toggle(item.id)} required={false} />
                ))}
              </div>
            </div>
          )}

          {/* Tips */}
          <div className="bg-amber-50 border border-amber-100 rounded-2xl px-6 py-4 flex gap-3">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-amber-500 shrink-0 mt-0.5">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            <div>
              <p className="text-sm font-semibold text-amber-800 mb-1">신청 전 확인 사항</p>
              <ul className="text-xs text-amber-700 leading-6 space-y-1">
                <li>• 서류는 최근 3개월 이내 발급본을 준비해 주세요.</li>
                <li>• 원본 서류가 필요한 경우 방문 접수 시 지참하세요.</li>
                <li>• 신청 기간 및 예산 현황에 따라 마감될 수 있으니 서두르세요.</li>
              </ul>
            </div>
          </div>
        </div>

        {/* Right: Progress & CTA */}
        <div className="w-80 shrink-0 flex flex-col gap-4 sticky top-6">
          {/* Progress card */}
          <div className="bg-white rounded-2xl border border-gray-200 px-5 py-5">
            <p className="text-sm font-bold text-gray-800 mb-4">준비 현황</p>

            {/* Progress ring area */}
            <div className="flex items-center gap-4 mb-4">
              <div className="relative w-16 h-16 shrink-0">
                <svg width="64" height="64" viewBox="0 0 64 64" className="-rotate-90">
                  <circle cx="32" cy="32" r="26" fill="none" stroke="#f3f4f6" strokeWidth="6" />
                  <circle
                    cx="32" cy="32" r="26"
                    fill="none"
                    stroke={allRequiredDone ? "#22c55e" : "#6C63FF"}
                    strokeWidth="6"
                    strokeDasharray={`${2 * Math.PI * 26}`}
                    strokeDashoffset={`${2 * Math.PI * 26 * (1 - totalChecked / detail.applicationChecklist.length)}`}
                    strokeLinecap="round"
                    style={{ transition: "stroke-dashoffset 0.4s ease" }}
                  />
                </svg>
                <div className="absolute inset-0 flex items-center justify-center">
                  <span className="text-sm font-bold text-gray-800">{totalChecked}/{detail.applicationChecklist.length}</span>
                </div>
              </div>
              <div>
                <p className="text-sm font-semibold text-gray-800">
                  {allRequiredDone ? "필수 서류 완료!" : `필수 서류 ${requiredChecked}/${required.length} 준비됨`}
                </p>
                <p className="text-xs text-gray-400 mt-0.5">
                  {allRequiredDone ? "신청 페이지로 이동할 수 있어요" : `${required.length - requiredChecked}개 서류가 남았어요`}
                </p>
              </div>
            </div>

            {/* Required items summary */}
            <div className="flex flex-col gap-1.5 mb-4">
              {required.map((item) => (
                <div key={item.id} className="flex items-center gap-2">
                  <div className={`w-4 h-4 rounded-full flex items-center justify-center shrink-0 ${checked[item.id] ? "bg-green-400" : "bg-gray-100"}`}>
                    {checked[item.id] && (
                      <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round">
                        <polyline points="20 6 9 17 4 12"/>
                      </svg>
                    )}
                  </div>
                  <span className={`text-xs truncate ${checked[item.id] ? "text-gray-400 line-through" : "text-gray-600"}`}>{item.label}</span>
                </div>
              ))}
            </div>

            {/* CTA button */}
            <a
              href={detail.applicationUrl}
              target="_blank"
              rel="noopener noreferrer"
              className={`w-full flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-semibold transition-colors ${
                allRequiredDone
                  ? "bg-primary-600 hover:bg-primary-700 text-white"
                  : "bg-gray-100 text-gray-400 pointer-events-none"
              }`}
            >
              신청 페이지로 이동
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
                <polyline points="15 3 21 3 21 9"/>
                <line x1="10" y1="14" x2="21" y2="3"/>
              </svg>
            </a>
            {!allRequiredDone && (
              <p className="text-xs text-gray-400 text-center mt-2">필수 서류를 모두 체크하면 활성화돼요</p>
            )}
          </div>

          {/* Back to policy */}
          <button
            onClick={() => navigate(`/policies/${policyId}`)}
            className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 transition-colors justify-center py-2"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="19" y1="12" x2="5" y2="12"/>
              <polyline points="12 19 5 12 12 5"/>
            </svg>
            정책 상세로 돌아가기
          </button>
        </div>
      </div>
    </div>
  );
}

function ChecklistRow({
  item,
  checked,
  onToggle,
  required,
}: {
  item: { id: string; label: string; required: boolean; description?: string };
  checked: boolean;
  onToggle: () => void;
  required: boolean;
}) {
  return (
    <div
      onClick={onToggle}
      className={`flex items-start gap-4 px-6 py-4 cursor-pointer transition-colors hover:bg-gray-50 ${checked ? "bg-gray-50" : ""}`}
    >
      <div className={`mt-0.5 w-5 h-5 rounded-md border-2 flex items-center justify-center shrink-0 transition-colors ${
        checked ? "bg-primary-600 border-primary-600" : "border-gray-300"
      }`}>
        {checked && (
          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
        )}
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <p className={`text-sm font-medium ${checked ? "text-gray-400 line-through" : "text-gray-800"}`}>{item.label}</p>
          {required && (
            <span className="text-xs text-red-400 font-medium shrink-0">필수</span>
          )}
        </div>
        {item.description && (
          <p className="text-xs text-gray-400 mt-0.5 leading-5">{item.description}</p>
        )}
      </div>
    </div>
  );
}
