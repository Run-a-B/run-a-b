import {
  buildPolicyNotice,
  firstSafeExternalUrl,
  type PolicyDetailData,
} from "@/features/policy/presentation";

export interface PolicyNoticeSummary {
  expandedDescription?: string | null;
  expandedApplicationMethod?: string | null;
}

interface PolicyNoticeDetailsProps {
  detail: PolicyDetailData;
  summary: PolicyNoticeSummary | null;
}

function FactCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-xl border border-gray-200 bg-gray-50 px-4 py-3">
      <dt className="mb-1 text-xs font-semibold text-gray-500">{label}</dt>
      <dd className="min-w-0 text-sm leading-6 text-gray-700 whitespace-pre-wrap [overflow-wrap:anywhere]">
        {value}
      </dd>
    </div>
  );
}

export function PolicyNoticeDetails({ detail, summary }: PolicyNoticeDetailsProps) {
  const originalNoticeText = detail.originalNoticeText?.trim() ? detail.originalNoticeText : null;
  const notice = originalNoticeText ? null : buildPolicyNotice(detail);
  const expandedDescription = summary?.expandedDescription?.trim();
  const applicationMethod = detail.originalApplicationMethod?.trim()
    ? detail.originalApplicationMethod : detail.applicationMethod;
  const expandedApplicationMethod = summary?.expandedApplicationMethod?.trim();
  const applicationPeriod = detail.originalApplicationPeriod?.trim()
    ? detail.originalApplicationPeriod : detail.applicationPeriod;
  const originalNoticeUrl = firstSafeExternalUrl(detail.detailUrl, detail.applicationUrl);
  const hasStoredContent = Boolean(originalNoticeText || notice?.overview.length || notice?.keyPoints.length);

  return (
    <section className="mb-8 min-w-0" aria-labelledby="policy-notice-details-heading">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h2 id="policy-notice-details-heading" className="text-lg font-bold text-gray-900">
          상세 공고 내용
        </h2>
        <span className="rounded-full border border-gray-200 bg-gray-50 px-2.5 py-1 text-xs font-medium text-gray-500">
          수집된 공고문 기반
        </span>
      </div>

      <div className="flex flex-col gap-5">
        {expandedDescription && (
          <div className="min-w-0 rounded-xl border border-primary-100 bg-primary-50 px-5 py-4">
            <div className="mb-2 flex items-center gap-2">
              <h3 className="text-sm font-bold text-primary-700">자동 정리된 사업 내용</h3>
              <span className="rounded-full bg-white px-2 py-0.5 text-[11px] font-medium text-primary-600">
                요약
              </span>
            </div>
            <p className="min-w-0 text-sm leading-7 text-gray-700 whitespace-pre-line [overflow-wrap:anywhere]">
              {expandedDescription}
            </p>
          </div>
        )}

        {originalNoticeText && (
          <div className="min-w-0 text-base leading-8 text-gray-700 whitespace-pre-wrap [overflow-wrap:anywhere]">
            {originalNoticeText}
          </div>
        )}

        {notice && notice.overview.length > 0 && (
          <div>
            <h3 className="mb-2 text-sm font-bold text-gray-800">사업 개요</h3>
            <div className="flex flex-col gap-2">
              {notice.overview.map((paragraph) => (
                <p
                  key={paragraph}
                  className="min-w-0 text-base leading-8 text-gray-700 whitespace-pre-line [overflow-wrap:anywhere]"
                >
                  {paragraph}
                </p>
              ))}
            </div>
          </div>
        )}

        {notice && notice.keyPoints.length > 0 && (
          <div>
            <h3 className="mb-3 text-sm font-bold text-gray-800">공고문 핵심 항목</h3>
            <ol className="grid grid-cols-1 gap-3 md:grid-cols-2">
              {notice.keyPoints.map((point, index) => (
                <li key={point} className="flex min-w-0 gap-3 rounded-xl border border-gray-200 bg-white px-4 py-3">
                  <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-primary-50 text-xs font-bold text-primary-600">
                    {index + 1}
                  </span>
                  <span className="min-w-0 text-sm leading-6 text-gray-700 [overflow-wrap:anywhere]">
                    {point}
                  </span>
                </li>
              ))}
            </ol>
          </div>
        )}

        {!hasStoredContent && !expandedDescription && (
          <p className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm text-gray-600">
            수집된 사업 설명이 없어 원문 공고 확인이 필요해요.
          </p>
        )}

        {(detail.targetGroup?.trim() || detail.supportScale?.trim() || applicationPeriod?.trim()
          || detail.executingAgency?.trim() || detail.inquiryContact?.trim()) && (
          <dl className="grid grid-cols-1 gap-3 md:grid-cols-2">
            {detail.targetGroup?.trim() && <FactCard label="지원 대상" value={detail.targetGroup} />}
            {detail.supportScale?.trim() && <FactCard label="지원 규모" value={detail.supportScale.trim()} />}
            {applicationPeriod?.trim() && <FactCard label="신청 기간" value={applicationPeriod} />}
            {detail.executingAgency?.trim() && <FactCard label="수행 기관" value={detail.executingAgency} />}
            {detail.inquiryContact?.trim() && <FactCard label="문의처" value={detail.inquiryContact} />}
          </dl>
        )}

        <div>
          <h3 className="mb-2 text-sm font-bold text-gray-800">신청 방법</h3>
          {applicationMethod?.trim() ? (
            <p className="min-w-0 rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm leading-7 text-gray-700 whitespace-pre-wrap [overflow-wrap:anywhere]">
              {applicationMethod}
            </p>
          ) : (
            <p className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm text-gray-600">
              신청 방법 정보가 아직 수집되지 않았어요.
            </p>
          )}
        </div>

        {expandedApplicationMethod && (
          <div className="min-w-0 rounded-xl border border-primary-100 bg-primary-50 px-5 py-4">
            <h3 className="mb-2 text-sm font-bold text-primary-700">AI가 정리한 신청 방법</h3>
            <p className="min-w-0 text-sm leading-7 text-gray-700 whitespace-pre-wrap [overflow-wrap:anywhere]">
              {expandedApplicationMethod}
            </p>
          </div>
        )}

        {Boolean(detail.sourceAttachments?.length) && (
          <div className="min-w-0">
            <h3 className="mb-2 text-sm font-bold text-gray-800">공식 첨부 자료</h3>
            <ul className="flex min-w-0 flex-col gap-3">
              {detail.sourceAttachments?.map((attachment, index) => {
                const safeUrl = firstSafeExternalUrl(attachment.url);
                const label = attachment.name?.trim() ? attachment.name : attachment.url;
                return (
                  <li key={`${attachment.role}-${index}`} className="min-w-0 rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm [overflow-wrap:anywhere]">
                    <span className="mb-1 block text-xs font-semibold text-gray-500">
                      {attachment.role === "notice" ? "공고문 파일" : "첨부파일"}
                    </span>
                    {safeUrl ? (
                      <a href={safeUrl} target="_blank" rel="noopener noreferrer" className="text-primary-600 hover:underline">
                        {label}
                      </a>
                    ) : (
                      <span className="text-gray-700">{label}</span>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        )}

        <div className="flex flex-wrap items-center gap-3 rounded-xl border border-primary-100 bg-primary-50 px-5 py-4">
          <p className="min-w-0 flex-1 text-sm text-gray-600">공고문 원문이 최종 기준입니다.</p>
          {originalNoticeUrl && (
            <a
              href={originalNoticeUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-11 shrink-0 items-center text-sm font-semibold text-primary-600 hover:underline"
            >
              원문 공고 보기 →
            </a>
          )}
        </div>
      </div>
    </section>
  );
}
