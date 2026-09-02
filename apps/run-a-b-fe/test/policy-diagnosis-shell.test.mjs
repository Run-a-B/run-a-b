import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToReadableStream } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { createServer } from "vite";

async function render(component) {
  const stream = await renderToReadableStream(component);
  await stream.allReady;
  return new Response(stream).text();
}

function createLocalStorageStub() {
  const values = new Map();
  return {
    get length() { return values.size; },
    clear() { values.clear(); },
    getItem(key) { return values.get(String(key)) ?? null; },
    key(index) { return [...values.keys()][index] ?? null; },
    removeItem(key) { values.delete(String(key)); },
    setItem(key, value) { values.set(String(key), String(value)); },
  };
}

test("renders the diagnosis loading shell at the diagnosis route", async (t) => {
  const previousStorage = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
  const vite = await createServer({
    root: process.cwd(),
    appType: "custom",
    logLevel: "silent",
    server: { middlewareMode: true },
    optimizeDeps: { noDiscovery: true },
    ssr: { optimizeDeps: { noDiscovery: true } },
  });

  t.after(async () => {
    await vite.close();
    if (previousStorage) Object.defineProperty(globalThis, "localStorage", previousStorage);
    else delete globalThis.localStorage;
  });

  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    writable: true,
    value: createLocalStorageStub(),
  });

  const [{ AppRoutes }, { AuthProvider }] = await Promise.all([
    vite.ssrLoadModule("/src/routes/AppRoutes.tsx"),
    vite.ssrLoadModule("/src/contexts/AuthContext.tsx"),
  ]);
  const tree = React.createElement(
    MemoryRouter,
    { initialEntries: ["/policies/42/diagnosis"] },
    React.createElement(AuthProvider, null, React.createElement(AppRoutes)),
  );
  const stream = await renderToReadableStream(tree);
  await stream.allReady;
  const html = await new Response(stream).text();

  assert.match(html, /60초 정책 진단/);
  assert.match(html, /정책 정보를 불러오고 있어요/);
});

function diagnosisResult(policy) {
  return {
    policy,
    verdict: "NEEDS_CONFIRMATION",
    verdictLabel: "조건 확인 필요",
    score: null,
    confidence: "LOW",
    summary: "원문 공고를 더 확인해 주세요.",
    evidence: [],
    unknownConditions: [],
    hardFailReasons: [],
    hardFail: false,
    dataQuality: {
      policyCardAvailable: false,
      extractionStatus: null,
      sourceSystem: "bizinfo",
      verifiedFactCount: 1,
      totalFactCount: 6,
    },
    disclaimer: "원문 공고와 담당 기관에서 확인해 주세요.",
  };
}

test("renders a safe original link for a retryable diagnosis error", async (t) => {
  const vite = await createServer({ root: process.cwd(), appType: "custom", logLevel: "silent" });
  t.after(() => vite.close());
  const { OriginalNoticeLink } = await vite.ssrLoadModule("/src/pages/PolicyDiagnosis.tsx");

  const safeHtml = await render(React.createElement(OriginalNoticeLink, { detailUrl: "https://example.com/notice" }));
  const unsafeHtml = await render(React.createElement(OriginalNoticeLink, { detailUrl: "javascript:alert(1)" }));

  assert.match(safeHtml, /원문 공고 보기/);
  assert.match(safeHtml, /href="https:\/\/example\.com\/notice"/);
  assert.match(safeHtml, /rel="noopener noreferrer"/);
  assert.doesNotMatch(unsafeHtml, /원문 공고 보기/);
});

test("renders exact result metadata and explicit missing metadata fallbacks", async (t) => {
  const vite = await createServer({ root: process.cwd(), appType: "custom", logLevel: "silent" });
  t.after(() => vite.close());
  const { DiagnosisResult } = await vite.ssrLoadModule("/src/pages/PolicyDiagnosis.tsx");

  const populated = await render(React.createElement(
    MemoryRouter,
    null,
    React.createElement(DiagnosisResult, { policyId: 42, result: diagnosisResult({ id: 42, title: "테스트 정책", category: "경영", agency: "테스트 기관", applicationPeriod: "2026.09.01 ~ 2026.09.30", detailUrl: null }) }),
  ));
  const missing = await render(React.createElement(
    MemoryRouter,
    null,
    React.createElement(DiagnosisResult, { policyId: 42, result: diagnosisResult({ id: 42, title: "테스트 정책", category: " ", agency: null, applicationPeriod: "", detailUrl: null }) }),
  ));

  assert.match(populated, /경영/);
  assert.match(populated, /테스트 기관/);
  assert.match(populated, /2026\.09\.01 ~ 2026\.09\.30/);
  assert.match(missing, /정보 없음/);
  assert.match(missing, /기간 정보 없음 — 원문 공고 확인/);
});

test("only treats policy detail as ready when it belongs to the current route", async (t) => {
  const vite = await createServer({ root: process.cwd(), appType: "custom", logLevel: "silent" });
  t.after(() => vite.close());
  const { isLoadedPolicyForRoute } = await vite.ssrLoadModule("/src/features/diagnosis/policyRoute.ts");

  const loadedPolicy = { policyId: 1, detail: { id: 1, title: "첫 번째 정책" } };
  assert.equal(isLoadedPolicyForRoute(loadedPolicy, 1), true);
  assert.equal(isLoadedPolicyForRoute(loadedPolicy, 2), false);
  assert.equal(isLoadedPolicyForRoute(null, 2), false);
});
