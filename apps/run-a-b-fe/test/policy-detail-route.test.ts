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
  assert.equal(policyDetailViewState("ready", loaded, 2), "loading");
  assert.equal(policyDetailViewState("missing", null, 2), "missing");
  assert.equal(policyDetailViewState("error", null, 2), "error");
  assert.equal(policyDetailViewState("ready", { ...loaded, policyId: 2 }, 2), "ready");
});
