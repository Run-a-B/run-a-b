import { useState, useEffect } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import api from "@/lib/api";
import { getSavedReport } from "@/data/reports";
import { markPolicyVisited } from "@/data/visited";

const HIGHLIGHT_ICONS: Record<string, React.ReactNode> = {
  money: (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-primary-500 shrink-0">
      <line x1="12" y1="1" x2="12" y2="23"/>
      <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
    </svg>
  ),
  check: (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="text-green-500 shrink-0">
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
      <polyline points="22 4 12 14.01 9 11.01"/>
    </svg>
  ),
  calendar: (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-blue-500 shrink-0">
      <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
      <line x1="16" y1="2" x2="16" y2="6"/>
      <line x1="8" y1="2" x2="8" y2="6"/>
      <line x1="3" y1="10" x2="21" y2="10"/>
    </svg>
  ),
};

const CATEGORY_COLORS: Record<string, string> = {
  "기술": "bg-violet-100 text-violet-700",
  "금융": "bg-indigo-100 text-indigo-700",
  "인력": "bg-blue-100 text-blue-700",
  "경영": "bg-emerald-100 text-emerald-700",
  "창업": "bg-orange-100 text-orange-700",
  "수출": "bg-sky-100 text-sky-700",
  "내수": "bg-teal-100 text-teal-700",
  "기타": "bg-gray-100 text-gray-600",
};

const TAG_COLORS = [
  "bg-gray-100 text-gray-600",
  "bg-blue-50 text-blue-700",
  "bg-purple-50 text-purple-700",
  "bg-green-50 text-green-700",
  "bg-orange-50 text-orange-700",
];

interface ApiDetail {
  id: number;
  title: string;
  description: string | null;
  date: string | null;
  announcementNo: string | null;
  department: string | null;
  applicationPeriod: string | null;
  supportScale: string | null;
  targetGroup: string | null;
  purposeText: string | null;
  applicationMethod: string | null;
  applicationUrl: string | null;
  detailUrl: string | null;
  category: string;
  region: string;
  industry: string;
  agency: string;
}

interface PolicySummaryData {
  summaryLines: string[];
  highlights: { icon: string; label: string; content: string }[];
}

// AI/미구현 필드 기본값 — AI 연동 후 API 응답으로 교체 예정
const AI_DEFAULTS = {
  tags: [] as string[],
  targetConditions: [] as string[],
  exclusions: [] as string[],
  supportItems: [] as { category: string; amount: string; method: string }[],
  aiSummaryText: "AI 요약 기능이 준비 중이에요. 곧 이 정책에 대한 맞춤 요약을 제공할 예정입니다.",
  aiHighlights: [] as { icon: string; label: string; content: string }[],
  businessImpact: [] as { label: string; level: number; direction: "up" | "down"; tag: string; barColor: string; tagColor: string }[],
  reportData: {
    impactLabel: "분석 준비 중",
    impactStyle: "bg-gray-100 text-gray-600",
    summary: "AI 분석이 준비 중입니다.",
    details: [] as string[],
    relatedIds: [] as number[],
  },
  applicationChecklist: [] as { id: string; label: string; required: boolean; description?: string }[],
};

