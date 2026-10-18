import test from "node:test";
import assert from "node:assert/strict";

import {
  isPolicyDetailForRoute,
  policyDetailViewState,
} from "../src/features/policy/detailRoute.ts";

const loaded = {
  policyId: 1,
  detail: { id: 1, title: "첫 번째 정책" },
};

test("a late detail response never belongs to a different policy route", () => {
  assert.equal(isPolicyDetailForRoute(loaded, 1), true);
  assert.equal(isPolicyDetailForRoute(loaded, 2), false);
  assert.equal(isPolicyDetailForRoute(null, 2), false);
});

test("a mismatched detail stays loading until the replacement request settles", () => {
  assert.equal(policyDetailViewState({ tag: "ready", policyId: 1 }, loaded, 2), "loading");
  assert.equal(policyDetailViewState({ tag: "missing", policyId: 2 }, null, 2), "missing");
  assert.equal(policyDetailViewState({ tag: "error", policyId: 2 }, null, 2), "error");
  assert.equal(policyDetailViewState({ tag: "ready", policyId: 2 }, { ...loaded, policyId: 2 }, 2), "ready");
});

test("terminal detail states never leak into a different policy route", () => {
  assert.equal(policyDetailViewState({ tag: "missing", policyId: 1 }, null, 2), "loading");
  assert.equal(policyDetailViewState({ tag: "error", policyId: 1 }, null, 2), "loading");
});
