import type { PolicyDetailData } from "./presentation.ts";

export interface LoadedPolicyDetail {
  policyId: number;
  detail: PolicyDetailData;
}

export type PolicyDetailLoadTag = "loading" | "ready" | "missing" | "error";

export interface PolicyDetailLoadState {
  tag: PolicyDetailLoadTag;
  policyId: number;
}

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
): PolicyDetailLoadTag {
  const belongsToRoute = isPolicyDetailForRoute(loadedDetail, policyId);
  if (loadState.policyId !== policyId
      || loadState.tag === "loading"
      || (loadedDetail !== null && !belongsToRoute)) {
    return "loading";
  }
  if (loadState.tag === "ready") return belongsToRoute ? "ready" : "loading";
  return loadState.tag;
}
