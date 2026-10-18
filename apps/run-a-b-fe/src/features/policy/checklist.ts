export interface ChecklistItem {
  id: string;
  label: string;
  required: boolean;
  description?: string | null;
}

export interface PolicyChecklistDetail {
  applicationPeriod: string;
  department: string;
  applicationUrl: string | null;
  applicationChecklist: ChecklistItem[];
}

export interface ChecklistPolicy {
  id: number;
  title: string;
  category: string | null;
  applicationUrl?: string | null;
  detailUrl?: string | null;
}

export interface ChecklistApiClient {
  get(url: string): Promise<{ data: { data: unknown } }>;
}

export function safeExternalUrl(value: string | null | undefined): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === "https:" || url.protocol === "http:" ? url.href : null;
  } catch {
    return null;
  }
}

export async function loadPolicyChecklist(client: ChecklistApiClient, id: number) {
  const [policyResponse, checklistResponse] = await Promise.all([
    client.get(`/api/v1/policies/${id}`),
    client.get(`/api/v1/policies/${id}/checklist`),
  ]);
  const policy = policyResponse.data.data as ChecklistPolicy;
  const detail = checklistResponse.data.data as PolicyChecklistDetail;
  const sourceUrl = safeExternalUrl(detail.applicationUrl)
    ?? safeExternalUrl(policy.applicationUrl)
    ?? safeExternalUrl(policy.detailUrl);

  return { policy, detail, sourceUrl };
}

export type ChecklistProgress = {
  kind: "required" | "recommendations";
  groupTitle: string;
  notice: string | null;
  completed: number;
  total: number;
};

export type CheckedByPolicy = Record<number, Record<string, boolean>>;

export function checkedForPolicy(
  checkedByPolicy: CheckedByPolicy,
  policyId: number,
): Record<string, boolean> {
  return checkedByPolicy[policyId] ?? {};
}

export function retryChecklistLoad(policyId: number) {
  return { tag: "loading" as const, policyId };
}

export function buildChecklistProgress(
  checklist: ChecklistItem[],
  checked: Record<string, boolean> = {},
): ChecklistProgress {
  const required = checklist.filter((item) => item.required);
  if (required.length > 0) {
    return {
      kind: "required",
      groupTitle: "필수 서류",
      notice: null,
      completed: required.filter((item) => checked[item.id]).length,
      total: required.length,
    };
  }

  return {
    kind: "recommendations",
    groupTitle: "일반 준비 권장",
    notice: "필수 여부는 원문 공고에서 확인해 주세요",
    completed: checklist.filter((item) => checked[item.id]).length,
    total: checklist.length,
  };
}
