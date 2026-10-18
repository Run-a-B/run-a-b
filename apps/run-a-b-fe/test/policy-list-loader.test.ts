import assert from "node:assert/strict";
import test from "node:test";
import { loadPolicyList, type PolicyListState } from "../src/features/policy/listLoader.ts";

const emptyPage = { policies: [], totalPages: 0, totalElements: 0 };
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

test("a late result from an inactive filter cannot overwrite the current result or counts", async () => {
  const old = deferred<{ data: { data: typeof emptyPage } }>();
  let oldActive = true;
  const states: PolicyListState[] = [];
  const pending = loadPolicyList({ get: () => old.promise }, "region=서울", () => oldActive, s => states.push(s));
  oldActive = false;
  await loadPolicyList({ get: async () => ({ data: { data: { ...emptyPage, totalElements: 24, totalPages: 2 } } }) },
    "region=부산", () => true, s => states.push(s));
  old.resolve({ data: { data: { ...emptyPage, totalElements: 999, totalPages: 84 } } });
  await pending;

  assert.deepEqual(states.at(-1), { tag: "success", data: { policies: [], totalPages: 2, totalElements: 24 } });
  assert.equal(states.filter(s => s.tag === "success").length, 1);
});

test("a failed current request reports an error rather than a successful empty page and can retry", async () => {
  const states: PolicyListState[] = [];
  await loadPolicyList({ get: async () => { throw new Error("offline"); } }, "", () => true, s => states.push(s));
  assert.deepEqual(states.at(-1), { tag: "error" });

  await loadPolicyList({ get: async () => ({ data: { data: emptyPage } }) }, "", () => true, s => states.push(s));
  assert.deepEqual(states.slice(-2), [{ tag: "loading" }, { tag: "success", data: emptyPage }]);
});

test("a late failed obsolete request cannot replace a newer success with an error", async () => {
  const old = deferred<{ data: { data: typeof emptyPage } }>();
  let oldActive = true;
  const states: PolicyListState[] = [];
  const pending = loadPolicyList({ get: () => old.promise }, "old", () => oldActive, s => states.push(s));
  oldActive = false;
  const current = { ...emptyPage, totalElements: 24, totalPages: 2 };
  await loadPolicyList({ get: async () => ({ data: { data: current } }) }, "new", () => true, s => states.push(s));
  old.reject(new Error("late offline"));
  await pending;
  assert.deepEqual(states.at(-1), { tag: "success", data: current });
});
