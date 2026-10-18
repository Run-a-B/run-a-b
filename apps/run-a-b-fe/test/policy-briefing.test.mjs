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
  assert.match(html, /지원 금액·방식은 아직 별도로 정리되지 않았어요/);
  assert.match(html, /아래 원문 공고에서 확인해 주세요/);
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

test("renders the real structured-condition extraction status", async (t) => {
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

  const complete = await render(React.createElement(PolicyBriefing, {
    detail: policyDetail({ extractionStatus: "extracted" }),
  }));
  const partial = await render(React.createElement(PolicyBriefing, {
    detail: policyDetail({ extractionStatus: "partially_extracted" }),
  }));
  const failed = await render(React.createElement(PolicyBriefing, {
    detail: policyDetail({ extractionStatus: "failed" }),
  }));

  assert.match(complete, /구조화 조건 상태: 완료/);
  assert.match(partial, /구조화 조건 상태: 부분 확인/);
  assert.match(failed, /구조화 조건 상태: 미확인/);
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

test("extractive AI summary keeps paragraph breaks and explains the original-notice basis", async (t) => {
  const { PolicySummaryPanel } = await loadPolicyDetailPanels(t);
  const html = await render(React.createElement(PolicySummaryPanel, {
    detail: policyDetail(),
    summary: {
      summaryLines: ["지원 대상: 소상공인\n제외 대상: 휴업 중인 사업자"],
      highlights: [{ icon: "check", label: "지원 대상 원문 발췌", content: "소상공인\n제외 대상: 휴업 중인 사업자" }],
    },
    summaryState: "done",
  }));

  assert.match(html, /class="[^"]*whitespace-pre-line[^"]*">지원 대상: 소상공인\n제외 대상: 휴업 중인 사업자/);
  assert.match(html, /class="[^"]*whitespace-pre-line[^"]*">소상공인\n제외 대상: 휴업 중인 사업자/);
  assert.match(html, /AI가 선택한 원문 발췌예요/);
  assert.match(html, /최종 신청 조건은 원문 공고를 확인해 주세요/);
});

test("summary panel gives guests a login action and configuration errors an honest reason", async (t) => {
  const { PolicySummaryPanel } = await loadPolicyDetailPanels(t);
  const guest = await render(React.createElement(MemoryRouter, null, React.createElement(PolicySummaryPanel, {
    detail: policyDetail(), summary: null, summaryState: "login",
  })));
  assert.match(guest, /로그인 후 AI 요약 보기/);
  assert.match(guest, /href="\/login"/);
  const unconfigured = await render(React.createElement(PolicySummaryPanel, {
    detail: policyDetail(), summary: null, summaryState: "error",
    summaryError: "AI 서비스가 아직 연결되지 않았어요. 원문 공고를 확인해 주세요.",
  }));
  assert.match(unconfigured, /AI 서비스가 아직 연결되지 않았어요/);
  assert.match(unconfigured, /저장된 정책 설명/);
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

test("application method section skips an unsafe primary URL for a safe fallback", async (t) => {
  const { PolicyApplicationMethodSection } = await loadPolicyDetailPanels(t);
  const html = await render(React.createElement(PolicyApplicationMethodSection, {
    detail: policyDetail({
      applicationMethod: null,
      detailUrl: "javascript:alert(1)",
      applicationUrl: "https://example.com/safe-apply",
    }),
    summary: null,
  }));

  assert.match(html, /href="https:\/\/example\.com\/safe-apply"/);
  assert.doesNotMatch(html, /javascript:/);
});

test("application method section keeps the original method beside the labeled AI method", async (t) => {
  const { PolicyApplicationMethodSection } = await loadPolicyDetailPanels(t);
  const html = await render(React.createElement(PolicyApplicationMethodSection, {
    detail: policyDetail({ originalApplicationMethod: "원본 신청 방법\n담당 부서 방문" }),
    summary: { summaryLines: [], highlights: [], expandedApplicationMethod: "정리된 신청 방법" },
  }));

  assert.match(html, /정리된 신청 방법/);
  assert.match(html, /원본 신청 방법\n담당 부서 방문/);
  assert.match(html, /AI가 정리한 신청 방법/);
  assert.doesNotMatch(html, /저장된 신청 방법/);
});

async function loadPolicyNoticeDetails(t) {
  const vite = await createServer({
    root: process.cwd(), appType: "custom", logLevel: "silent",
    server: { middlewareMode: true }, optimizeDeps: { noDiscovery: true },
    ssr: { optimizeDeps: { noDiscovery: true } },
  });
  t.after(() => vite.close());
  return vite.ssrLoadModule("/src/components/policy/PolicyNoticeDetails.tsx");
}

test("official notice preserves stored order, repeated clauses, whitespace and escaped text", async (t) => {
  const { PolicyNoticeDetails } = await loadPolicyNoticeDetails(t);
  const html = await render(React.createElement(PolicyNoticeDetails, {
    detail: policyDetail({
      originalNoticeText: "  첫 문단\n☞ 같은 조건\n☞ 같은 조건\n\n<script>문자 그대로</script>\n마지막 조항  ",
      originalApplicationMethod: "원문 방문 접수\n두 번째 절차",
      originalApplicationPeriod: "예산 소진 시까지",
      applicationPeriod: "2026.01.01 ~ 2026.12.31",
      executingAgency: "실제 수행 기관",
      inquiryContact: "공식 문의 02-1234-5678",
    }),
    summary: {
      expandedDescription: "확장된 사업 설명",
      expandedApplicationMethod: "AI가 정리한 접수 절차",
    },
  }));

  assert.ok(html.includes("  첫 문단\n☞ 같은 조건\n☞ 같은 조건\n\n&lt;script&gt;문자 그대로&lt;/script&gt;\n마지막 조항  "));
  assert.match(html, /whitespace-pre-wrap/);
  assert.doesNotMatch(html, /<script>/);
  assert.doesNotMatch(html, /저장된 정책 설명|저장된 사업 목적|사업 개요|공고문 핵심 항목/);
  assert.match(html, /원문 방문 접수\n두 번째 절차/);
  assert.match(html, /AI가 정리한 신청 방법/);
  assert.match(html, /AI가 정리한 접수 절차/);
  assert.match(html, /AI가 정리한 사업 내용/);
  assert.match(html, /확장된 사업 설명/);
  assert.match(html, /예산 소진 시까지/);
  assert.doesNotMatch(html, /2026\.01\.01/);
  assert.match(html, /수행 기관/);
  assert.match(html, /실제 수행 기관/);
  assert.match(html, /공식 문의 02-1234-5678/);
});

test("official files keep role order and only safe HTTP links become anchors", async (t) => {
  const { PolicyNoticeDetails } = await loadPolicyNoticeDetails(t);
  const html = await render(React.createElement(PolicyNoticeDetails, {
    detail: policyDetail({
      detailUrl: "javascript:alert(1)", applicationUrl: "data:text/html,unsafe",
      sourceAttachments: [
        { name: "신청서.hwp", url: "https://example.com/apply.hwp", role: "attachment" },
        { name: "위험한 공고문.pdf", url: "javascript:alert(2)", role: "notice" },
        { name: null, url: "https://example.com/" + "long".repeat(100), role: "notice" },
        { name: "이름만 있는 파일", url: null, role: "attachment" },
      ],
    }),
    summary: null,
  }));

  assert.match(html, /첨부파일/);
  assert.match(html, /공고문 파일/);
  assert.ok(html.indexOf("신청서.hwp") < html.indexOf("위험한 공고문.pdf"));
  assert.match(html, /href="https:\/\/example\.com\/apply\.hwp"/);
  assert.match(html, /target="_blank" rel="noopener noreferrer"/);
  assert.match(html, /위험한 공고문.pdf/);
  assert.match(html, /이름만 있는 파일/);
  assert.match(html, /\[overflow-wrap:anywhere\]/);
  assert.doesNotMatch(html, /href="(?:javascript:|data:)/);
});

test("blank or missing original notice retains the legacy presentation and period fallback", async (t) => {
  const { PolicyNoticeDetails } = await loadPolicyNoticeDetails(t);
  for (const originalNoticeText of [undefined, null, " \n\t"]) {
    const html = await render(React.createElement(PolicyNoticeDetails, {
      detail: policyDetail({ originalNoticeText, originalApplicationPeriod: " ", applicationPeriod: "기존 신청 기간" }),
      summary: { expandedApplicationMethod: "AI 접수 안내" },
    }));
    assert.match(html, /사업 개요/);
    assert.match(html, /저장된 사업 목적/);
    assert.match(html, /저장된 정책 설명/);
    assert.match(html, /기존 신청 기간/);
    assert.match(html, /저장된 신청 방법/);
    assert.match(html, /AI 접수 안내/);
    assert.match(html, /AI가 정리한 신청 방법/);
  }
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

test("detailed notice renders every stored source clause and application fact", async (t) => {
  const vite = await createServer({
    root: process.cwd(),
    appType: "custom",
    logLevel: "silent",
    server: { middlewareMode: true },
    optimizeDeps: { noDiscovery: true },
    ssr: { optimizeDeps: { noDiscovery: true } },
  });
  t.after(() => vite.close());
  const { PolicyNoticeDetails } = await vite.ssrLoadModule(
    "/src/components/policy/PolicyNoticeDetails.tsx",
  );

  const html = await render(React.createElement(PolicyNoticeDetails, {
    detail: policyDetail({
      description: "여성친화기업을 지원합니다. ☞ 경산시 소재 2년 이상 기업 ☞ 편의시설 개선비 최대 500만원",
      purposeText: null,
      targetGroup: "중소기업",
      applicationPeriod: "2026.09.07 ~ 2026.09.14",
      applicationMethod: "방문 또는 이메일 접수",
    }),
    summary: null,
  }));

  assert.match(html, /상세 공고 내용/);
  assert.match(html, /사업 개요/);
  assert.match(html, /여성친화기업을 지원합니다\./);
  assert.match(html, /공고문 핵심 항목/);
  assert.match(html, /경산시 소재 2년 이상 기업/);
  assert.match(html, /편의시설 개선비 최대 500만원/);
  assert.match(html, /중소기업/);
  assert.match(html, /2026\.09\.07 ~ 2026\.09\.14/);
  assert.match(html, /방문 또는 이메일 접수/);
  assert.match(html, /공고문 원문이 최종 기준입니다\./);
  assert.match(html, /href="https:\/\/example\.com\/original"/);
});
