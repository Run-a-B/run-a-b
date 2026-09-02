import assert from "node:assert/strict";
import test from "node:test";
import React from "react";
import { renderToReadableStream } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { createServer } from "vite";

async function render(component) {
  const stream = await renderToReadableStream(component);
  await stream.allReady;
  return new Response(stream).text();
}

const policy = {
  id: 17,
  category: "경영",
  filterCategory: "경영",
  region: "전국",
  industry: "도소매업",
  isAIRecommended: false,
  title: "소상공인 성장 지원",
  description: "지원 정책 설명",
  relevance: 84,
  agency: "테스트 기관",
  date: "상시",
};

test("grid and list diagnosis actions include the policy title without changing visible copy", async (t) => {
  const vite = await createServer({ root: process.cwd(), appType: "custom", logLevel: "silent" });
  t.after(() => vite.close());
  const { PolicyCard, PolicyListItem } = await vite.ssrLoadModule("/src/pages/Policies.tsx");

  const grid = await render(React.createElement(MemoryRouter, null, React.createElement(PolicyCard, { policy, visited: false, relevance: 84 })));
  const list = await render(React.createElement(MemoryRouter, null, React.createElement(PolicyListItem, { policy, visited: false, relevance: 84 })));

  for (const html of [grid, list]) {
    assert.match(html, /aria-label="소상공인 성장 지원 60초 진단"/);
    assert.match(html, />60초 진단<\/button>/);
    assert.match(html, /min-h-11/);
  }
});
