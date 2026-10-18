import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToReadableStream } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { createServer } from "vite";

test("report list offers additional review alongside legacy impact filters", async (t) => {
  const vite = await createServer({ root: process.cwd(), appType: "custom", logLevel: "silent",
    server: { middlewareMode: true }, optimizeDeps: { noDiscovery: true }, ssr: { optimizeDeps: { noDiscovery: true } } });
  t.after(() => vite.close());
  const { default: ReportList } = await vite.ssrLoadModule("/src/pages/ReportList.tsx");
  const stream = await renderToReadableStream(React.createElement(MemoryRouter, null, React.createElement(ReportList)));
  await stream.allReady;
  const html = await new Response(stream).text();

  assert.match(html, /<button[^>]*>추가 확인<\/button>/);
  assert.match(html, /<button[^>]*>긍정 영향<\/button>/);
  assert.match(html, /<button[^>]*>부정 영향<\/button>/);
});
