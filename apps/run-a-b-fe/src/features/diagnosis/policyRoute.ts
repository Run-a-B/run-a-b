import type { PolicyDetailData } from "@/features/policy/presentation";

export interface LoadedPolicy {
  policyId: number;
  detail: PolicyDetailData;
}

export function isLoadedPolicyForRoute(
  loadedPolicy: LoadedPolicy | null,
  policyId: number,
): boolean {
  return loadedPolicy?.policyId === policyId;
}