export default function PolicyDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const policyId = Number(id);

  const [apiDetail, setApiDetail] = useState<ApiDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [reportState, setReportState] = useState<"idle" | "loading" | "done">("idle");
  const [summary, setSummary] = useState<PolicySummaryData | null>(null);
  const [summaryState, setSummaryState] = useState<"loading" | "done" | "error">("loading");

  useEffect(() => {
    setLoading(true);
    setNotFound(false);
    api.get(`/api/v1/policies/${policyId}`)
      .then(res => {
        const data: ApiDetail = res.data.data;
        setApiDetail(data);
        markPolicyVisited(policyId);
      })
      .catch(err => {
        if (err.response?.status === 404) setNotFound(true);
      })
      .finally(() => setLoading(false));
  }, [policyId]);

  // 이미 이 정책 리포트를 만들었는지 백엔드에서 확인 (계정별 스코프)
  useEffect(() => {
    let active = true;
    getSavedReport(policyId)
      .then((found) => {
        if (active && found) setReportState("done");
      })
      .catch(() => { /* 미로그인/네트워크 오류 시 그냥 idle 유지 */ });
    return () => {
      active = false;
    };
  }, [policyId]);

  // AI 요약 조회 (최초 1회 백엔드에서 생성 후 캐싱 → 재방문 시 빠름). 실패해도 페이지는 정상.
  useEffect(() => {
    let active = true;
    setSummary(null);
    setSummaryState("loading");
    api.get(`/api/v1/policies/${policyId}/summary`)
      .then((res) => {
        if (!active) return;
        setSummary(res.data.data);
        setSummaryState("done");
      })
      .catch(() => {
        if (active) setSummaryState("error");
      });
    return () => {
      active = false;
    };
  }, [policyId]);

  function handleGenerateReport() {
    if (!apiDetail) return;
    setReportState("loading");
    // 생성 요청이 백엔드에서 user_id 스코프로 DB에 저장(upsert)까지 처리한다. 프론트는 결과 페이지로 이동만.
    api.post(`/api/v1/policies/${apiDetail.id}/report`)
      .then(() => {
        navigate(`/reports/${apiDetail.id}`);
      })
      .catch((err) => {
        console.error(err);
        alert(err.response?.data?.message || "리포트 생성에 실패했어요");
        setReportState("idle");
      });
  }

  if (loading) {
    return (
      <div className="bg-gray-50 min-h-screen pt-15 px-40 py-8">
        <div className="flex flex-col gap-4 animate-pulse">
          <div className="w-48 h-4 bg-gray-200 rounded" />
          <div className="bg-white rounded-2xl border border-gray-100 p-6 flex flex-col gap-4">
            <div className="w-3/4 h-7 bg-gray-200 rounded" />
            <div className="w-full h-4 bg-gray-100 rounded" />
            <div className="w-2/3 h-4 bg-gray-100 rounded" />
          </div>
        </div>
      </div>
    );
  }

  if (notFound || !apiDetail) {
    return (
      <div className="flex flex-col items-center justify-center py-40 text-gray-400">
        <p className="text-lg font-semibold mb-2">정책을 찾을 수 없어요</p>
        <button onClick={() => navigate("/policies")} className="mt-4 text-sm text-primary-600 hover:underline">
          정책 목록으로 돌아가기
        </button>
      </div>
    );
  }

  return (
    <div className="bg-gray-50 min-h-screen pt-15">
      <div className="px-40 py-4">
        <nav className="flex items-center gap-1.5 text-sm text-gray-500">
          <Link to="/" className="hover:text-gray-700 transition-colors">홈</Link>
          <span className="text-gray-300">›</span>
          <Link to="/policies" className="hover:text-gray-700 transition-colors">정책 모아보기</Link>
          <span className="text-gray-300">›</span>
          <span className="text-gray-700 font-medium truncate max-w-xs">{apiDetail.title}</span>
        </nav>
      </div>

      <div className="px-40 py-8 flex gap-6 items-start">
        {/* Left: Policy Document */}
        <div className="flex-1 min-w-0">
          <div className="bg-white rounded-2xl border border-gray-200 overflow-hidden">
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
              <div className="flex items-center gap-2">
                <span className="text-sm font-semibold text-gray-700">정책 원문</span>
                <span className="text-xs px-2 py-0.5 rounded bg-gray-100 text-gray-500">공식 문서</span>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => navigator.clipboard.writeText(apiDetail.title)}
                  className="flex items-center gap-1.5 text-xs text-gray-500 border border-gray-200 rounded-lg px-3 py-1.5 hover:bg-gray-50 transition-colors"
                >
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                  </svg>
                  복사
                </button>
                {(apiDetail.detailUrl || apiDetail.applicationUrl) ? (
                  <a
                    href={(apiDetail.detailUrl || apiDetail.applicationUrl)!}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-1.5 text-xs text-primary-600 border border-primary-200 bg-primary-50 rounded-lg px-3 py-1.5 hover:bg-primary-100 transition-colors font-medium"
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
                      <polyline points="15 3 21 3 21 9"/>
                      <line x1="10" y1="14" x2="21" y2="3"/>
                    </svg>
                    원문 공고 보기
                  </a>
                ) : null}
              </div>
            </div>

            <div className="px-6 py-6">
              {/* Tags */}
              {AI_DEFAULTS.tags.length > 0 && (
                <div className="flex items-center gap-2 mb-4">
                  {AI_DEFAULTS.tags.map((tag, i) => (
                    <span key={tag} className={`text-xs font-medium px-2.5 py-1 rounded-full ${TAG_COLORS[i % TAG_COLORS.length]}`}>
                      {tag}
                    </span>
                  ))}
                </div>
              )}

              {/* Category + Title */}
              <div className="flex items-center gap-2 mb-3">
                <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${CATEGORY_COLORS[apiDetail.category] ?? "bg-gray-100 text-gray-600"}`}>
                  {apiDetail.category}
                </span>
                <span className="text-xs text-gray-400">{apiDetail.region}</span>
              </div>
              <h1 className="text-2xl font-bold text-gray-900 leading-snug mb-6">{apiDetail.title}</h1>

              {/* Metadata grid */}
              <div className="grid grid-cols-2 border border-gray-200 rounded-xl overflow-hidden text-sm mb-8">
                {[
                  { label: "주관 기관", value: apiDetail.agency },
                  { label: "지역", value: apiDetail.region },
                  { label: "지원 분야", value: apiDetail.category },
                  { label: "업종", value: apiDetail.industry },
                  { label: "공고일", value: apiDetail.date ?? "-" },
                  { label: "신청 기간", value: apiDetail.applicationPeriod || "-" },
                ].map(({ label, value }, idx) => (
                  <div key={label} className={`flex px-4 py-3 ${idx < 4 ? "border-b border-gray-100" : ""} ${idx % 2 === 0 ? "border-r border-gray-100" : ""}`}>
                    <span className="text-gray-400 w-20 shrink-0">{label}</span>
                    <span className="text-gray-800 font-medium">{value}</span>
                  </div>
                ))}
              </div>

              {/* 지원 대상 배지 */}
              {apiDetail.targetGroup && (
                <div className="flex items-center gap-2 mb-5">
                  <span className="text-xs font-semibold text-gray-400 shrink-0">지원 대상</span>
                  <span className="text-xs font-medium px-3 py-1 rounded-full bg-blue-50 text-blue-700 border border-blue-100">
                    {apiDetail.targetGroup}
                  </span>
                </div>
              )}

              {/* 지원 규모 */}
              {apiDetail.supportScale && (
                <div className="mb-5 flex items-start gap-3 bg-primary-50 border border-primary-100 rounded-xl px-4 py-3">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-primary-500 shrink-0 mt-0.5">
                    <line x1="12" y1="1" x2="12" y2="23"/>
                    <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
                  </svg>
                  <div>
                    <p className="text-xs font-semibold text-primary-600 mb-0.5">지원 규모</p>
                    <p className="text-sm text-gray-700 leading-6 whitespace-pre-line">{apiDetail.supportScale}</p>
                  </div>
                </div>
              )}

              {/* 사업 목적 */}
              {apiDetail.purposeText && (
                <section className="mb-6">
                  <h2 className="text-lg font-bold text-gray-900 mb-3">사업 목적</h2>
                  <p className="text-base text-gray-700 leading-8 whitespace-pre-line">{apiDetail.purposeText}</p>
                </section>
              )}

              {/* 사업 내용 */}
              {apiDetail.description && (
                <section className="mb-6">
                  <h2 className="text-lg font-bold text-gray-900 mb-3">사업 내용</h2>
                  <p className="text-base text-gray-700 leading-8 whitespace-pre-line">{apiDetail.description}</p>
                </section>
              )}

              {/* 신청 방법 */}
              {apiDetail.applicationMethod && (
                <section className="mb-6">
                  <h2 className="text-lg font-bold text-gray-900 mb-3">신청 방법</h2>
                  <p className="text-base text-gray-700 leading-8">{apiDetail.applicationMethod}</p>
                </section>
              )}

              {/* 더 자세한 내용 안내 */}
              {(apiDetail.detailUrl || apiDetail.applicationUrl) && (
                <div className="mt-2 flex items-center gap-3 bg-primary-50 border border-primary-100 rounded-xl px-5 py-4">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-primary-500 shrink-0">
                    <circle cx="12" cy="12" r="10"/>
                    <line x1="12" y1="8" x2="12" y2="12"/>
                    <line x1="12" y1="16" x2="12.01" y2="16"/>
                  </svg>
                  <p className="text-sm text-gray-600 flex-1">더 자세한 내용은 원문 공고에서 확인할 수 있어요.</p>
                  <a
                    href={(apiDetail.detailUrl || apiDetail.applicationUrl)!}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="shrink-0 text-sm font-semibold text-primary-600 hover:underline"
                  >
                    원문 보기 →
                  </a>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Right: AI panels */}
        <div className="w-110 shrink-0 flex flex-col gap-4 sticky top-6">
          {/* AI Summary */}
          <div className="bg-primary-50 rounded-2xl border border-primary-100 overflow-hidden">
            <div className="flex items-center justify-between px-5 py-4 border-b border-primary-100">
              <div className="flex items-center gap-2">
                <div className="w-7 h-7 rounded-xl bg-primary-500 flex items-center justify-center">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor" className="text-white">
                    <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
                  </svg>
                </div>
                <span className="text-sm font-bold text-gray-800">AI 요약</span>
              </div>
              {summaryState === "done" ? (
                <span className="flex items-center gap-1 text-xs font-medium px-2.5 py-1 rounded-full bg-white text-primary-600 border border-primary-100">
                  <span className="w-1.5 h-1.5 rounded-full bg-primary-500 inline-block" />
                  완료
                </span>
              ) : summaryState === "loading" ? (
                <span className="flex items-center gap-1 text-xs font-medium px-2.5 py-1 rounded-full bg-white text-gray-400 border border-gray-200">
                  <span className="w-1.5 h-1.5 rounded-full bg-gray-300 inline-block animate-pulse" />
                  요약 중
                </span>
              ) : (
                <span className="flex items-center gap-1 text-xs font-medium px-2.5 py-1 rounded-full bg-white text-gray-400 border border-gray-200">
                  <span className="w-1.5 h-1.5 rounded-full bg-gray-300 inline-block" />
                  준비 중
                </span>
              )}
            </div>

            <div className="px-5 py-4 flex flex-col gap-4">
              {summaryState === "loading" && (
                <div className="flex flex-col gap-2 animate-pulse">
                  <div className="h-3.5 bg-white rounded w-full" />
                  <div className="h-3.5 bg-white rounded w-5/6" />
                  <div className="h-3.5 bg-white rounded w-2/3" />
                  <p className="text-xs text-gray-400 mt-1">AI가 공고문을 요약하고 있어요...</p>
                </div>
              )}

              {summaryState === "error" && (
                <p className="text-sm text-gray-500 leading-7">{AI_DEFAULTS.aiSummaryText}</p>
              )}

              {summaryState === "done" && summary && (
                <>
                  {summary.summaryLines.length > 0 ? (
                    <ul className="flex flex-col gap-2">
                      {summary.summaryLines.map((line, i) => (
                        <li key={i} className="flex gap-2 text-sm text-gray-600 leading-6">
                          <span className="text-primary-500 font-bold shrink-0">{i + 1}.</span>
                          <span>{line}</span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-sm text-gray-500 leading-7">{AI_DEFAULTS.aiSummaryText}</p>
                  )}
                  {summary.highlights.length > 0 && (
                    <div className="flex flex-col gap-2">
                      {summary.highlights.map((h, i) => (
                        <div key={i} className="flex gap-3 bg-white rounded-xl p-3 border border-primary-100">
                          <span className="mt-0.5">{HIGHLIGHT_ICONS[h.icon]}</span>
                          <div>
                            <p className="text-sm font-semibold text-gray-700 mb-0.5">{h.label}</p>
                            <p className="text-sm text-gray-500 leading-6">{h.content}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </>
              )}
            </div>
          </div>

          {/* AI Report */}
          <div className="bg-white rounded-2xl border border-gray-200 overflow-hidden">
            <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
              <span className="text-sm font-bold text-gray-800">AI 리포트</span>
              {reportState === "done" && (
                <span className="flex items-center gap-1 text-xs font-medium px-2.5 py-1 rounded-full bg-primary-50 text-primary-600 border border-primary-100">
                  <span className="w-1.5 h-1.5 rounded-full bg-primary-400 inline-block" />
                  저장됨
                </span>
              )}
            </div>

            {reportState === "idle" && (
              <div className="px-5 py-6 flex flex-col items-center gap-3">
                <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="text-gray-300">
                  <rect x="2" y="8" width="20" height="13" rx="2"/>
                  <circle cx="8.5" cy="14.5" r="1.5"/>
                  <circle cx="15.5" cy="14.5" r="1.5"/>
                  <path d="M9 18.5h6"/>
                  <path d="M12 8V5"/>
                  <circle cx="12" cy="4" r="1"/>
                </svg>
                <div className="text-center">
                  <p className="text-sm font-semibold text-gray-800 mb-1">내 사업 맞춤 리포트를 생성할 수 있어요</p>
                  <p className="text-xs text-gray-500 leading-5">
                    업종·지역·매출·직원 수를 기반으로<br />
                    이 정책이 내 사업에 미치는 영향을<br />
                    AI가 분석해 드려요.
                  </p>
                </div>
                <button
                  onClick={handleGenerateReport}
                  className="w-full mt-1 bg-primary-600 hover:bg-primary-700 text-white text-sm font-semibold py-3 rounded-xl transition-colors"
                >
                  AI 리포트 생성하기
                </button>
              </div>
            )}

            {reportState === "loading" && (
              <div className="px-5 py-10 flex flex-col items-center gap-4">
                <div className="w-10 h-10 rounded-full border-[3px] border-primary-200 border-t-primary-600 animate-spin" />
                <div className="text-center">
                  <p className="text-sm font-semibold text-gray-700">AI가 분석 중이에요</p>
                  <p className="text-xs text-gray-400 mt-1">잠시만 기다려 주세요...</p>
                </div>
              </div>
            )}

            {reportState === "done" && (
              <div className="px-5 py-6 flex flex-col items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-primary-50 flex items-center justify-center">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="text-primary-600">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                </div>
                <div className="text-center">
                  <p className="text-sm font-semibold text-gray-800 mb-1">리포트가 저장되어 있어요</p>
                  <p className="text-xs text-gray-500">리포트 페이지에서 전체 내용을 확인하세요.</p>
                </div>
                <button
                  onClick={() => navigate(`/reports/${policyId}`)}
                  className="w-full mt-1 bg-primary-600 hover:bg-primary-700 text-white text-sm font-semibold py-3 rounded-xl transition-colors"
                >
                  리포트 보기
                </button>
              </div>
            )}
          </div>

          <button
            onClick={() => navigate(-1)}
            className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 transition-colors justify-center py-2"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="19" y1="12" x2="5" y2="12"/>
              <polyline points="12 19 5 12 12 5"/>
            </svg>
            목록으로 돌아가기
          </button>
        </div>
      </div>
    </div>
  );
}
