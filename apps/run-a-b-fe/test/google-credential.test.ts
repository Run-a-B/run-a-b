import test from 'node:test';
import assert from 'node:assert/strict';
import axios from 'axios';
import { loginWithGoogleCredential } from '../src/features/auth/google.ts';

test('Google login exchanges the ID credential received from Google', async () => {
  const api = axios.create();
  const requests: unknown[] = [];
  api.defaults.adapter = async config => {
    requests.push({ url: config.url, data: JSON.parse(config.data) });
    return { config, data: { data: { access_token: 'app-session', user: {} } }, status: 200, statusText: 'OK', headers: {} };
  };
  const result = await loginWithGoogleCredential(api, 'signed-google-id-credential');
  assert.deepEqual(requests, [{ url: '/api/v1/auth/google', data: { id_token: 'signed-google-id-credential' } }]);
  assert.equal(result.data.data.access_token, 'app-session');
});

test('missing Google ID credential fails without calling the login endpoint', async () => {
  const api = axios.create();
  let requests = 0;
  api.defaults.adapter = async () => { requests++; throw new Error('Unexpected request'); };
  await assert.rejects(loginWithGoogleCredential(api, undefined), /구글/);
  assert.equal(requests, 0);
});
