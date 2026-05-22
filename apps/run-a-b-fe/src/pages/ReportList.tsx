import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getSavedReports, deleteReport, type SavedReport } from "@/data/reports";
import { MOCK_POLICIES, POLICY_DETAILS } from "@/data/policies";

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
  const [selected, setSelected] = useState<SavedReport | null>(null);
  const [query, setQuery] = useState("");
  const [tab, setTab] = useState<Tab>("최신순");

  useEffect(() => {
    const loaded = getSavedReports();
    setReports(loaded);
    if (loaded.length > 0) setSelected(loaded[0]);
  }, []);

  function handleDelete(policyId: number) {
    deleteReport(policyId);
    const updated = getSavedReports();
    setReports(updated);
    if (selected?.policyId === policyId) {
      setSelected(updated[0] ?? null);
    }
  }

  const filtered = reports
    .filter((r) => {
      if (query && !r.policyTitle.includes(query) && !r.summary.includes(query)) return false;
      if (tab === "부정 영향" && !r.impactLabel.includes("부정")) return false;
      if (tab === "긍정 영향" && !r.impactLabel.includes("긍정")) return false;
      return true;
    })
    .sort((a, b) => {
      if (tab === "오래된순") return a.savedAt.localeCompare(b.savedAt);
      return b.savedAt.localeCompare(a.savedAt);
    });

  const selectedPolicy = selected ? MOCK_POLICIES.find((p) => p.id === selected.policyId) : null;
  const selectedBusinessImpact = selected ? POLICY_DETAILS[selected.policyId]?.businessImpact : null;
  const selectedRelated = selected
    ? selected.relatedIds.map((id) => MOCK_POLICIES.find((p) => p.id === id)).filter(Boolean)
    : [];

  return (
    <div className="flex" style={{ height: "calc(100vh - 60px)" }}>
      {/* ── Left sidebar ── */}
      <aside className="w-72 shrink-0 border-r border-gray-200 flex flex-col bg-white">
        <div className="px-5 pt-6 pb-4">
          <h2 className="text-base font-bold text-gray-900">내 리포트</h2>
        </div>

        {/* Search */}
        <div className="px-4 pb-3">
          <div className="flex items-center gap-2 border border-gray-200 rounded-xl px-3 py-2 focus-within:border-primary-400 transition-colors bg-white">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-400 shrink-0">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="리포트 검색..."
              className="flex-1 text-xs text-gray-700 placeholder-gray-400 focus:outline-none bg-transparent"
            />
          </div>
        </div>

        {/* Tabs */}
        <div className="px-4 pb-3 flex gap-1.5 flex-wrap">
          {TABS.map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`text-xs font-medium px-3 py-1.5 rounded-full transition-colors ${
                tab === t ? "bg-primary-600 text-white" : "text-gray-500 hover:bg-gray-100"
              }`}
            >
              {t}
            </button>
          ))}
        </div>

        <div className="border-b border-gray-100" />

        {/* Report list */}
        <div className="flex-1 overflow-y-auto">
          {filtered.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-gray-400 px-4">
              <p className="text-xs text-center">조건에 맞는 리포트가 없어요</p>
            </div>
          ) : (
            filtered.map((report) => {
              const policy = MOCK_POLICIES.find((p) => p.id === report.policyId);
              const categoryColor = CATEGORY_COLORS[policy?.category ?? ""] ?? "bg-gray-100 text-gray-600";
              const isSelected = selected?.policyId === report.policyId;
              return (
                <button
                  key={report.id}
                  onClick={() => setSelected(report)}
                  className={`w-full text-left px-5 py-4 border-b border-gray-100 transition-colors ${
                    isSelected ? "bg-primary-50" : "hover:bg-gray-50"
                  }`}
                >
                  <div className="flex items-center justify-between mb-2">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${categoryColor}`}>
                      {policy?.category ?? ""}
                    </span>
                    <span className="text-xs text-gray-400">{formatDate(report.savedAt)}</span>
                  </div>
                  <p className="text-sm font-bold text-gray-900 leading-snug line-clamp-2 mb-1.5">
                    {report.policyTitle}
                  </p>
                  <p className="text-xs text-gray-500 leading-5 line-clamp-2 mb-2">
                    {report.summary}
                  </p>
                  <div className="flex items-center gap-1.5">
                    <span className={`w-2 h-2 rounded-full shrink-0 ${impactDotColor(report.impactStyle)}`} />
                    <span className="text-xs text-gray-500">{report.impactLabel}</span>
                  </div>
                </button>
              );
            })
          )}
        </div>
      </aside>

      {/* ── Right panel ── */}
      <main className="flex-1 overflow-y-auto bg-gray-50">
        {!selected ? (
          /* Empty state */
          <div className="flex flex-col items-center justify-center h-full gap-4 text-gray-400">
            <svg width="60" height="60" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-300">
              <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>
              <rect x="8" y="2" width="8" height="4" rx="1" ry="1"/>
              <line x1="9" y1="12" x2="15" y2="12"/>
              <line x1="9" y1="16" x2="13" y2="16"/>
            </svg>
            <div className="text-center">
              <p className="text-base font-bold text-gray-600 mb-1">리포트를 선택해 주세요</p>
              <p className="text-sm text-gray-400 leading-6">
                왼쪽 목록에서 리포트를 클릭하면<br />AI가 분석한 상세 내용을 확인할 수 있어요.
              </p>
            </div>
            <Link
              to="/policies"
              className="bg-primary-600 hover:bg-primary-700 text-white text-sm font-semibold px-6 py-3 rounded-xl transition-colors"
            >
              새 정책 분석하기
            </Link>
          </div>
        ) : (
          /* Report detail */
          <div className="max-w-2xl mx-auto px-8 py-8 flex flex-col gap-6">
            {/* Header */}
            <div className="flex items-start justify-between gap-4">
              <div>
                {selectedPolicy && (
                  <span className={`text-xs font-medium px-2.5 py-1 rounded-full mb-3 inline-block ${CATEGORY_COLORS[selectedPolicy.category] ?? "bg-gray-100 text-gray-600"}`}>
                    {selectedPolicy.category}
                  </span>
                )}
                <h1 className="text-lg font-bold text-gray-900 leading-snug">{selected.policyTitle}</h1>
              </div>
              <button
                onClick={() => handleDelete(selected.policyId)}
                className="shrink-0 text-gray-300 hover:text-red-400 transition-colors mt-1"
                aria-label="리포트 삭제"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                  <path d="M10 11v6"/><path d="M14 11v6"/>
                  <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                </svg>
              </button>
            </div>

            {/* Impact */}
            <div className="bg-white rounded-2xl border border-gray-200 p-5 flex flex-col gap-4">
              <div>
                <p className="text-xs text-gray-400 mb-2">영향 방향</p>
                <span className={`inline-flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg ${selected.impactStyle}`}>
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                  </svg>
                  {selected.impactLabel}
                </span>
              </div>
              <p className="text-sm text-gray-700 leading-6">{selected.summary}</p>
            </div>

            {/* Business Impact */}
            {selectedBusinessImpact && selectedBusinessImpact.length > 0 && (
              <div className="bg-primary-50 rounded-2xl border border-primary-100 p-5">
                <p className="text-xs font-bold text-primary-600 mb-4">내 사업 영향도 미리보기</p>
                <div className="flex flex-col gap-3">
                  {selectedBusinessImpact.map((item) => (
                    <div key={item.label} className="flex items-center gap-3">
                      <span className="text-xs text-gray-500 w-12 shrink-0">{item.label}</span>
                      <div className="flex-1 h-2 bg-white rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full ${item.barColor}`}
                          style={{ width: `${item.level}%` }}
                        />
                      </div>
                      <span className={`flex items-center gap-1 text-xs font-semibold shrink-0 whitespace-nowrap ${item.tagColor}`}>
                        {item.direction === "up" ? (
                          <svg width="8" height="8" viewBox="0 0 10 10" fill="currentColor"><polygon points="5,1 9,9 1,9"/></svg>
                        ) : (
                          <svg width="8" height="8" viewBox="0 0 10 10" fill="currentColor"><polygon points="5,9 9,1 1,1"/></svg>
                        )}
                        {item.tag}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Detail */}
            <div className="bg-white rounded-2xl border border-gray-200 p-5 flex flex-col gap-3">
              <p className="text-xs text-gray-400">상세 분석</p>
              {selected.details.map((text, i) => (
                <p key={i} className="text-sm text-gray-700 leading-6">{text}</p>
              ))}
            </div>

            {/* Related */}
            {selectedRelated.length > 0 && (
              <div className="bg-white rounded-2xl border border-gray-200 p-5 flex flex-col gap-3">
                <p className="text-xs text-gray-400">함께 신청하면 좋아요</p>
                {selectedRelated.map((rel) => rel && (
                  <button
                    key={rel.id}
                    onClick={() => navigate(`/policies/${rel.id}`)}
                    className="flex items-center gap-3 text-left bg-primary-50 hover:bg-primary-100 border border-primary-100 rounded-xl p-3 transition-colors"
                  >
                    <div className="w-8 h-8 rounded-lg bg-primary-100 flex items-center justify-center shrink-0">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-primary-500">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                        <polyline points="14 2 14 8 20 8"/>
                        <line x1="16" y1="13" x2="8" y2="13"/>
                        <line x1="16" y1="17" x2="8" y2="17"/>
                      </svg>
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-semibold text-gray-800 line-clamp-1">{rel.title}</p>
                      <p className="text-xs text-primary-500 mt-0.5">{rel.agency}</p>
                    </div>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-400 shrink-0">
                      <polyline points="9 18 15 12 9 6"/>
                    </svg>
                  </button>
                ))}
              </div>
            )}

            {/* Actions */}
            <div className="flex gap-3">
              <Link
                to={`/policies/${selected.policyId}`}
                className="flex-1 flex items-center justify-center gap-1.5 text-sm font-semibold text-gray-600 border border-gray-200 py-3 rounded-xl hover:bg-gray-100 transition-colors bg-white"
              >
                정책 원문 보기
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="5" y1="12" x2="19" y2="12"/>
                  <polyline points="12 5 19 12 12 19"/>
                </svg>
              </Link>
              <Link
                to="/policies"
                className="flex-1 flex items-center justify-center gap-1.5 text-sm font-semibold text-white bg-primary-600 hover:bg-primary-700 py-3 rounded-xl transition-colors"
              >
                새 정책 분석하기
              </Link>
            </div>

            <p className="text-xs text-gray-400 text-center">저장일: {formatDate(selected.savedAt)}</p>
          </div>
        )}
      </main>
    </div>
  );
}
