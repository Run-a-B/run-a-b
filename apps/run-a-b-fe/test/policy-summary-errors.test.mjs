import assert from "node:assert/strict";
import test from "node:test";
import { summaryFailureMessage } from "../src/features/policy/presentation.ts";

test("AI quota and configuration failures explain that administrator action is required", () => {
  assert.equal(summaryFailureMessage("AI_QUOTA_EXHAUSTED", 503),
    "AI 서비스 이용 한도에 도달했어요. 관리자에게 문의해 주세요. 원문 공고는 계속 확인할 수 있어요.");
  assert.equal(summaryFailureMessage("AI_CONFIGURATION_ERROR", 503),
    "AI 서비스 설정을 확인해야 해요. 관리자에게 문의해 주세요. 원문 공고는 계속 확인할 수 있어요.");
});

test("temporary throttling asks users to retry later without suggesting account problems", () => {
  assert.equal(summaryFailureMessage("AI_RATE_LIMITED", 429),
    "요약 요청이 많아요. 잠시 후 다시 방문해 주세요.");
});

test("unconfigured and unknown AI failures preserve useful original-notice guidance", () => {
  assert.equal(summaryFailureMessage("AI_NOT_CONFIGURED", 503),
    "AI 서비스가 아직 연결되지 않았어요. 원문 공고를 확인해 주세요.");
  assert.equal(summaryFailureMessage(undefined, undefined),
    "AI 요약을 잠시 이용할 수 없어요. 원문 공고를 확인해 주세요.");
});
