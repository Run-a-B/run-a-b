import type { SavedReport } from "../../data/reports.ts";

export type ReportDetailState =
  | { tag: "loading" | "missing" | "error"; policyId: number }
  | { tag: "ready"; policyId: number; report: SavedReport };

// The data reader converts only actual HTTP 404 responses to null; transport/5xx failures reject.
export async function loadReportDetail(
  readReport: (policyId: number) => Promise<SavedReport | null>,
  policyId: number,
  isActive: () => boolean,
  update: (state: ReportDetailState) => void,
) {
  if (!isActive()) return;
  if (!Number.isSafeInteger(policyId) || policyId < 1) {
    update({ tag: "missing", policyId });
    return;
  }
  update({ tag: "loading", policyId });
  try {
    const report = await readReport(policyId);
    if (isActive()) update(report ? { tag: "ready", policyId, report } : { tag: "missing", policyId });
  } catch {
    if (isActive()) update({ tag: "error", policyId });
  }
}
