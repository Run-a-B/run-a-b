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

test("renders explicit unknowns and stored source quality", async (t) => {
  const vite = await createServer({
    root: process.cwd(),
    appType: "custom",
    logLevel: "silent",
    server: { middlewareMode: true },
    optimizeDeps: { noDiscovery: true },
    ssr: { optimizeDeps: { noDiscovery: true } },
  });
  t.after(() => vite.close());

  const { PolicyBriefing } = await vite.ssrLoadModule(
    "/src/components/policy/PolicyBriefing.tsx",
  );
  const html = await render(
    React.createElement(PolicyBriefing, {
      detail: {
        id: 42,
        title: "테스트 정책",
        targetGroup: "서울 소상공인",
        supportScale: null,
        applicationPeriod: "",
        description: "저장된 정책 설명",
        purposeText: null,
        applicationMethod: null,
        sourceSystem: "bizinfo",
        announcementNo: null,
        department: null,
        date: null,
        applicationUrl: null,
        detailUrl: null,
        category: null,
        region: null,
        industry: null,
        agency: null,
      },
    }),
  );

  assert.match(html, /지원 대상/);
  assert.match(html, /서울 소상공인/);
  assert.match(html, /지원 금액·방식 정보가 아직 수집되지 않았어요/);
  assert.match(html, /기간 정보 없음 — 원문 공고 확인/);
  assert.match(html, /출처/);
  assert.match(html, /기업마당/);
  assert.match(html, /확인 가능한 항목 수/);
  assert.match(html, /2\/7/);
  assert.match(html, /지원 내용, 신청 기간, 신청 방법, 공고 번호, 담당 부서/);
  assert.match(html, /구조화 조건 상태: 미확인/);
  assert.match(html, /원문 공고가 최종 기준입니다/);
});

test("renders diagnosis entry copy and policy-specific route", async (t) => {
  const vite = await createServer({
    root: process.cwd(),
    appType: "custom",
    logLevel: "silent",
    server: { middlewareMode: true },
    optimizeDeps: { noDiscovery: true },
    ssr: { optimizeDeps: { noDiscovery: true } },
  });
  t.after(() => vite.close());

  const { DiagnosisEntryCard } = await vite.ssrLoadModule(
    "/src/components/policy/DiagnosisEntryCard.tsx",
  );
  const html = await render(
    React.createElement(
      MemoryRouter,
      null,
      React.createElement(DiagnosisEntryCard, { policyId: 42 }),
    ),
  );

  assert.match(html, /60초 정책 진단/);
  assert.match(html, /내 사업 정보와 공고 조건을 비교하고, 항목별 판정 근거를 확인해 보세요\./);
  assert.match(html, /내 조건으로 진단하기/);
  assert.match(html, /href="\/policies\/42\/diagnosis"/);
});
