import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToReadableStream } from "react-dom/server";
import { createServer } from "vite";
import { retryChecklistLoad } from "../src/features/policy/checklist.ts";

async function render(component) {
  const stream = await renderToReadableStream(component);
  await stream.allReady;
  return new Response(stream).text();
}

test("retry transitions synchronously to loading and checklist rows expose pressed state", async (t) => {
  const vite = await createServer({ root: process.cwd(), appType: "custom", logLevel: "silent" });
  t.after(() => vite.close());
  const { ChecklistRow } = await vite.ssrLoadModule("/src/pages/PolicyChecklist.tsx");

  assert.deepEqual(retryChecklistLoad(2), { tag: "loading", policyId: 2 });

  const item = { id: "biz_reg", label: "사업자등록증", required: false };
  const checked = await render(React.createElement(ChecklistRow, { item, checked: true, onToggle: () => {} }));
  const unchecked = await render(React.createElement(ChecklistRow, { item, checked: false, onToggle: () => {} }));

  assert.match(checked, /aria-pressed="true"/);
  assert.match(unchecked, /aria-pressed="false"/);
});

test("checklist loading and failure states are announced with the appropriate urgency", async (t) => {
  const vite = await createServer({ root: process.cwd(), appType: "custom", logLevel: "silent" });
  t.after(() => vite.close());
  const { ChecklistLoadNotice } = await vite.ssrLoadModule("/src/pages/PolicyChecklist.tsx");

  const loading = await render(React.createElement(ChecklistLoadNotice, { failed: false, onBack: () => {}, onRetry: () => {} }));
  const failure = await render(React.createElement(ChecklistLoadNotice, { failed: true, onBack: () => {}, onRetry: () => {} }));

  assert.match(loading, /role="status"/);
  assert.match(loading, /aria-live="polite"/);
  assert.match(loading, /aria-busy="true"/);
  assert.match(failure, /role="alert"/);
  assert.doesNotMatch(failure, /aria-busy="true"/);
  assert.match(failure, /text-gray-600/);
  assert.match(failure, /다시 시도/);
  assert.equal((failure.match(/min-h-11/g) ?? []).length, 2);
});

test("important checklist descriptions do not use low-contrast gray text", async (t) => {
  const vite = await createServer({ root: process.cwd(), appType: "custom", logLevel: "silent" });
  t.after(() => vite.close());
  const { ChecklistRow } = await vite.ssrLoadModule("/src/pages/PolicyChecklist.tsx");

  const html = await render(React.createElement(ChecklistRow, {
    item: { id: "biz_reg", label: "사업자등록증", required: false, description: "원문 발급 기준을 확인해 주세요." },
    checked: false,
    onToggle: () => {},
  }));

  assert.match(html, /text-gray-600[^>]*>원문 발급 기준을 확인해 주세요/);
  assert.doesNotMatch(html, /text-gray-400[^>]*>원문 발급 기준을 확인해 주세요/);
});
