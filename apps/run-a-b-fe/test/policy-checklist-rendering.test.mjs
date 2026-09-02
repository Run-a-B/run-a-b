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
