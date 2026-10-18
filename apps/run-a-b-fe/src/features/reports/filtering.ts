import type { SavedReport } from "../../data/reports.ts";

export const REPORT_TABS = ["최신순", "오래된순", "부정 영향", "긍정 영향", "추가 확인"] as const;
export type ReportTab = (typeof REPORT_TABS)[number];

export function filterReports(reports: readonly SavedReport[], query: string, tab: ReportTab): SavedReport[] {
  return reports
    .filter(report => {
      if (query && !report.policyTitle.includes(query) && !report.summary.includes(query)) return false;
      if (tab === "부정 영향" && report.direction !== "negative") return false;
      if (tab === "긍정 영향" && report.direction !== "positive") return false;
      if (tab === "추가 확인" && report.direction !== "neutral") return false;
      return true;
    })
    .sort((a, b) => tab === "오래된순"
      ? a.savedAt.localeCompare(b.savedAt)
      : b.savedAt.localeCompare(a.savedAt));
}
