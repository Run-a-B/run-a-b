import assert from "node:assert/strict";
import test from "node:test";
import axios from "axios";
import { generatePolicyReport } from "../src/features/reports/generation.ts";

test("a report completing after route departure finishes the request without navigating away from the new page", async () => {
  let resolve!: () => void;
  const waiting = new Promise<void>(done => { resolve = done; });
  const requests: string[] = [];
  const events: unknown[] = [];
  let active = true;
  const task = generatePolicyReport({ post: async url => { requests.push(url); await waiting; } }, 42,
    () => active, id => events.push({ navigate: id }), error => events.push({ error }));

  active = false;
  resolve();
  await task;

  assert.deepEqual(requests, ["/api/v1/policies/42/report"]);
  assert.deepEqual(events, []);
});

test("a late failed report cannot show an alert on a different policy page", async () => {
  let reject!: (error: Error) => void;
  const waiting = new Promise<void>((_resolve, fail) => { reject = fail; });
  const events: unknown[] = [];
  let active = true;
  const task = generatePolicyReport({ post: () => waiting }, 42, () => active,
    id => events.push({ navigate: id }), error => events.push({ error }));
  active = false;
  reject(new Error("provider unavailable"));
  await task;

  assert.deepEqual(events, []);
});

test("active report completion navigates to the generated policy only", async () => {
  const events: unknown[] = [];
  await generatePolicyReport({ post: async () => ({}) }, 42, () => true,
    id => events.push({ navigate: id }), error => events.push({ error }));
  assert.deepEqual(events, [{ navigate: 42 }]);
});

test("active report failure preserves the server explanation needed for a retry", async () => {
  const api = axios.create({ adapter: async config => {
    throw new axios.AxiosError("conflict", "ERR_BAD_REQUEST", config, undefined, {
      data: { success: false, message: "사업 정보가 변경되었습니다. 다시 생성해주세요", error: { code: "REPORT_CONTEXT_CHANGED", status: 409 } },
      status: 409, statusText: "Conflict", headers: {}, config,
    });
  } });
  const events: unknown[] = [];
  await generatePolicyReport(api, 42, () => true,
    id => events.push({ navigate: id }), error => events.push({ error }));
  assert.deepEqual(events, [{ error: "사업 정보가 변경되었습니다. 다시 생성해주세요" }]);
});
