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

test("grid and list cards expose policy links and distinct named diagnosis actions", async (t) => {
  const vite = await createServer({ root: process.cwd(), appType: "custom", logLevel: "silent" });
  t.after(() => vite.close());
  const { PolicyCard, PolicyListItem } = await vite.ssrLoadModule("/src/pages/Policies.tsx");

  const grid = await render(React.createElement(MemoryRouter, null, React.createElement(PolicyCard, { policy, visited: false, relevance: 84 })));
  const list = await render(React.createElement(MemoryRouter, null, React.createElement(PolicyListItem, { policy, visited: false, relevance: 84 })));

  for (const html of [grid, list]) {
    assert.match(html, /<a\b[^>]*href="\/policies\/17"[^>]*>소상공인 성장 지원<\/a>/);
    assert.match(html, /aria-label="소상공인 성장 지원 60초 진단"/);
    assert.match(html, />60초 진단<\/button>/);
    assert.match(html, /min-h-11/);
  }
});

test("grid and list missing-score labels follow the user's login state", async (t) => {
  const vite = await createServer({ root: process.cwd(), appType: "custom", logLevel: "silent" });
  t.after(() => vite.close());
  const { PolicyCard, PolicyListItem } = await vite.ssrLoadModule("/src/pages/Policies.tsx");

  for (const Component of [PolicyCard, PolicyListItem]) {
    const signedIn = await render(React.createElement(MemoryRouter, null,
      React.createElement(Component, { policy, visited: false, relevance: null, authenticated: true })));
    const signedOut = await render(React.createElement(MemoryRouter, null,
      React.createElement(Component, { policy, visited: false, relevance: null, authenticated: false })));
    assert.match(signedIn, /분석 정보 없음/);
    assert.doesNotMatch(signedIn, /로그인 후 분석|사업 정보 등록/);
    assert.match(signedOut, /로그인 후 분석/);
  }
});
