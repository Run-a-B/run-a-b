import { useState, useEffect } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import { MOCK_POLICIES, POLICY_DETAILS } from "@/data/policies";
import { saveReport } from "@/data/reports";
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

const TAG_COLORS = [
  "bg-gray-100 text-gray-600",
  "bg-blue-50 text-blue-700",
  "bg-purple-50 text-purple-700",
  "bg-green-50 text-green-700",
  "bg-orange-50 text-orange-700",
];

export default function PolicyDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const policyId = Number(id);
  const [reportState, setReportState] = useState<"idle" | "loading" | "done">("idle");

  const policy = MOCK_POLICIES.find((p) => p.id === policyId);
  const detail = POLICY_DETAILS[policyId];

  useEffect(() => {
    if (policy) markPolicyVisited(policyId);
  }, [policyId, policy]);

  function handleGenerateReport() {
    setReportState("loading");
    setTimeout(() => {
      if (policy && detail) {
        saveReport({
          policyId: policy.id,
          policyTitle: policy.title,
          impactLabel: detail.reportData.impactLabel,
          impactStyle: detail.reportData.impactStyle,
          summary: detail.reportData.summary,
          details: detail.reportData.details,
          relatedIds: detail.reportData.relatedIds,
        });
      }
      setReportState("done");
    }, 1600);
  }

  if (!policy || !detail) {
    return (
      <div className="flex flex-col items-center justify-center py-40 text-gray-400">
        <p className="text-lg font-semibold mb-2">정책을 찾을 수 없어요</p>
        <button
          onClick={() => navigate("/policies")}
          className="mt-4 text-sm text-primary-600 hover:underline"
        >
          정책 목록으로 돌아가기
        </button>
      </div>
    );
  }

  const categoryColor = CATEGORY_COLORS[policy.category] ?? "bg-gray-100 text-gray-600";

  return (
    <div className="bg-gray-50 min-h-screen">
      <div className="px-40 py-4 bg-white border-b border-gray-200">
        <nav className="flex items-center gap-1.5 text-sm text-gray-500">
          <Link to="/" className="hover:text-gray-700 transition-colors">홈</Link>
          <span className="text-gray-300">›</span>
          <Link to="/policies" className="hover:text-gray-700 transition-colors">정책 모아보기</Link>
          <span className="text-gray-300">›</span>
          <span className="text-gray-700 font-medium truncate max-w-xs">{policy.title}</span>
        </nav>
      </div>

      <div className="px-40 py-8 flex gap-6 items-start">
        {/* Left: Policy Document */}
        <div className="flex-1 min-w-0">
          <div className="bg-white rounded-2xl border border-gray-200 overflow-hidden">
            {/* Card header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
              <div className="flex items-center gap-2">
                <span className="text-sm font-semibold text-gray-700">정책 원문</span>
                <span className="text-xs px-2 py-0.5 rounded bg-gray-100 text-gray-500">공식 문서</span>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => navigator.clipboard.writeText(policy.title)}
                  className="flex items-center gap-1.5 text-xs text-gray-500 border border-gray-200 rounded-lg px-3 py-1.5 hover:bg-gray-50 transition-colors"
                >
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                  </svg>
                  복사
                </button>
                <button className="flex items-center gap-1.5 text-xs text-gray-500 border border-gray-200 rounded-lg px-3 py-1.5 hover:bg-gray-50 transition-colors">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
                    <polyline points="15 3 21 3 21 9"/>
                    <line x1="10" y1="14" x2="21" y2="3"/>
                  </svg>
                  원문 링크
                </button>
              </div>
            </div>

            {/* Policy body */}
            <div className="px-6 py-6">
              {/* Tags */}
              <div className="flex items-center gap-2 mb-4">
                {detail.tags.map((tag, i) => (
                  <span key={tag} className={`text-xs font-medium px-2.5 py-1 rounded-full ${TAG_COLORS[i % TAG_COLORS.length]}`}>
                    {tag}
                  </span>
                ))}
              </div>

              {/* Title */}
              <h1 className="text-xl font-bold text-gray-900 leading-snug mb-6">{policy.title}</h1>

              {/* Metadata grid */}
              <div className="grid grid-cols-2 border border-gray-200 rounded-xl overflow-hidden text-sm mb-8">
                {[
                  { label: "담당 부서", value: detail.department },
                  { label: "공고 번호", value: detail.announcementNo },
                  { label: "공고일", value: policy.date },
                  { label: "신청 기간", value: detail.applicationPeriod },
                  { label: "지원 규모", value: detail.supportScale },
                  { label: "지원 대상", value: detail.targetGroup },
                ].map(({ label, value }, idx) => (
                  <div key={label} className={`flex px-4 py-3 ${idx < 4 ? "border-b border-gray-100" : ""} ${idx % 2 === 0 ? "border-r border-gray-100" : ""}`}>
                    <span className="text-gray-500 w-20 shrink-0">{label}</span>
                    <span className="text-gray-800 font-medium">{value}</span>
                  </div>
                ))}
              </div>

              {/* Section 1 */}
              <section className="mb-6">
                <h2 className="text-base font-bold text-gray-900 mb-3">1. 사업 목적</h2>
                <p className="text-sm text-gray-700 leading-7">{detail.purposeText}</p>
              </section>

              {/* Section 2 */}
              <section className="mb-6">
                <h2 className="text-base font-bold text-gray-900 mb-3">2. 지원 대상</h2>
                <p className="text-sm text-gray-600 mb-3">다음 각 호에 해당하는 사업장을 운영하는 사업주를 지원 대상으로 합니다.</p>
                <ul className="space-y-2">
                  {detail.targetConditions.map((cond) => (
                    <li key={cond} className="flex items-start gap-2 text-sm text-gray-700">
                      <span className="mt-1.5 w-1.5 h-1.5 rounded-full bg-primary-400 shrink-0" />
                      {cond}
                    </li>
                  ))}
                  {detail.exclusions.map((exc) => (
                    <li key={exc} className="flex items-start gap-2 text-sm text-gray-500">
                      <span className="mt-1.5 w-1.5 h-1.5 rounded-full bg-gray-300 shrink-0" />
                      단, {exc}은 제외
                    </li>
                  ))}
                </ul>
              </section>

              {/* Section 3 */}
              <section className="mb-6">
                <h2 className="text-base font-bold text-gray-900 mb-3">3. 지원 내용</h2>
                <div className="overflow-hidden rounded-xl border border-gray-200">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-gray-50 border-b border-gray-200">
                        <th className="text-left px-4 py-3 font-semibold text-gray-600 w-1/3">구분</th>
                        <th className="text-left px-4 py-3 font-semibold text-primary-600 w-1/3">지원 금액</th>
                        <th className="text-left px-4 py-3 font-semibold text-gray-600 w-1/3">지원 방식</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detail.supportItems.map((item, idx) => (
                        <tr key={item.category} className={idx < detail.supportItems.length - 1 ? "border-b border-gray-100" : ""}>
                          <td className="px-4 py-3 text-gray-700">{item.category}</td>
                          <td className="px-4 py-3 text-gray-800 font-medium">{item.amount}</td>
                          <td className="px-4 py-3 text-gray-600">{item.method}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>

              {/* Section 4 */}
              <section>
                <h2 className="text-base font-bold text-gray-900 mb-3">4. 신청 방법</h2>
                <p className="text-sm text-gray-700 leading-7">{detail.applicationMethod}</p>
              </section>
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
              <span className="flex items-center gap-1 text-xs font-medium px-2.5 py-1 rounded-full bg-white text-primary-600 border border-primary-200">
                <span className="w-1.5 h-1.5 rounded-full bg-primary-400 inline-block" />
                AI 분석 완료
              </span>
            </div>

            <div className="px-5 py-4 flex flex-col gap-4">
              <p className="text-sm text-gray-600 leading-6">{detail.aiSummaryText}</p>

              {/* Highlights */}
              <div className="flex flex-col gap-2">
                {detail.aiHighlights.map((h) => (
                  <div key={h.label} className="flex gap-3 bg-white rounded-xl p-3 border border-primary-100">
                    <span className="mt-0.5">{HIGHLIGHT_ICONS[h.icon]}</span>
                    <div>
                      <p className="text-xs font-semibold text-gray-700 mb-0.5">{h.label}</p>
                      <p className="text-xs text-gray-500 leading-5">{h.content}</p>
                    </div>
                  </div>
                ))}
              </div>

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

            {/* idle */}
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

            {/* loading */}
            {reportState === "loading" && (
              <div className="px-5 py-10 flex flex-col items-center gap-4">
                <div className="w-10 h-10 rounded-full border-[3px] border-primary-200 border-t-primary-600 animate-spin" />
                <div className="text-center">
                  <p className="text-sm font-semibold text-gray-700">AI가 분석 중이에요</p>
                  <p className="text-xs text-gray-400 mt-1">잠시만 기다려 주세요...</p>
                </div>
              </div>
            )}

            {/* done */}
            {reportState === "done" && (
              <div className="px-5 py-5 flex flex-col gap-4">
                {/* 영향 방향 */}
                <div>
                  <p className="text-xs text-gray-400 mb-2">영향 방향</p>
                  <span className={`inline-flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg ${detail.reportData.impactStyle}`}>
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                      <polyline points="14 2 14 8 20 8"/>
                    </svg>
                    {detail.reportData.impactLabel}
                  </span>
                </div>

                {/* 내 사업 영향도 */}
                <div className="bg-primary-50 rounded-xl border border-primary-100 p-4">
                  <p className="text-xs font-bold text-primary-600 mb-4">내 사업 영향도 미리보기</p>
                  <div className="flex flex-col gap-3">
                    {detail.businessImpact.map((item) => (
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

                {/* 요약 */}
                <p className="text-sm text-gray-700 leading-6">{detail.reportData.summary}</p>

                {/* 상세 분석 */}
                <div className="border-t border-gray-100 pt-4">
                  <p className="text-xs text-gray-400 mb-2.5">상세 분석</p>
                  <div className="flex flex-col gap-3">
                    {detail.reportData.details.map((text, i) => (
                      <p key={i} className="text-xs text-gray-600 leading-5">{text}</p>
                    ))}
                  </div>
                </div>

                {/* 함께 신청하면 좋아요 */}
                {detail.reportData.relatedIds.length > 0 && (
                  <div className="border-t border-gray-100 pt-4">
                    <p className="text-xs text-gray-400 mb-2.5">함께 신청하면 좋아요</p>
                    <div className="flex flex-col gap-2">
                      {detail.reportData.relatedIds.map((relId) => {
                        const rel = MOCK_POLICIES.find((p) => p.id === relId);
                        if (!rel) return null;
                        return (
                          <button
                            key={relId}
                            onClick={() => navigate(`/policies/${relId}`)}
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
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* 저장 완료 배너 */}
                <div className="flex items-start gap-2 bg-green-50 border border-green-100 rounded-xl p-3">
                  <div className="w-5 h-5 rounded-full bg-green-400 flex items-center justify-center shrink-0 mt-0.5">
                    <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
                      <polyline points="20 6 9 17 4 12"/>
                    </svg>
                  </div>
                  <p className="text-xs text-green-700 leading-5">리포트가 저장되었어요! 리포트 모아보기에서 언제든 확인할 수 있어요.</p>
                </div>

                {/* 리포트 모아보기 버튼 */}
                <Link
                  to="/reports"
                  className="w-full flex items-center justify-center gap-1.5 text-sm font-semibold text-gray-700 border border-gray-200 py-3 rounded-xl hover:bg-gray-50 transition-colors"
                >
                  리포트 모아보기에서 확인
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <line x1="5" y1="12" x2="19" y2="12"/>
                    <polyline points="12 5 19 12 12 19"/>
                  </svg>
                </Link>
              </div>
            )}
          </div>

          {/* Back link */}
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
