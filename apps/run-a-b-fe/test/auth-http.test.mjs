import test from 'node:test'
import assert from 'node:assert/strict'
import axios from 'axios'
import { createServer } from 'vite'

test('API credentials stay scoped to the request that owns them', async (t) => {
  const values = new Map([['access_token', 'stale-session'], ['user', '{}']])
  const storageDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
  const windowDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'window')
  const location = { pathname: '/signup', href: '/signup' }
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: key => values.get(key) ?? null,
      removeItem: key => values.delete(key),
    },
  })
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { location } })
  const vite = await createServer({
    root: process.cwd(), appType: 'custom', logLevel: 'silent',
    server: { middlewareMode: true }, optimizeDeps: { noDiscovery: true },
    ssr: { optimizeDeps: { noDiscovery: true } },
  })
  t.after(async () => {
    await vite.close()
    for (const [key, descriptor] of [['localStorage', storageDescriptor], ['window', windowDescriptor]]) {
      if (descriptor) Object.defineProperty(globalThis, key, descriptor)
      else delete globalThis[key]
    }
  })
  const { default: api } = await vite.ssrLoadModule('/src/lib/api.ts')
  const requestHeaders = async (url, headers) => {
    const response = await api.post(url, {}, {
      headers,
      adapter: async config => ({ config, data: {}, status: 200, statusText: 'OK', headers: {} }),
    })
    return response.config.headers
  }
  const unauthorized = async (url, headers) => {
    await assert.rejects(api.post(url, {}, {
      headers,
      adapter: async config => {
        throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, {
          config, data: { message: 'Invalid credentials' }, status: 401, statusText: 'Unauthorized', headers: {},
        })
      },
    }), error => error.response.status === 401)
  }
  t.beforeEach(() => {
    values.set('access_token', 'stale-session')
    values.set('user', '{}')
    location.href = '/signup'
  })

  await t.test('explicit bearer header is not overwritten by stored access credentials', async () => {
    assert.equal((await requestHeaders('/api/v1/auth/signup/business', { authorization: 'Bearer explicit-temp' })).get('Authorization'), 'Bearer explicit-temp')
  })
  await t.test('public auth routes do not send stale access credentials', async () => {
    for (const route of ['login', 'signup', 'signup/complete', 'email/send', 'email/verify', 'google']) {
      assert.equal((await requestHeaders(`/api/v1/auth/${route}`)).get('Authorization'), undefined)
    }
  })
  await t.test('protected routes receive stored access credentials', async () => {
    assert.equal((await requestHeaders('/api/v1/users/me')).get('Authorization'), 'Bearer stale-session')
  })
  await t.test('invalid login and verification responses keep the form and existing session', async () => {
    for (const route of ['login', 'email/verify']) await unauthorized(`/api/v1/auth/${route}`)
    assert.equal(values.get('access_token'), 'stale-session')
    assert.equal(location.href, '/signup')
  })
  await t.test('an explicit credential failure does not destroy a different access session', async () => {
    await unauthorized('/api/v1/auth/signup/business', { Authorization: 'Bearer explicit-temp' })
    assert.equal(values.get('access_token'), 'stale-session')
    assert.equal(location.href, '/signup')
  })
  await t.test('expired current access credentials clear both persisted identity and token', async () => {
    await unauthorized('/api/v1/users/me')
    assert.equal(values.has('access_token'), false)
    assert.equal(values.has('user'), false)
    assert.equal(location.href, '/login')
  })
})
