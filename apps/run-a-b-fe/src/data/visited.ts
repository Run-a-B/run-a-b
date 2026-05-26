const KEY = "rab_visited_policies";

export function getVisitedPolicies(): Set<number> {
  try {
    const arr = JSON.parse(localStorage.getItem(KEY) ?? "[]") as number[];
    return new Set(arr);
  } catch {
    return new Set();
  }
}

export function markPolicyVisited(policyId: number): void {
  const visited = getVisitedPolicies();
  visited.add(policyId);
  localStorage.setItem(KEY, JSON.stringify([...visited]));
}
