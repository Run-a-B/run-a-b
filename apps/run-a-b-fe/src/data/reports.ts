export interface SavedReport {
  id: string;
  policyId: number;
  policyTitle: string;
  savedAt: string;
  impactLabel: string;
  impactStyle: string;
  summary: string;
  details: string[];
  relatedIds: number[];
}

const KEY = "rab_reports";

export function getSavedReports(): SavedReport[] {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? "[]");
  } catch {
    return [];
  }
}

export function saveReport(report: Omit<SavedReport, "id" | "savedAt">): SavedReport {
  const reports = getSavedReports();
  const newReport: SavedReport = {
    ...report,
    id: `report_${report.policyId}`,
    savedAt: new Date().toISOString(),
  };
  const idx = reports.findIndex((r) => r.policyId === report.policyId);
  if (idx >= 0) {
    reports[idx] = newReport;
  } else {
    reports.unshift(newReport);
  }
  localStorage.setItem(KEY, JSON.stringify(reports));
  return newReport;
}

export function deleteReport(policyId: number): void {
  const reports = getSavedReports().filter((r) => r.policyId !== policyId);
  localStorage.setItem(KEY, JSON.stringify(reports));
}
