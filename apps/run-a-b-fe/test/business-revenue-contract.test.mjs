import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "vite";

const CANONICAL_REVENUE_OPTIONS = [
  "1,000만원 미만",
  "1,000만원~5,000만원",
  "5,000만원~1억원",
  "1억원~3억원",
  "3억원~5억원",
  "5억원 이상",
];

async function loadRevenueContract(t) {
  const vite = await createServer({
    root: process.cwd(),
    appType: "custom",
    logLevel: "silent",
    server: { middlewareMode: true },
    optimizeDeps: { noDiscovery: true },
    ssr: { optimizeDeps: { noDiscovery: true } },
  });
  t.after(() => vite.close());
  return Promise.all([
    vite.ssrLoadModule("/src/pages/Signup.tsx"),
    vite.ssrLoadModule("/src/pages/mypage/MyBusinessPage.tsx"),
    vite.ssrLoadModule("/src/features/diagnosis/model.ts"),
    vite.ssrLoadModule("/src/features/business/signup.ts"),
    vite.ssrLoadModule("/src/features/business/options.ts"),
  ]);
}

test("Signup, My Page, and diagnosis expose one canonical revenue contract", async (t) => {
  const [signup, myBusiness, diagnosis, , businessOptions] = await loadRevenueContract(t);

  assert.deepEqual(businessOptions.REVENUE_OPTIONS, CANONICAL_REVENUE_OPTIONS);
  assert.strictEqual(signup.SIGNUP_REVENUE_OPTIONS, businessOptions.REVENUE_OPTIONS);
  assert.strictEqual(myBusiness.MY_BUSINESS_REVENUE_OPTIONS, businessOptions.REVENUE_OPTIONS);
  assert.strictEqual(diagnosis.REVENUE_OPTIONS, businessOptions.REVENUE_OPTIONS);
});

test("Signup omits operating-only values for a preparing business", async (t) => {
  const [, , , signup] = await loadRevenueContract(t);

  assert.deepEqual(
    signup.toSignupBusinessRequest({
      status: "사업 준비 중",
      industry: "카페/베이커리",
      region: "서울",
      revenue: "1억원~3억원",
      employees: "1~4명",
    }),
    {
      businessStatus: false,
      jobCategory: "카페/베이커리",
      region: "서울",
      annualRevenue: null,
      employeeCount: null,
    },
  );
});

test("Signup preserves a canonical revenue selection for an operating business", async (t) => {
  const [, , , signup] = await loadRevenueContract(t);

  for (const annualRevenue of CANONICAL_REVENUE_OPTIONS) {
    assert.equal(
      signup.toSignupBusinessRequest({
        status: "사업 중",
        industry: "카페/베이커리",
        region: "서울",
        revenue: annualRevenue,
        employees: "1~4명",
      }).annualRevenue,
      annualRevenue,
    );
  }
});

test("Signup requires revenue and employees only for an operating business", async (t) => {
  const [, , , signup] = await loadRevenueContract(t);

  const base = {
    industry: "카페/베이커리",
    region: "서울",
    revenue: "",
    employees: "",
  };
  assert.equal(signup.isSignupBusinessValid({ ...base, status: "사업 중" }), false);
  assert.equal(
    signup.isSignupBusinessValid({
      ...base,
      status: "사업 중",
      revenue: "1억원~3억원",
    }),
    false,
  );
  assert.equal(
    signup.isSignupBusinessValid({
      ...base,
      status: "사업 중",
      employees: "1~4명",
    }),
    false,
  );
  assert.equal(
    signup.isSignupBusinessValid({
      ...base,
      status: "사업 중",
      revenue: "1억원~3억원",
      employees: "1~4명",
    }),
    true,
  );
  assert.equal(signup.isSignupBusinessValid({ ...base, status: "사업 준비 중" }), true);
});
