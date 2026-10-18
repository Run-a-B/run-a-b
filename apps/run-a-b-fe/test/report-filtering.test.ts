import assert from "node:assert/strict";
import test from "node:test";
import type { SavedReport } from "../src/data/reports.ts";
import { filterReports } from "../src/features/reports/filtering.ts";

function report(policyId: number, direction: SavedReport["direction"], day: string,
  policyTitle = "정책", summary = "조건 검토"): SavedReport {
  return { id: `report_${policyId}`, policyId, policyTitle, summary, direction,
    savedAt: `2026-10-${day}T12:00:00`, impactLabel: "조건 확인", impactStyle: "bg-gray-100 text-gray-600",
    details: [], relatedIds: [], businessImpact: [] };
}

const reports = [
  report(1, "positive", "16"),
  report(2, "negative", "17"),
  report(3, "neutral", "19", "서울 지원", "신청 조건 검토"),
  report(4, "neutral", "18", "부산 지원", "서울 이전 조건 검토"),
  report(5, undefined, "15"),
];

test("additional review shows only neutral reports with newest first", () => {
  assert.deepEqual(filterReports(reports, "", "추가 확인").map(item => item.policyId), [3, 4]);
});

test("positive impact preserves legacy positives and excludes uncertain reports", () => {
  assert.deepEqual(filterReports(reports, "", "긍정 영향").map(item => item.policyId), [1]);
});

test("negative impact preserves existing mismatch reports", () => {
  assert.deepEqual(filterReports(reports, "", "부정 영향").map(item => item.policyId), [2]);
});

test("additional review applies title and summary searches", () => {
  assert.deepEqual(filterReports(reports, "서울", "추가 확인").map(item => item.policyId), [3, 4]);
  assert.deepEqual(filterReports(reports, "부산", "추가 확인").map(item => item.policyId), [4]);
  assert.deepEqual(filterReports(reports, "제주", "추가 확인"), []);
});

test("date sorting retains every direction and leaves saved report order intact", () => {
  assert.deepEqual(filterReports(reports, "", "최신순").map(item => item.policyId), [3, 4, 2, 1, 5]);
  assert.deepEqual(filterReports(reports, "", "오래된순").map(item => item.policyId), [5, 1, 2, 4, 3]);
  assert.deepEqual(reports.map(item => item.policyId), [1, 2, 3, 4, 5]);
});
