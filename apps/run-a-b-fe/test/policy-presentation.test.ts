import test from "node:test";
import assert from "node:assert/strict";
import {
  buildPolicyNotice,
  buildPolicyBrief,
  buildPolicyQuality,
  evidenceStatusPresentation,
  firstSafeExternalUrl,
  relevancePresentation,
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
  assert.equal(items[1].value, "지원 금액·방식은 아직 별도로 정리되지 않았어요. 아래 원문 공고에서 확인해 주세요.");
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
test("brief and notice use the original application period and method", () => {
  const detail = policy({
    applicationPeriod: "2026.09.01 ~ 09.30",
    originalApplicationPeriod: " 예산 소진시까지 ",
    applicationMethod: "온라인 접수",
    originalApplicationMethod: " 담당 기관에 이메일 접수 ",
  });
  assert.deepEqual(buildPolicyBrief(detail)[2], {
    label: "신청 기간", value: "예산 소진시까지", available: true,
  });
  assert.equal(buildPolicyNotice(detail).applicationPeriod, "예산 소진시까지");
  assert.equal(buildPolicyNotice(detail).applicationMethod, "담당 기관에 이메일 접수");
});
test("quality counts available original notice fields without inventing extracted conditions", () => {
  const quality = buildPolicyQuality(policy({
    originalApplicationPeriod: "예산 소진시까지",
    originalApplicationMethod: "방문 접수",
    originalNoticeText: "수집된 원문 사업 내용",
  }));
  assert.equal(quality.availableCount, 3);
  assert.deepEqual(quality.missingLabels, ["지원 대상", "지원 내용", "공고 번호", "담당 부서"]);
});
test("blank original fields do not hide available structured values", () => {
  const detail = policy({
    applicationPeriod: "2026.09.01 ~ 09.30", originalApplicationPeriod: " \t",
    applicationMethod: "온라인 접수", originalApplicationMethod: " ",
  });
  assert.equal(buildPolicyBrief(detail)[2].value, "2026.09.01 ~ 09.30");
  assert.equal(buildPolicyNotice(detail).applicationMethod, "온라인 접수");
  assert.equal(buildPolicyQuality(detail).availableCount, 2);
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
      ["지원 금액·방식은 아직 별도로 정리되지 않았어요. 아래 원문 공고에서 확인해 주세요.", false],
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

test("upcoming application dates use a truthful contextual status label", () => {
  assert.equal(evidenceStatusPresentation("date", "PARTIAL").label, "접수 예정");
  assert.equal(evidenceStatusPresentation("revenue", "PARTIAL").label, "부분 일치");
});

test("external policy links skip unsafe candidates and keep a safe fallback", () => {
  assert.equal(
    firstSafeExternalUrl("javascript:alert(1)", "https://example.com/original"),
    "https://example.com/original",
  );
  assert.equal(firstSafeExternalUrl("mailto:test@example.com", "not a url"), null);
  assert.equal(firstSafeExternalUrl("http://example.com/apply"), "http://example.com/apply");
});

test("missing relevance is unavailable instead of a fake zero percent", () => {
  assert.deepEqual(relevancePresentation(null), {
    available: false,
    label: "로그인 후 분석",
    value: null,
    width: "0%",
  });
});

test("missing relevance for a signed-in user does not ask them to log in or assume a missing business profile", () => {
  assert.deepEqual(relevancePresentation(null, true), {
    available: false,
    label: "분석 정보 없음",
    value: null,
    width: "0%",
  });
  assert.equal(relevancePresentation(null, false).label, "로그인 후 분석");
  assert.equal(relevancePresentation(64, true).label, "관련도 64%");
});

test("missing structured support points to existing raw notice without inventing extracted benefits", () => {
  const items = buildPolicyBrief(policy({
    supportScale: null,
    originalNoticeText: "선정 기업에 시설 개선비 최대 500만원을 지원합니다.",
  }));
  assert.equal(items[1].available, false);
  assert.equal(items[1].value, "지원 금액·방식은 아직 별도로 정리되지 않았어요. 아래 원문 공고에서 확인해 주세요.");
});

test("numeric relevance keeps its visible percentage", () => {
  assert.deepEqual(relevancePresentation(64), {
    available: true,
    label: "관련도 64%",
    value: 64,
    width: "64%",
  });
});

test("notice keeps the overview and splits source-marked clauses in order", () => {
  assert.deepEqual(
    buildPolicyNotice(policy({
      description: "여성친화기업을 지원합니다. ☞ 경산시 소재 2년 이상 기업 ☞ 편의시설 개선비 최대 500만원",
      targetGroup: "중소기업",
      applicationPeriod: "2026.09.07 ~ 2026.09.14",
      applicationMethod: "방문 또는 이메일 접수",
    })),
    {
      overview: ["여성친화기업을 지원합니다."],
      keyPoints: [
        "경산시 소재 2년 이상 기업",
        "편의시설 개선비 최대 500만원",
      ],
      targetGroup: "중소기업",
      applicationPeriod: "2026.09.07 ~ 2026.09.14",
      applicationMethod: "방문 또는 이메일 접수",
    },
  );
});

test("notice removes blank and duplicate marked clauses without rewriting text", () => {
  const notice = buildPolicyNotice(policy({
    purposeText: "저장된 사업 목적",
    description: "  ☞ 실제 지원 조건 ☞  ☞ 실제 지원 조건 ☞ 원문 그대로의 혜택  ",
  }));

  assert.deepEqual(notice.overview, ["저장된 사업 목적"]);
  assert.deepEqual(notice.keyPoints, ["실제 지원 조건", "원문 그대로의 혜택"]);
});

test("unmarked notice text remains a source overview", () => {
  const notice = buildPolicyNotice(policy({ description: "구분자 없는 원문 설명" }));

  assert.deepEqual(notice.overview, ["구분자 없는 원문 설명"]);
  assert.deepEqual(notice.keyPoints, []);
});
