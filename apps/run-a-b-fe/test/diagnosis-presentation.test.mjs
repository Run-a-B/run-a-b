import assert from "node:assert/strict";
import test from "node:test";

async function loadPresentation() {
  return import("../src/features/diagnosis/presentation.ts").catch(() => ({}));
}

test("diagnosis data-quality tokens have Korean presentation labels", async () => {
  const presentation = await loadPresentation();

  assert.equal(presentation.diagnosisSourceLabel?.("bizinfo"), "기업마당");
  assert.equal(presentation.diagnosisSourceLabel?.(null), "출처 정보 없음");
  assert.equal(presentation.diagnosisExtractionLabel?.("extracted"), "추출 완료");
  assert.equal(presentation.diagnosisExtractionLabel?.("partially_extracted"), "일부 추출");
  assert.equal(presentation.diagnosisExtractionLabel?.("failed"), "추출 실패");
  assert.equal(presentation.diagnosisExtractionLabel?.("not_available"), "추출 정보 없음");
  assert.equal(presentation.diagnosisExtractionLabel?.("unexpected_token"), "상태 확인 필요");
});

test("diagnosis announcements describe each changing flow state", async () => {
  const presentation = await loadPresentation();

  assert.equal(presentation.diagnosisAnnouncement?.({ tag: "form" }), "진단 입력 양식입니다. 필수 항목을 입력해 주세요.");
  assert.equal(presentation.diagnosisAnnouncement?.({ tag: "analyzing", step: 1 }), "공고문 조건 대조 진행 중입니다.");
  assert.equal(presentation.diagnosisAnnouncement?.({ tag: "result", verdictLabel: "조건 확인 필요" }), "진단 결과가 나왔습니다. 조건 확인 필요");
  assert.equal(presentation.diagnosisAnnouncement?.({ tag: "error", message: "잠시 후 다시 시도해 주세요." }), "진단에 실패했습니다. 잠시 후 다시 시도해 주세요.");
});

test("diagnosis flow states map to one deterministic focus target", async () => {
  const presentation = await loadPresentation();

  assert.equal(presentation.diagnosisFocusTarget?.("idle"), "form");
  assert.equal(presentation.diagnosisFocusTarget?.("analyzing"), "analyzing");
  assert.equal(presentation.diagnosisFocusTarget?.("result"), "result");
  assert.equal(presentation.diagnosisFocusTarget?.("error"), "error");
});
