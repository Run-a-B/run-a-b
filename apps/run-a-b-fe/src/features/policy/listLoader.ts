import type { Policy } from "../../data/policies.ts";

type PolicyPage = { policies: Policy[]; totalPages: number; totalElements: number };
export type PolicyListState =
  | { tag: "loading" }
  | { tag: "error" }
  | { tag: "success"; data: PolicyPage };

type PolicyListClient = { get(url: string): Promise<{ data: { data: PolicyPage } }> };

export async function loadPolicyList(
  client: PolicyListClient,
  params: string,
  isActive: () => boolean,
  update: (state: PolicyListState) => void,
) {
  if (!isActive()) return;
  update({ tag: "loading" });
  try {
    const response = await client.get(`/api/v1/policies?${params}`);
    if (isActive()) update({ tag: "success", data: response.data.data });
  } catch {
    if (isActive()) update({ tag: "error" });
  }
}
