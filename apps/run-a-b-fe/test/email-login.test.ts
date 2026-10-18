import assert from "node:assert/strict";
import test from "node:test";
import axios from "axios";
import { createEmailLoginSubmitter } from "../src/features/auth/login.ts";

function storageWithSavedEmail(email?: string) {
  const values = new Map(email ? [["saved_login_email", email]] : []);
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value); },
    removeItem: (key: string) => { values.delete(key); },
    values,
  };
}

test("email login remembers only the opted-in email after successful authentication", async () => {
  const requests: unknown[] = [];
  const api = axios.create({ adapter: async config => {
    requests.push({ url: config.url, body: JSON.parse(config.data) });
    return { data: { data: { access_token: "test-token" } }, status: 200, statusText: "OK", headers: {}, config };
  } });
  const storage = storageWithSavedEmail();
  const submit = createEmailLoginSubmitter(api, storage);

  await submit("  owner@example.com  ", "secret123!", true);

  assert.deepEqual(requests, [{ url: "/api/v1/auth/login", body: { email: "owner@example.com", password: "secret123!" } }]);
  assert.deepEqual([...storage.values], [["saved_login_email", "owner@example.com"]]);
});

test("opting out removes a previously remembered email after successful login", async () => {
  const api = axios.create({ adapter: async config => ({ data: {}, status: 200, statusText: "OK", headers: {}, config }) });
  const storage = storageWithSavedEmail("old@example.com");

  await createEmailLoginSubmitter(api, storage)("owner@example.com", "secret123!", false);

  assert.equal(storage.getItem("saved_login_email"), null);
});

test("failed login preserves remembered email and allows a corrected retry", async () => {
  let attempts = 0;
  const api = axios.create({ adapter: async config => {
    attempts += 1;
    if (attempts === 1) throw new Error("invalid credentials");
    return { data: {}, status: 200, statusText: "OK", headers: {}, config };
  } });
  const storage = storageWithSavedEmail("old@example.com");
  const submit = createEmailLoginSubmitter(api, storage);

  await assert.rejects(submit("new@example.com", "wrong-password", true), /invalid credentials/);
  assert.equal(storage.getItem("saved_login_email"), "old@example.com");
  await submit("new@example.com", "secret123!", true);
  assert.equal(storage.getItem("saved_login_email"), "new@example.com");
});

test("concurrent login submits share the same request and saved-email choice", async () => {
  let release!: () => void;
  const waiting = new Promise<void>(resolve => { release = resolve; });
  const requests: unknown[] = [];
  const api = axios.create({ adapter: async config => {
    requests.push(JSON.parse(config.data));
    await waiting;
    return { data: {}, status: 200, statusText: "OK", headers: {}, config };
  } });
  const storage = storageWithSavedEmail();
  const submit = createEmailLoginSubmitter(api, storage);
  const first = submit("first@example.com", "secret123!", true);
  const second = submit("second@example.com", "secret123!", false);
  release();
  await Promise.all([first, second]);

  assert.deepEqual(requests, [{ email: "first@example.com", password: "secret123!" }]);
  assert.equal(storage.getItem("saved_login_email"), "first@example.com");
});
