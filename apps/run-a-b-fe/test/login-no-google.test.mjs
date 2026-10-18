import test from 'node:test'
import assert from 'node:assert/strict'
import React from 'react'
import { renderToReadableStream } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { createServer } from 'vite'

function createLocalStorageStub() {
  const values = new Map()

  return {
    get length() {
      return values.size
    },
    clear() {
      values.clear()
    },
    getItem(key) {
      return values.get(String(key)) ?? null
    },
    key(index) {
      return [...values.keys()][index] ?? null
    },
    removeItem(key) {
      values.delete(String(key))
    },
    setItem(key, value) {
      values.set(String(key), String(value))
    },
  }
}

test('renders /login when VITE_GOOGLE_CLIENT_ID is blank', async (t) => {
  const previousClientId = process.env.VITE_GOOGLE_CLIENT_ID
  const previousStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
  let vite

  t.after(async () => {
    await vite?.close()

    if (previousClientId === undefined) {
      delete process.env.VITE_GOOGLE_CLIENT_ID
    } else {
      process.env.VITE_GOOGLE_CLIENT_ID = previousClientId
    }

    if (previousStorage) {
      Object.defineProperty(globalThis, 'localStorage', previousStorage)
    } else {
      delete globalThis.localStorage
    }
  })

  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    writable: true,
    value: createLocalStorageStub(),
  })

  for (const clientId of ['', '   ']) {
    process.env.VITE_GOOGLE_CLIENT_ID = clientId
    vite = await createServer({
      root: process.cwd(),
      appType: 'custom',
      logLevel: 'silent',
      server: { middlewareMode: true },
      optimizeDeps: { noDiscovery: true },
      ssr: { optimizeDeps: { noDiscovery: true } },
    })

    const [{ AppRoutes }, { AuthProvider }] = await Promise.all([
      vite.ssrLoadModule('/src/routes/AppRoutes.tsx'),
      vite.ssrLoadModule('/src/contexts/AuthContext.tsx'),
    ])

    const tree = React.createElement(
      MemoryRouter,
      { initialEntries: ['/login'] },
      React.createElement(AuthProvider, null, React.createElement(AppRoutes)),
    )

    let renderError
    const stream = await renderToReadableStream(tree, {
      onError(error) {
        renderError ??= error
      },
    })

    await stream.allReady
    const html = await new Response(stream).text()

    assert.ifError(renderError)
    assert.match(html, /id="email"/)
    assert.match(html, />로그인</)
    assert.doesNotMatch(html, /Google로 계속하기/)

    await vite.close()
    vite = undefined
  }
})
