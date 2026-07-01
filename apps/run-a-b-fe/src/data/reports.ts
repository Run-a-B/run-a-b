import api from "@/lib/api";

export interface BusinessImpactItem {
  label: string;
  level: number;
  direction: "up" | "down";
  tag: string;
  barColor: string;
  tagColor: string;
}

export interface RelatedPolicy {
  id: number;
  title: string;
}

export interface SavedReport {
  id: string;
  policyId: number;
  policyTitle: string;
  category?: string;
  savedAt: string;
  impactLabel: string;
  impactStyle: string;
  direction?: "positive" | "negative";
  summary: string;
  details: string[];
  relatedIds: RelatedPolicy[];
  businessImpact?: BusinessImpactItem[];
}

// 백엔드 응답(PolicyReportResponse)에는 id가 없으므로 policyId로 안정적인 키를 만들어 붙인다.
// (기존 localStorage 시절 `report_${policyId}` 규칙 유지 — 컴포넌트 key/링크 호환)
function normalize(r: Omit<SavedReport, "id">): SavedReport {
  return {
    ...r,
    id: `report_${r.policyId}`,
    details: r.details ?? [],
    relatedIds: r.relatedIds ?? [],
    businessImpact: r.businessImpact ?? [],
  };
}

// 내 리포트 목록 (로그인 사용자 스코프)
export async function getSavedReports(): Promise<SavedReport[]> {
  const res = await api.get("/api/v1/reports");
  const list = (res.data.data ?? []) as Omit<SavedReport, "id">[];
  return list.map(normalize);
}

// 특정 정책의 내 리포트. 없으면 null.
export async function getSavedReport(policyId: number): Promise<SavedReport | null> {
  try {
    const res = await api.get(`/api/v1/reports/${policyId}`);
    return normalize(res.data.data);
  } catch (err: any) {
    if (err.response?.status === 404) return null;
    throw err;
  }
}

// 내 리포트 삭제
export async function deleteReport(policyId: number): Promise<void> {
  await api.delete(`/api/v1/reports/${policyId}`);
}
