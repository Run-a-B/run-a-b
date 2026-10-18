import assert from "node:assert/strict";
import test from "node:test";
import { businessEditorReducer, initialBusinessEditor, toBusinessUpdateRequest } from "../src/features/business/editor.ts";

const operating = {
  businessStatus: true,
  jobCategory: "카페/음료",
  region: "서울특별시",
  annualRevenue: "1억원~3억원",
  employeeCount: "1~4명",
};

test("cancel restores status and numeric bands before an unrelated edit is saved", () => {
  let editor = businessEditorReducer(initialBusinessEditor, { type: "loaded", business: operating });
  editor = businessEditorReducer(editor, { type: "edit", field: "status", value: "사업 준비 중" });
  editor = businessEditorReducer(editor, { type: "edit", field: "revenue", value: "" });
  editor = businessEditorReducer(editor, { type: "edit", field: "employees", value: "" });
  editor = businessEditorReducer(editor, { type: "edit", field: "region", value: "부산광역시" });
  editor = businessEditorReducer(editor, { type: "cancel" });
  editor = businessEditorReducer(editor, { type: "edit", field: "industry", value: "제조업" });

  assert.deepEqual(toBusinessUpdateRequest(editor.draft), {
    businessStatus: true,
    jobCategory: "제조업",
    region: "서울특별시",
    annualRevenue: "1억원~3억원",
    employeeCount: "1~4명",
  });
});

test("a successful save becomes the snapshot used by the next cancellation", () => {
  let editor = businessEditorReducer(initialBusinessEditor, { type: "loaded", business: operating });
  editor = businessEditorReducer(editor, {
    type: "loaded", business: { ...operating, businessStatus: false, annualRevenue: null, employeeCount: null },
  });
  editor = businessEditorReducer(editor, { type: "edit", field: "status", value: "사업 중" });
  editor = businessEditorReducer(editor, { type: "cancel" });

  assert.deepEqual(toBusinessUpdateRequest(editor.draft), {
    businessStatus: false, jobCategory: "카페/음료", region: "서울특별시", annualRevenue: null, employeeCount: null,
  });
});

test("preparing business never submits hidden revenue or employees", () => {
  let editor = businessEditorReducer(initialBusinessEditor, { type: "loaded", business: operating });
  editor = businessEditorReducer(editor, { type: "edit", field: "status", value: "사업 준비 중" });

  assert.deepEqual(toBusinessUpdateRequest(editor.draft), {
    businessStatus: false, jobCategory: "카페/음료", region: "서울특별시", annualRevenue: null, employeeCount: null,
  });
});

test("a first Google user can set up a missing business profile and cancel back to the blank snapshot", () => {
  let editor = businessEditorReducer(initialBusinessEditor, {
    type: "load-failed",
    error: { response: { status: 404, data: { success: false, error: { code: "BUSINESS_INFO_NOT_FOUND", status: 404 } } } },
  });

  assert.notEqual(editor.saved, null);
  assert.equal(editor.loadFailed, false);
  editor = businessEditorReducer(editor, { type: "edit", field: "industry", value: "카페/음료" });
  editor = businessEditorReducer(editor, { type: "edit", field: "region", value: "서울특별시" });
  assert.deepEqual(toBusinessUpdateRequest(editor.draft), {
    businessStatus: true, jobCategory: "카페/음료", region: "서울특별시", annualRevenue: null, employeeCount: null,
  });
  editor = businessEditorReducer(editor, { type: "cancel" });
  assert.deepEqual(editor.draft, { status: "사업 중", industry: "", region: "", revenue: "", employees: "" });
});

test("generic 404, mismatched business errors and network failures block profile updates", () => {
  for (const error of [
    { response: { status: 404, data: { error: { code: "NOT_FOUND" } } } },
    { response: { status: 500, data: { error: { code: "BUSINESS_INFO_NOT_FOUND" } } } },
    { response: { status: 404, data: "proxy not found" } },
    new Error("offline"),
  ]) {
    const editor = businessEditorReducer(initialBusinessEditor, { type: "load-failed", error });
    assert.equal(editor.saved, null);
    assert.equal(editor.loadFailed, true);
  }
});

test("pending saves freeze business edits until the submitted snapshot settles", () => {
  let editor = businessEditorReducer(initialBusinessEditor, { type: "loaded", business: operating });
  editor = businessEditorReducer(editor, { type: "save-started" });
  editor = businessEditorReducer(editor, { type: "edit", field: "region", value: "부산광역시" });
  editor = businessEditorReducer(editor, { type: "edit", field: "status", value: "사업 준비 중" });

  assert.equal(editor.saving, true);
  assert.deepEqual(toBusinessUpdateRequest(editor.draft), {
    businessStatus: true, jobCategory: "카페/음료", region: "서울특별시", annualRevenue: "1억원~3억원", employeeCount: "1~4명",
  });
  editor = businessEditorReducer(editor, { type: "cancel" });
  assert.equal(editor.saving, true);
  editor = businessEditorReducer(editor, { type: "loaded", business: operating });
  editor = businessEditorReducer(editor, { type: "save-finished" });
  editor = businessEditorReducer(editor, { type: "edit", field: "region", value: "부산광역시" });
  assert.equal(editor.saving, false);
  assert.equal(editor.draft.region, "부산광역시");
  assert.equal(editor.saved?.region, "서울특별시");
});

test("a failed save releases the editor without erasing the user's submitted edits", () => {
  let editor = businessEditorReducer(initialBusinessEditor, { type: "loaded", business: operating });
  editor = businessEditorReducer(editor, { type: "edit", field: "region", value: "부산광역시" });
  editor = businessEditorReducer(editor, { type: "save-started" });
  editor = businessEditorReducer(editor, { type: "save-finished" });

  assert.equal(editor.saving, false);
  assert.equal(editor.draft.region, "부산광역시");
  assert.equal(editor.saved?.region, "서울특별시");
});

test("signup labels reopen as selectable business profile values without losing their meaning", () => {
  for (const [jobCategory, region, industry, province] of [
    ["카페/베이커리", "경기", "카페/음료", "경기도"],
    ["음식점", "서울", "음식점업", "서울특별시"],
    ["교육", "강원도", "교육/학원", "강원특별자치도"],
    ["제조", "전라북도", "제조업", "전북특별자치도"],
    ["도소매", "제주", "도소매업", "제주특별자치도"],
  ]) {
    const editor = businessEditorReducer(initialBusinessEditor, {
      type: "loaded", business: { ...operating, jobCategory, region },
    });
    assert.equal(editor.draft.industry, industry);
    assert.equal(editor.draft.region, province);
    assert.equal(editor.saved?.industry, industry);
    assert.equal(editor.saved?.region, province);
    assert.equal(toBusinessUpdateRequest(editor.draft).jobCategory, industry);
    assert.equal(toBusinessUpdateRequest(editor.draft).region, province);
  }
});
