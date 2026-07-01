import { useState, useEffect } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { getSavedReport, deleteReport, type SavedReport } from "@/data/reports";

const CATEGORY_COLORS: Record<string, string> = {
  "기술": "bg-violet-100 text-violet-700",
  "금융": "bg-indigo-100 text-indigo-700",
  "인력": "bg-blue-100 text-blue-700",
  "경영": "bg-emerald-100 text-emerald-700",
  "창업": "bg-orange-100 text-orange-700",
  "수출": "bg-sky-100 text-sky-700",
  "내수": "bg-teal-100 text-teal-700",
  "기타": "bg-gray-100 text-gray-600",
  // 구형 mock 카테고리 호환
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

function formatDate(iso: string) {
  const d = new Date(iso);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
}

export default function ReportDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const policyId = Number(id);
  const [report, setReport] = useState<SavedReport | null | "notfound">(null);

  useEffect(() => {
    let active = true;
    setReport(null);
    getSavedReport(policyId)
      .then((found) => {
        if (active) setReport(found ?? "notfound");
      })
      .catch(() => {
        if (active) setReport("notfound");
      });
    return () => {
      active = false;
    };
  }, [policyId]);

  async function handleDelete() {
    try {
      await deleteReport(policyId);
      navigate("/reports", { replace: true });
    } catch (err: any) {
      alert(err.response?.data?.message || "리포트 삭제에 실패했어요");
    }
  }

  if (report === null) return null;

  if (report === "notfound") {
    return (
      <div className="flex flex-col items-center justify-center py-40 text-gray-400">
        <p className="text-lg font-semibold mb-2">리포트를 찾을 수 없어요</p>
        <button onClick={() => navigate("/reports")} className="mt-4 text-sm text-primary-600 hover:underline">
          리포트 목록으로 돌아가기
        </button>
      </div>
    );
  }

  const categoryColor = CATEGORY_COLORS[report.category ?? ""] ?? "bg-gray-100 text-gray-600";
  const businessImpact = report.businessImpact ?? [];

  return (
    <div className="bg-gray-50 min-h-screen">
      <div className="bg-white border-b border-gray-100 px-40 py-4">
        <nav className="flex items-center gap-1.5 text-sm text-gray-500">
          <Link to="/" className="hover:text-gray-700 transition-colors">홈</Link>
          <span className="text-gray-300">›</span>
          <Link to="/reports" className="hover:text-gray-700 transition-colors">내 리포트</Link>
          <span className="text-gray-300">›</span>
          <span className="text-gray-700 font-medium truncate max-w-xs">{report.policyTitle}</span>
        </nav>
      </div>

      <div className="max-w-3xl mx-auto px-8 py-10 flex flex-col gap-6">
        {/* Header */}
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-3 flex-wrap">
              {report.category && (
                <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${categoryColor}`}>
                  {report.category}
                </span>
              )}
              <span className="flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full bg-primary-50 text-primary-600 border border-primary-100">
                <span className="w-1.5 h-1.5 rounded-full bg-primary-400 inline-block" />
                AI 리포트
              </span>
              <span className="text-xs text-gray-400">저장일: {formatDate(report.savedAt)}</span>
            </div>
            <h1 className="text-2xl font-bold text-gray-900 leading-snug">{report.policyTitle}</h1>
          </div>
          <button
            onClick={handleDelete}
            className="shrink-0 p-2 text-gray-300 hover:text-red-400 transition-colors"
            aria-label="리포트 삭제"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
              <path d="M10 11v6"/><path d="M14 11v6"/>
              <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
            </svg>
          </button>
        </div>

        {/* Impact summary */}
        <div className="bg-white rounded-2xl border border-gray-200 p-6 flex flex-col gap-4">
          <div>
            <p className="text-xs text-gray-400 mb-2">영향 방향</p>
            <span className={`inline-flex items-center gap-1.5 text-sm font-semibold px-3 py-1.5 rounded-lg ${report.impactStyle}`}>
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
              </svg>
              {report.impactLabel}
            </span>
          </div>
          <p className="text-base text-gray-700 leading-8">{report.summary}</p>
        </div>

        {/* Business impact bars */}
        {businessImpact.length > 0 && (
          <div className="bg-primary-50 rounded-2xl border border-primary-100 p-6">
            <p className="text-sm font-bold text-primary-600 mb-5">내 사업 영향도</p>
            <div className="flex flex-col gap-4">
              {businessImpact.map((item) => (
                <div key={item.label} className="flex items-center gap-3">
                  <span className="text-sm text-gray-500 w-16 shrink-0">{item.label}</span>
                  <div className="flex-1 h-2.5 bg-white rounded-full overflow-hidden">
                    <div
                      className={`h-full rounded-full ${item.barColor}`}
                      style={{ width: `${item.level}%` }}
                    />
                  </div>
                  <span className={`flex items-center gap-1 text-sm font-semibold shrink-0 whitespace-nowrap w-16 ${item.tagColor}`}>
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

        {/* Detailed analysis */}
        {report.details.length > 0 && (
          <div className="bg-white rounded-2xl border border-gray-200 p-6 flex flex-col gap-4">
            <p className="text-sm font-bold text-gray-800">상세 분석</p>
            {report.details.map((text, i) => (
              <p key={i} className="text-base text-gray-600 leading-7">{text}</p>
            ))}
          </div>
        )}

        {/* Related policies */}
        {report.relatedIds.length > 0 && (
          <div className="bg-white rounded-2xl border border-gray-200 p-6 flex flex-col gap-3">
            <p className="text-sm font-bold text-gray-800">함께 신청하면 좋아요</p>
            {report.relatedIds.map((relId) => (
              <button
                key={relId}
                onClick={() => navigate(`/policies/${relId}`)}
                className="flex items-center gap-3 text-left bg-primary-50 hover:bg-primary-100 border border-primary-100 rounded-xl p-3 transition-colors"
              >
                <div className="w-8 h-8 rounded-lg bg-primary-100 flex items-center justify-center shrink-0">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-primary-500">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                  </svg>
                </div>
                <span className="flex-1 text-sm font-medium text-primary-700">관련 정책 보기</span>
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
            to={`/policies/${policyId}`}
            className="flex-1 flex items-center justify-center gap-1.5 text-sm font-semibold text-gray-600 border border-gray-200 py-3.5 rounded-xl hover:bg-gray-50 transition-colors bg-white"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
            </svg>
            정책 원문 보기
          </Link>
          <Link
            to={`/policies/${policyId}/checklist`}
            className="flex-1 flex items-center justify-center gap-1.5 text-sm font-semibold text-white bg-primary-600 hover:bg-primary-700 py-3.5 rounded-xl transition-colors"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M9 11l3 3L22 4"/>
              <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
            </svg>
            신청 준비하기
          </Link>
        </div>

        <button
          onClick={() => navigate("/reports")}
          className="flex items-center gap-1.5 text-sm text-gray-400 hover:text-gray-600 transition-colors justify-center py-2"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="19" y1="12" x2="5" y2="12"/>
            <polyline points="12 19 5 12 12 5"/>
          </svg>
          목록으로 돌아가기
        </button>
      </div>
    </div>
  );
}
