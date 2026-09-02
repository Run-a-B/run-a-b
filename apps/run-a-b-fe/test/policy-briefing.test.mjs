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

function policyDetail(overrides = {}) {
  return {
    id: 42,
    title: "테스트 정책",
    description: "저장된 정책 설명",
    date: null,
    announcementNo: null,
    department: null,
    applicationPeriod: null,
    supportScale: null,
    targetGroup: null,
    purposeText: "저장된 사업 목적",
    applicationMethod: "저장된 신청 방법",
    applicationUrl: "https://example.com/apply",
    detailUrl: "https://example.com/original",
    category: null,
    region: null,
    industry: null,
    agency: null,
    sourceSystem: "bizinfo",
    ...overrides,
  };
}

async function loadPolicyDetailPanels(t) {
  const vite = await createServer({
    root: process.cwd(),
    appType: "custom",
    logLevel: "silent",
    server: { middlewareMode: true },
    optimizeDeps: { noDiscovery: true },
    ssr: { optimizeDeps: { noDiscovery: true } },
  });
  t.after(() => vite.close());
  return vite.ssrLoadModule("/src/pages/PolicyDetail.tsx");
}

test("summary panel uses stored facts when summary requests fail", async (t) => {
  const { PolicySummaryPanel } = await loadPolicyDetailPanels(t);
  const html = await render(React.createElement(PolicySummaryPanel, {
    detail: policyDetail(),
    summary: null,
    summaryState: "error",
  }));

  assert.match(html, /자동 요약을 불러오지 못해 수집된 공고 정보를 보여드려요\./);
  assert.match(html, /저장된 사업 목적/);
  assert.match(html, /저장된 정책 설명/);
  assert.doesNotMatch(html, />완료</);
});

test("summary panel treats a done empty response as unavailable", async (t) => {
  const { PolicySummaryPanel } = await loadPolicyDetailPanels(t);
  const html = await render(React.createElement(PolicySummaryPanel, {
    detail: policyDetail(),
    summary: { summaryLines: [], highlights: [] },
    summaryState: "done",
  }));

  assert.match(html, /자동 요약을 불러오지 못해 수집된 공고 정보를 보여드려요\./);
  assert.doesNotMatch(html, />완료</);
});

test("summary panel ignores blank highlight shells", async (t) => {
  const { PolicySummaryPanel } = await loadPolicyDetailPanels(t);
  const html = await render(React.createElement(PolicySummaryPanel, {
    detail: policyDetail(),
    summary: {
      summaryLines: ["  "],
      highlights: [{ icon: "check", label: " ", content: "\t" }],
    },
    summaryState: "done",
  }));

  assert.match(html, /자동 요약을 불러오지 못해 수집된 공고 정보를 보여드려요\./);
  assert.doesNotMatch(html, />완료</);
});

test("summary panel marks visible lines as complete", async (t) => {
  const { PolicySummaryPanel } = await loadPolicyDetailPanels(t);
  const html = await render(React.createElement(PolicySummaryPanel, {
    detail: policyDetail(),
    summary: { summaryLines: ["지원 대상 확인"], highlights: [] },
    summaryState: "done",
  }));

  assert.match(html, /지원 대상 확인/);
  assert.match(html, />완료</);
  assert.doesNotMatch(html, /자동 요약을 불러오지 못해 수집된 공고 정보를 보여드려요\./);
});

test("summary panel marks a visible highlight as complete", async (t) => {
  const { PolicySummaryPanel } = await loadPolicyDetailPanels(t);
  const html = await render(React.createElement(PolicySummaryPanel, {
    detail: policyDetail(),
    summary: {
      summaryLines: ["   "],
      highlights: [{ icon: "check", label: "지원 조건", content: "대상을 확인하세요." }],
    },
    summaryState: "done",
  }));

  assert.match(html, /지원 조건/);
  assert.match(html, />완료</);
  assert.doesNotMatch(html, /자동 요약을 불러오지 못해 수집된 공고 정보를 보여드려요\./);
});

test("summary panel completes for expanded description without false unavailable copy", async (t) => {
  const { PolicySummaryPanel } = await loadPolicyDetailPanels(t);
  const html = await render(React.createElement(PolicySummaryPanel, {
    detail: policyDetail(),
    summary: { summaryLines: ["  "], highlights: [], expandedDescription: "정리된 사업 내용" },
    summaryState: "done",
  }));

  assert.match(html, />완료</);
  assert.match(html, /자동 정리된 상세 정보를 본문에 반영했어요\./);
  assert.doesNotMatch(html, /자동 요약을 불러오지 못해 수집된 공고 정보를 보여드려요\./);
});

test("summary panel completes for expanded application method without false unavailable copy", async (t) => {
  const { PolicySummaryPanel } = await loadPolicyDetailPanels(t);
  const html = await render(React.createElement(PolicySummaryPanel, {
    detail: policyDetail(),
    summary: { summaryLines: [], highlights: [], expandedApplicationMethod: "정리된 신청 방법" },
    summaryState: "done",
  }));

  assert.match(html, />완료</);
  assert.match(html, /자동 정리된 상세 정보를 본문에 반영했어요\./);
  assert.doesNotMatch(html, /자동 요약을 불러오지 못해 수집된 공고 정보를 보여드려요\./);
});

test("application method section links to the original notice when no method is stored", async (t) => {
  const { PolicyApplicationMethodSection } = await loadPolicyDetailPanels(t);
  const html = await render(React.createElement(PolicyApplicationMethodSection, {
    detail: policyDetail({ applicationMethod: null }),
    summary: null,
  }));

  assert.match(html, /신청 방법 정보가 아직 수집되지 않았어요\./);
  assert.match(html, /href="https:\/\/example\.com\/original"/);
  assert.match(html, /target="_blank"/);
  assert.match(html, /rel="noopener noreferrer"/);
});

test("application method section prefers expanded method over stored method", async (t) => {
  const { PolicyApplicationMethodSection } = await loadPolicyDetailPanels(t);
  const html = await render(React.createElement(PolicyApplicationMethodSection, {
    detail: policyDetail(),
    summary: { summaryLines: [], highlights: [], expandedApplicationMethod: "정리된 신청 방법" },
  }));

  assert.match(html, /정리된 신청 방법/);
  assert.doesNotMatch(html, /저장된 신청 방법/);
});

test("application method section uses the stored method when no expanded method exists", async (t) => {
  const { PolicyApplicationMethodSection } = await loadPolicyDetailPanels(t);
  const html = await render(React.createElement(PolicyApplicationMethodSection, {
    detail: policyDetail({ applicationMethod: "저장된 신청 방법" }),
    summary: { summaryLines: [], highlights: [] },
  }));

  assert.match(html, /저장된 신청 방법/);
  assert.doesNotMatch(html, /신청 방법 정보가 아직 수집되지 않았어요\./);
});
