import type { PolicyDetailData } from "@/features/policy/presentation";

export interface LoadedPolicy {
  policyId: number;
  detail: PolicyDetailData;
}

export type PolicyLoadState = "loading" | "missing" | "error" | "ready";

export function isLoadedPolicyForRoute(
  loadedPolicy: LoadedPolicy | null,
  policyId: number,
): boolean {
  return loadedPolicy?.policyId === policyId;
}

export function diagnosisPolicyViewState(
  policyState: PolicyLoadState,
  loadedPolicy: LoadedPolicy | null,
  policyId: number,
): "loading" | "unavailable" | "ready" {
  const belongsToRoute = isLoadedPolicyForRoute(loadedPolicy, policyId);
  if (policyState === "loading" || (loadedPolicy !== null && !belongsToRoute)) return "loading";
  return policyState === "ready" && belongsToRoute ? "ready" : "unavailable";
}
