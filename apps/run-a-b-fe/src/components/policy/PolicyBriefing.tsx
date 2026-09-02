import {
  buildPolicyBrief,
  buildPolicyQuality,
  type PolicyDetailData,
} from "@/features/policy/presentation";

interface PolicyBriefingProps {
  detail: PolicyDetailData;
}

function sourceLabel(sourceSystem: string | null): string {
  if (sourceSystem === "bizinfo") return "기업마당";
  return sourceSystem?.trim() || "출처 정보 없음";
}

function availableCardClass(label: string): string {
  if (label === "지원 대상") return "rounded-xl border border-blue-100 bg-blue-50 px-4 py-3";
  if (label === "지원 내용") return "rounded-xl border border-primary-100 bg-primary-50 px-4 py-3";
  return "rounded-xl border border-gray-200 bg-white px-4 py-3";
}

function availableLabelClass(label: string): string {
  if (label === "지원 대상") return "text-xs font-semibold text-blue-700 mb-1";
  if (label === "지원 내용") return "text-xs font-semibold text-primary-600 mb-1";
  return "text-xs font-semibold text-gray-600 mb-1";
}

export function PolicyBriefing({ detail }: PolicyBriefingProps) {
  const briefItems = buildPolicyBrief(detail);
  const quality = buildPolicyQuality(detail);

  return (
    <section className="mb-8" aria-labelledby="policy-briefing-heading">
      <h2 id="policy-briefing-heading" className="text-lg font-bold text-gray-900 mb-3">
        한눈에 보기
      </h2>
      <div className="grid grid-cols-3 gap-3 mb-4">
        {briefItems.map((item) => (
          <div
            key={item.label}
            className={item.available
              ? availableCardClass(item.label)
              : "rounded-xl border border-gray-200 bg-gray-50 px-4 py-3"}
          >
            <p className={item.available ? availableLabelClass(item.label) : "text-xs font-semibold text-gray-500 mb-1"}>
              {item.label}
            </p>
            <p className="text-sm text-gray-700 leading-6 whitespace-pre-line">{item.value}</p>
          </div>
        ))}
      </div>

      <div className="rounded-xl border border-gray-200 bg-white px-4 py-3">
        <div className="flex items-center justify-between gap-3 mb-2">
          <h3 className="text-sm font-semibold text-gray-800">정보 품질</h3>
          <span className="text-xs font-medium text-gray-500">출처: {sourceLabel(detail.sourceSystem)}</span>
        </div>
        <div className="flex flex-col gap-1 text-xs text-gray-600 leading-5">
          <p>{`확인 가능한 항목 수: ${quality.availableCount}/${quality.totalCount}`}</p>
          <p>확인되지 않은 항목: {quality.missingLabels.length > 0 ? quality.missingLabels.join(", ") : "없음"}</p>
          <p>구조화 조건 상태: 미확인</p>
          <p className="text-gray-500">원문 공고가 최종 기준입니다.</p>
        </div>
      </div>
    </section>
  );
}
