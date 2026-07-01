import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getSavedReports, deleteReport, type SavedReport } from "@/data/reports";

const CATEGORY_COLORS: Record<string, string> = {
  "기술": "bg-violet-100 text-violet-700",
  "금융": "bg-indigo-100 text-indigo-700",
  "인력": "bg-blue-100 text-blue-700",
  "경영": "bg-emerald-100 text-emerald-700",
  "창업": "bg-orange-100 text-orange-700",
  "수출": "bg-sky-100 text-sky-700",
  "내수": "bg-teal-100 text-teal-700",
  "기타": "bg-gray-100 text-gray-600",
  "최저임금": "bg-gray-100 text-gray-600",
  "노동·복지": "bg-blue-100 text-blue-700",
  "대출·자금": "bg-indigo-100 text-indigo-700",
  "에너지": "bg-orange-100 text-orange-700",
  "디지털": "bg-violet-100 text-violet-700",
  "세금": "bg-green-100 text-green-700",
  "창업지원": "bg-emerald-100 text-emerald-700",
  "임차료": "bg-pink-100 text-pink-700",
  "교육": "bg-sky-100 text-sky-700",
};

function impactDotColor(style: string) {
  if (style.includes("red")) return "bg-red-400";
  if (style.includes("green")) return "bg-green-400";
  if (style.includes("yellow")) return "bg-yellow-400";
  return "bg-gray-400";
}

function formatDate(iso: string) {
  const d = new Date(iso);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
}

const TABS = ["최신순", "오래된순", "부정 영향", "긍정 영향"] as const;
type Tab = (typeof TABS)[number];

export default function ReportList() {
  const navigate = useNavigate();
  const [reports, setReports] = useState<SavedReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [query, setQuery] = useState("");
  const [tab, setTab] = useState<Tab>("최신순");

  useEffect(() => {
    setLoading(true);
    setError(false);
    getSavedReports()
      .then(setReports)
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  async function handleDelete(e: React.MouseEvent, policyId: number) {
    e.stopPropagation();
    try {
      await deleteReport(policyId);
      setReports((prev) => prev.filter((r) => r.policyId !== policyId));
    } catch (err: any) {
      alert(err.response?.data?.message || "리포트 삭제에 실패했어요");
    }
  }

  const filtered = reports
    .filter((r) => {
      if (query && !r.policyTitle.includes(query) && !r.summary.includes(query)) return false;
      // impactLabel 텍스트 매칭 대신 direction 필드로 필터 (impactLabel엔 "긍정/부정" 글자가 없는 경우가 많음)
      if (tab === "부정 영향" && r.direction !== "negative") return false;
      if (tab === "긍정 영향" && r.direction !== "positive") return false;
      return true;
    })
    .sort((a, b) => {
      if (tab === "오래된순") return a.savedAt.localeCompare(b.savedAt);
      return b.savedAt.localeCompare(a.savedAt);
    });

  return (
    <div className="bg-gray-50 min-h-screen">
      <div className="bg-primary-100 px-40 pt-25 pb-10">
        <p className="text-sm font-semibold text-primary-600">AI 분석 결과</p>
        <h4 className="text-3xl font-bold mt-3">내 리포트</h4>
        <p className="text-base mt-3 text-gray-500">AI가 분석한 정책 리포트를 한 곳에서 확인하세요.</p>
      </div>

      <div className="px-40 py-8">
        {/* Search + Tabs */}
        <div className="flex items-center gap-3 mb-6">
          <div className="flex items-center gap-2 border border-gray-200 rounded-2xl px-4 py-2.5 focus-within:border-primary-400 transition-colors bg-white flex-1 max-w-sm">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-400 shrink-0">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="리포트 검색..."
              className="flex-1 text-sm text-gray-700 placeholder-gray-400 focus:outline-none bg-transparent"
            />
          </div>
          <div className="flex gap-1.5">
            {TABS.map((t) => (
              <button
                key={t}
                onClick={() => setTab(t)}
                className={`text-sm font-medium px-4 py-2.5 rounded-2xl transition-colors ${
                  tab === t ? "bg-primary-600 text-white" : "text-gray-500 border border-gray-200 bg-white hover:bg-gray-50"
                }`}
              >
                {t}
              </button>
            ))}
          </div>
        </div>

        {/* List */}
        {loading ? (
          <div className="flex flex-col gap-3">
            {[0, 1, 2].map((i) => (
              <div key={i} className="bg-white rounded-2xl border border-gray-200 px-6 py-5 animate-pulse">
                <div className="w-32 h-4 bg-gray-100 rounded mb-3" />
                <div className="w-2/3 h-4 bg-gray-200 rounded mb-2" />
                <div className="w-full h-3 bg-gray-100 rounded" />
              </div>
            ))}
          </div>
        ) : error ? (
          <div className="flex flex-col items-center justify-center py-24 text-gray-400">
            <p className="text-base font-semibold text-gray-500 mb-1">리포트를 불러오지 못했어요</p>
            <p className="text-sm text-gray-400">잠시 후 다시 시도해 주세요.</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-24 text-gray-400">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-300 mb-4">
              <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>
              <rect x="8" y="2" width="8" height="4" rx="1" ry="1"/>
            </svg>
            <p className="text-base font-semibold text-gray-500 mb-1">
              {query || tab !== "최신순" ? "조건에 맞는 리포트가 없어요" : "아직 저장된 리포트가 없어요"}
            </p>
            <p className="text-sm text-gray-400 mb-6">정책 상세 페이지에서 AI 리포트를 생성해 보세요.</p>
            <Link
              to="/policies"
              className="bg-primary-600 hover:bg-primary-700 text-white text-sm font-semibold px-6 py-3 rounded-xl transition-colors"
            >
              정책 둘러보기
            </Link>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {filtered.map((report) => (
              <div
                key={report.id}
                onClick={() => navigate(`/reports/${report.policyId}`)}
                className="bg-white rounded-2xl border border-gray-200 px-6 py-5 flex items-center gap-5 hover:shadow-md transition-shadow cursor-pointer"
              >
                <div className="flex flex-col gap-2 flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    {report.category && (
                      <span className={`text-xs font-medium px-2.5 py-1 rounded-full shrink-0 ${CATEGORY_COLORS[report.category] ?? "bg-gray-100 text-gray-600"}`}>
                        {report.category}
                      </span>
                    )}
                    <span className="flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full bg-primary-50 text-primary-600 border border-primary-100 shrink-0">
                      <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${impactDotColor(report.impactStyle)}`} />
                      {report.impactLabel}
                    </span>
                    <span className="text-xs text-gray-400 ml-auto shrink-0">{formatDate(report.savedAt)}</span>
                  </div>
                  <h3 className="text-sm font-bold text-gray-900 leading-snug line-clamp-1">{report.policyTitle}</h3>
                  <p className="text-xs text-gray-500 leading-5 line-clamp-2">{report.summary}</p>
                </div>

                <div className="flex items-center gap-3 shrink-0">
                  <button
                    onClick={(e) => handleDelete(e, report.policyId)}
                    className="p-1.5 text-gray-300 hover:text-red-400 transition-colors"
                    aria-label="리포트 삭제"
                  >
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <polyline points="3 6 5 6 21 6"/>
                      <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                      <path d="M10 11v6"/><path d="M14 11v6"/>
                      <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                    </svg>
                  </button>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-300">
                    <polyline points="9 18 15 12 9 6"/>
                  </svg>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
