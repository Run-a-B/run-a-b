import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToReadableStream } from "react-dom/server";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { createServer } from "vite";

async function render(component) {
  const stream = await renderToReadableStream(component);
  await stream.allReady;
  return new Response(stream).text();
}

async function modules(t) {
  const vite = await createServer({ root: process.cwd(), appType: "custom", logLevel: "silent",
    server: { middlewareMode: true }, optimizeDeps: { noDiscovery: true }, ssr: { optimizeDeps: { noDiscovery: true } } });
  t.after(() => vite.close());
  return vite;
}

test("report route shows visible loading instead of a blank page", async (t) => {
  const vite = await modules(t);
  const { default: ReportDetail } = await vite.ssrLoadModule("/src/pages/ReportDetail.tsx");
  const html = await render(React.createElement(MemoryRouter, { initialEntries: ["/reports/42"] },
    React.createElement(Routes, null,
      React.createElement(Route, { path: "/reports/:id", element: React.createElement(ReportDetail) }))));
  assert.match(html, /리포트를 불러오고 있어요/);
  assert.match(html, /role="status"/);
  assert.doesNotMatch(html, /리포트를 찾을 수 없어요/);
});

test("report reader and loader distinguish actual 404 from transport and server failures, then retry", async (t) => {
  const vite = await modules(t);
  const [{ loadReportDetail }, { getSavedReport }, { default: api }] = await Promise.all([
    vite.ssrLoadModule("/src/features/reports/detailLoader.ts"),
    vite.ssrLoadModule("/src/data/reports.ts"),
    vite.ssrLoadModule("/src/lib/api.ts"),
  ]);
  const states = [];
  const update = state => states.push(state);
  api.get = async () => { throw { isAxiosError: true, response: { status: 404, data: { error: { code: "REPORT_NOT_FOUND" } } } }; };
  await loadReportDetail(getSavedReport, 42, () => true, update);
  assert.equal(states.at(-1).tag, "missing");
  for (const status of [500, undefined]) {
    api.get = async () => { throw { isAxiosError: true, response: status ? { status } : undefined }; };
    await loadReportDetail(getSavedReport, 42, () => true, update);
    assert.equal(states.at(-1).tag, "error");
  }
  api.get = async () => ({ data: { data: {
    policyId: 42, policyTitle: "저장된 리포트", savedAt: "2026-10-18T00:00:00", summary: "저장된 분석",
    impactLabel: "조건 확인", impactStyle: "", details: [], relatedIds: [],
  } } });
  await loadReportDetail(getSavedReport, 42, () => true, update);
  assert.equal(states.at(-2).tag, "loading");
  assert.equal(states.at(-1).tag, "ready");
  assert.equal(states.at(-1).report.policyTitle, "저장된 리포트");
});

test("failed report view offers retry while missing report keeps an accurate missing state", async (t) => {
  const vite = await modules(t);
  const { ReportLoadNotice } = await vite.ssrLoadModule("/src/pages/ReportDetail.tsx");
  const failed = await render(React.createElement(ReportLoadNotice, { state: "error", onRetry() {}, onBack() {} }));
  assert.match(failed, /role="alert"/);
  assert.match(failed, /리포트를 불러오지 못했어요/);
  assert.match(failed, /다시 시도/);
  assert.doesNotMatch(failed, /리포트를 찾을 수 없어요/);
  const missing = await render(React.createElement(ReportLoadNotice, { state: "missing", onRetry() {}, onBack() {} }));
  assert.match(missing, /리포트를 찾을 수 없어요/);
  assert.doesNotMatch(missing, /다시 시도/);
});

test("report suggestions and homepage explain category selection and separate eligibility checks", async (t) => {
  const vite = await modules(t);
  const { RelatedPolicies } = await vite.ssrLoadModule("/src/pages/ReportDetail.tsx");
  const related = await render(React.createElement(RelatedPolicies, {
    policies: [{ id: 7, title: "같은 분야의 공고 제목" }], onOpen() {},
  }));
  assert.match(related, /같은 카테고리에서 최신순/);
  assert.match(related, /신청 자격과 접수 기간은 각 공고에서 따로 확인/);
  assert.match(related, /같은 분야의 공고 제목/);
  assert.doesNotMatch(related, /함께 신청하면 좋아요/);
  const { HomePage } = await vite.ssrLoadModule("/src/pages/HomePage.tsx");
  const { AuthProvider } = await vite.ssrLoadModule("/src/contexts/AuthContext.tsx");
  const previousStorage = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
  Object.defineProperty(globalThis, "localStorage", { configurable: true, value: { getItem: () => null } });
  t.after(() => {
    if (previousStorage) Object.defineProperty(globalThis, "localStorage", previousStorage);
    else delete globalThis.localStorage;
  });
  const home = await render(React.createElement(MemoryRouter, null,
    React.createElement(AuthProvider, null, React.createElement(HomePage))));
  assert.match(home, /같은 카테고리의 다른 최신 공고/);
  assert.match(home, /신청 자격과 접수 기간/);
  assert.doesNotMatch(home, /정책 영향 분석 결과에 기반한 지원사업을 추천/);
});
