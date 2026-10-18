import type { EvidenceStatus, Verdict } from "../diagnosis/model.ts";
export interface PolicyDetailData {
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
  category: string | null;
  region: string | null;
  industry: string | null;
  agency: string | null;
  sourceSystem: string | null;
  extractionStatus?: string | null;
  originalNoticeText?: string | null;
  originalApplicationMethod?: string | null;
  originalApplicationPeriod?: string | null;
  executingAgency?: string | null;
  inquiryContact?: string | null;
  sourceAttachments?: {
    name: string | null;
    url: string | null;
    role: "attachment" | "notice";
  }[] | null;
  tags?: string[];
  targetConditions?: string[];
  supportItems?: { category: string; amount: string; method: string }[];
  exclusions?: string[];
  aiSummaryText?: string | null;
  aiHighlights?: { icon: string; label: string; content: string }[];
  businessImpact?: unknown[];
  reportData?: unknown;
  applicationChecklist?: unknown[];
}
export const statusPresentation: Record<
  EvidenceStatus | Verdict,
  { label: string; className: string }
> = {
  PASS: {
    label: "일치",
    className: "bg-green-100 text-green-700 border-green-200",
  },
  PARTIAL: {
    label: "부분 일치",
    className: "bg-yellow-100 text-yellow-700 border-yellow-200",
  },
  UNKNOWN: {
    label: "확인 필요",
    className: "bg-gray-100 text-gray-600 border-gray-200",
  },
  FAIL: {
    label: "불일치",
    className: "bg-red-100 text-red-700 border-red-200",
  },
  NOT_REQUIRED: {
    label: "조건 없음",
    className: "bg-gray-100 text-gray-600 border-gray-200",
  },
  NOT_ELIGIBLE: {
    label: "현재 대상 아님",
    className: "bg-red-100 text-red-700 border-red-200",
  },
  ON_HOLD: {
    label: "판정 보류",
    className: "bg-gray-100 text-gray-600 border-gray-200",
  },
  NEEDS_CONFIRMATION: {
    label: "조건 확인 필요",
    className: "bg-yellow-100 text-yellow-700 border-yellow-200",
  },
  LIKELY_ELIGIBLE: {
    label: "신청 가능성 높음",
    className: "bg-green-100 text-green-700 border-green-200",
  },
};

export function evidenceStatusPresentation(
  key: string,
  status: EvidenceStatus,
): { label: string; className: string } {
  const presentation = statusPresentation[status];
  return key === "date" && status === "PARTIAL"
    ? { ...presentation, label: "접수 예정" }
    : presentation;
}

export function firstSafeExternalUrl(
  ...candidates: Array<string | null | undefined>
): string | null {
  for (const candidate of candidates) {
    if (!candidate?.trim()) continue;
    try {
      const url = new URL(candidate);
      if (url.protocol === "https:" || url.protocol === "http:") return url.href;
    } catch {
      // Try the next stored source URL.
    }
  }
  return null;
}

export function structuredConditionLabel(extractionStatus: string | null | undefined): string {
  if (extractionStatus === "extracted") return "완료";
  if (extractionStatus === "partially_extracted") return "부분 확인";
  return "미확인";
}

export interface BriefItem {
  label: string;
  value: string;
  available: boolean;
}

export interface RelevancePresentation {
  available: boolean;
  label: string;
  value: number | null;
  width: string;
}

export interface PolicyNotice {
  overview: string[];
  keyPoints: string[];
  targetGroup: string | null;
  applicationPeriod: string | null;
  applicationMethod: string | null;
}

function isPresent(value: string | null | undefined): boolean {
  return Boolean(value?.trim());
}

function uniquePresent(values: Array<string | null | undefined>): string[] {
  return [...new Set(values.filter(isPresent).map((value) => value!.trim()))];
}

export function relevancePresentation(
  relevance: number | null | undefined,
): RelevancePresentation {
  if (relevance === null || relevance === undefined || !Number.isFinite(relevance)) {
    return {
      available: false,
      label: "로그인 후 분석",
      value: null,
      width: "0%",
    };
  }

  const value = Math.min(100, Math.max(0, Math.round(relevance)));
  return {
    available: true,
    label: `관련도 ${value}%`,
    value,
    width: `${value}%`,
  };
}

export function buildPolicyNotice(policy: PolicyDetailData): PolicyNotice {
  const description = policy.description?.trim() ?? "";
  const descriptionOverview: string[] = [];
  const keyPoints: string[] = [];

  if (description.includes("☞")) {
    const [overview, ...points] = description.split(/\s*☞\s*/u);
    if (overview?.trim()) descriptionOverview.push(overview.trim());
    keyPoints.push(...points);
  } else if (description) {
    const lines = description.split(/\r?\n/u).map((line) => line.trim()).filter(Boolean);
    const bulletPattern = /^(?:[•·▪■▶※]|[-–—])\s*/u;
    if (lines.some((line) => bulletPattern.test(line))) {
      for (const line of lines) {
        if (bulletPattern.test(line)) {
          keyPoints.push(line.replace(bulletPattern, ""));
        } else {
          descriptionOverview.push(line);
        }
      }
    } else {
      descriptionOverview.push(description);
    }
  }

  return {
    overview: uniquePresent([policy.purposeText, ...descriptionOverview]),
    keyPoints: uniquePresent(keyPoints),
    targetGroup: firstPresent(policy.targetGroup),
    applicationPeriod: firstPresent(policy.applicationPeriod),
    applicationMethod: firstPresent(policy.applicationMethod),
  };
}

function firstPresent(
  ...values: Array<string | null | undefined>
): string | null {
  return values.find(isPresent)?.trim() ?? null;
}

export function buildPolicyBrief(policy: PolicyDetailData): BriefItem[] {
  const target = firstPresent(policy.targetGroup);
  const support = firstPresent(policy.supportScale);
  const period = firstPresent(policy.applicationPeriod);
  return [
    {
      label: "지원 대상",
      value: target ?? "원문에서 지원 대상을 확인해 주세요.",
      available: target !== null,
    },
    {
      label: "지원 내용",
      value: support ?? "지원 금액·방식 정보가 아직 수집되지 않았어요.",
      available: support !== null,
    },
    {
      label: "신청 기간",
      value: period ?? "기간 정보 없음 — 원문 공고 확인",
      available: period !== null,
    },
  ];
}
export function buildPolicyQuality(detail: PolicyDetailData) {
  const facts = [
    detail.targetGroup,
    detail.supportScale,
    detail.applicationPeriod,
    firstPresent(detail.purposeText, detail.description),
    detail.applicationMethod,
    detail.announcementNo,
    detail.department,
  ];
  const labels = [
    "지원 대상",
    "지원 내용",
    "신청 기간",
    "사업 내용",
    "신청 방법",
    "공고 번호",
    "담당 부서",
  ];
  const missingLabels = labels.filter((_, index) => !isPresent(facts[index]));
  const availableCount = labels.length - missingLabels.length;
  const level =
    availableCount === 7 ? "rich" : availableCount >= 3 ? "partial" : "basic";
  return { availableCount, totalCount: 7, missingLabels, level };
}
