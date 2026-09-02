import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToReadableStream } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { createServer } from "vite";

function createLocalStorageStub() {
  const values = new Map();
  return {
    get length() { return values.size; },
    clear() { values.clear(); },
    getItem(key) { return values.get(String(key)) ?? null; },
    key(index) { return [...values.keys()][index] ?? null; },
    removeItem(key) { values.delete(String(key)); },
    setItem(key, value) { values.set(String(key), String(value)); },
  };
}

test("renders the diagnosis loading shell at the diagnosis route", async (t) => {
  const previousStorage = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
  const vite = await createServer({
    root: process.cwd(),
    appType: "custom",
    logLevel: "silent",
    server: { middlewareMode: true },
    optimizeDeps: { noDiscovery: true },
    ssr: { optimizeDeps: { noDiscovery: true } },
  });

  t.after(async () => {
    await vite.close();
    if (previousStorage) Object.defineProperty(globalThis, "localStorage", previousStorage);
    else delete globalThis.localStorage;
  });

  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    writable: true,
    value: createLocalStorageStub(),
  });

  const [{ AppRoutes }, { AuthProvider }] = await Promise.all([
    vite.ssrLoadModule("/src/routes/AppRoutes.tsx"),
    vite.ssrLoadModule("/src/contexts/AuthContext.tsx"),
  ]);
  const tree = React.createElement(
    MemoryRouter,
    { initialEntries: ["/policies/42/diagnosis"] },
    React.createElement(AuthProvider, null, React.createElement(AppRoutes)),
  );
  const stream = await renderToReadableStream(tree);
  await stream.allReady;
  const html = await new Response(stream).text();

  assert.match(html, /60초 정책 진단/);
  assert.match(html, /정책 정보를 불러오고 있어요/);
});
