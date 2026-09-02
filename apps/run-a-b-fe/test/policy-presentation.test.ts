import test from "node:test";
import assert from "node:assert/strict";
import {
  buildPolicyBrief,
  buildPolicyQuality,
  statusPresentation,
  type PolicyDetailData,
} from "../src/features/policy/presentation.ts";
const policy = (
  overrides: Partial<PolicyDetailData> = {},
): PolicyDetailData => ({
  id: 1,
  title: "정책",
  description: null,
  date: null,
  announcementNo: null,
  department: null,
  applicationPeriod: null,
  supportScale: null,
  targetGroup: null,
  purposeText: null,
  applicationMethod: null,
  applicationUrl: null,
  detailUrl: null,
  category: null,
  region: null,
  industry: null,
  agency: null,
  sourceSystem: null,
  ...overrides,
});
test("missing support scale is an explicit unknown brief item", () => {
  const items = buildPolicyBrief(policy());
  assert.equal(
    items.map((i) => i.label).join(","),
    "지원 대상,지원 내용,신청 기간",
  );
  assert.equal(items[0].value, "원문에서 지원 대상을 확인해 주세요.");
  assert.equal(items[1].value, "지원 금액·방식 정보가 아직 수집되지 않았어요.");
  assert.equal(items[1].available, false);
});
test("brief preserves target support period order and values", () => {
  assert.deepEqual(
    buildPolicyBrief(
      policy({
        targetGroup: "소상공인",
        supportScale: "최대 500만원",
        applicationPeriod: "2026.09.01 ~ 09.30",
      }),
    ).map((i) => i.value),
    ["소상공인", "최대 500만원", "2026.09.01 ~ 09.30"],
  );
});
test("quality audits seven stored fields in fixed order", () => {
  assert.deepEqual(
    buildPolicyQuality(
      policy({
        targetGroup: "대상",
        purposeText: "내용",
        announcementNo: "공고",
        department: "부서",
      }),
    ),
    {
      availableCount: 4,
      totalCount: 7,
      missingLabels: ["지원 내용", "신청 기간", "신청 방법"],
      level: "partial",
    },
  );
});
test("quality level thresholds are rich, partial, and basic", () => {
  assert.equal(
    buildPolicyQuality(
      policy({
        targetGroup: "a",
        supportScale: "b",
        applicationPeriod: "c",
        purposeText: "d",
        applicationMethod: "e",
        announcementNo: "f",
        department: "g",
      }),
    ).level,
    "rich",
  );
  assert.equal(
    buildPolicyQuality(policy({ targetGroup: "a", supportScale: "b" })).level,
    "basic",
  );
});
test("quality reports exact missing labels for rich, partial, and basic", () => {
  assert.deepEqual(
    buildPolicyQuality(
      policy({
        targetGroup: "a",
        supportScale: "b",
        applicationPeriod: "c",
        purposeText: "d",
        applicationMethod: "e",
        announcementNo: "f",
        department: "g",
      }),
    ),
    { availableCount: 7, totalCount: 7, missingLabels: [], level: "rich" },
  );
  assert.deepEqual(
    buildPolicyQuality(
      policy({ targetGroup: "a", supportScale: "b", purposeText: "d" }),
    ),
    {
      availableCount: 3,
      totalCount: 7,
      missingLabels: ["신청 기간", "신청 방법", "공고 번호", "담당 부서"],
      level: "partial",
    },
  );
  assert.deepEqual(buildPolicyQuality(policy()), {
    availableCount: 0,
    totalCount: 7,
    missingLabels: [
      "지원 대상",
      "지원 내용",
      "신청 기간",
      "사업 내용",
      "신청 방법",
      "공고 번호",
      "담당 부서",
    ],
    level: "basic",
  });
});
test("quality falls back from blank purpose to description", () => {
  assert.equal(
    buildPolicyQuality(
      policy({ purposeText: "  ", description: "설명" }),
    ).missingLabels.includes("사업 내용"),
    false,
  );
});
test("brief treats whitespace-only values as missing", () => {
  const items = buildPolicyBrief(
    policy({ targetGroup: "  ", supportScale: "\t", applicationPeriod: "  " }),
  );
  assert.deepEqual(
    items.map((item) => [item.value, item.available]),
    [
      ["원문에서 지원 대상을 확인해 주세요.", false],
      ["지원 금액·방식 정보가 아직 수집되지 않았어요.", false],
      ["기간 정보 없음 — 원문 공고 확인", false],
    ],
  );
});
test("status presentation uses exact Korean labels and classes", () => {
  const expected = {
    PASS: ["일치", "bg-green-100 text-green-700 border-green-200"],
    PARTIAL: ["부분 일치", "bg-yellow-100 text-yellow-700 border-yellow-200"],
    UNKNOWN: ["확인 필요", "bg-gray-100 text-gray-600 border-gray-200"],
    FAIL: ["불일치", "bg-red-100 text-red-700 border-red-200"],
    NOT_REQUIRED: ["조건 없음", "bg-gray-100 text-gray-600 border-gray-200"],
    NOT_ELIGIBLE: ["현재 대상 아님", "bg-red-100 text-red-700 border-red-200"],
    ON_HOLD: ["판정 보류", "bg-gray-100 text-gray-600 border-gray-200"],
    NEEDS_CONFIRMATION: [
      "조건 확인 필요",
      "bg-yellow-100 text-yellow-700 border-yellow-200",
    ],
    LIKELY_ELIGIBLE: [
      "신청 가능성 높음",
      "bg-green-100 text-green-700 border-green-200",
    ],
  } as const;
  for (const [key, [label, className]] of Object.entries(expected)) {
    assert.deepEqual(
      statusPresentation[key as keyof typeof statusPresentation],
      { label, className },
    );
  }
});
