import type { PolicyDetailData } from "./presentation.ts";

export interface LoadedPolicyDetail {
  policyId: number;
  detail: PolicyDetailData;
}

export type PolicyDetailLoadState = "loading" | "ready" | "missing" | "error";

export function isPolicyDetailForRoute(
  loadedDetail: LoadedPolicyDetail | null,
  policyId: number,
): boolean {
  return loadedDetail?.policyId === policyId;
}

export function policyDetailViewState(
  loadState: PolicyDetailLoadState,
  loadedDetail: LoadedPolicyDetail | null,
  policyId: number,
): PolicyDetailLoadState {
  const belongsToRoute = isPolicyDetailForRoute(loadedDetail, policyId);
  if (loadState === "loading" || (loadedDetail !== null && !belongsToRoute)) {
    return "loading";
  }
  return loadState === "ready" && belongsToRoute ? "ready" : loadState;
}
