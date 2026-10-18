import assert from "node:assert/strict";
import test from "node:test";
import {
  buildChecklistProgress,
  checkedForPolicy,
  loadPolicyChecklist,
  safeExternalUrl,
} from "../src/features/policy/checklist.ts";

const policyFixture = {
  id: 1,
  title: "실제 정책",
  category: "경영",
  applicationUrl: "https://policy.example/apply",
  detailUrl: "https://policy.example/original",
};

const checklistFixture = {
  applicationPeriod: "기간 정보 없음 — 원문 공고 확인",
  department: "테스트 기관",
  applicationUrl: "https://checklist.example/apply",
  applicationChecklist: [{ id: "recommendation", label: "사업자등록증", required: false }],
};

test("policy id 1 still loads live policy and checklist APIs", async () => {
  const calls: string[] = [];
  const client = {
    async get(url: string) {
      calls.push(url);
      return url.endsWith("/checklist")
        ? { data: { data: checklistFixture } }
        : { data: { data: policyFixture } };
    },
  };

  const result = await loadPolicyChecklist(client, 1);

  assert.deepEqual(calls, ["/api/v1/policies/1", "/api/v1/policies/1/checklist"]);
  assert.equal(result.sourceUrl, "https://checklist.example/apply");
});

test("prefers the checklist application URL and only falls back to safe policy URLs", async () => {
  const client = {
    async get(url: string) {
      if (url.endsWith("/checklist")) {
        return { data: { data: { ...checklistFixture, applicationUrl: "javascript:alert(1)" } } };
      }
      return { data: { data: policyFixture } };
    },
  };

  const result = await loadPolicyChecklist(client, 7);

  assert.equal(result.sourceUrl, "https://policy.example/apply");
  assert.equal(safeExternalUrl("javascript:alert(1)"), null);
  assert.equal(safeExternalUrl("https://safe.example/notice"), "https://safe.example/notice");
});

test("zero required documents are recommendations rather than completed requirements", () => {
  const progress = buildChecklistProgress(checklistFixture.applicationChecklist);

  assert.deepEqual(progress, {
    kind: "recommendations",
    groupTitle: "일반 준비 권장",
    notice: "필수 여부는 원문 공고에서 확인해 주세요",
    completed: 0,
    total: 1,
  });
});

test("checks are isolated by policy even when generic document IDs match", () => {
  const checkedByPolicy = { 1: { biz_reg: true } };

  assert.deepEqual(checkedForPolicy(checkedByPolicy, 1), { biz_reg: true });
  assert.deepEqual(checkedForPolicy(checkedByPolicy, 2), {});
});
