import test from 'node:test';
import assert from 'node:assert/strict';
import axios from 'axios';
import { createSignupSubmitter, getSignupErrors, isSignupAccountValid, isValidPassword } from '../src/features/auth/signup.ts';
import * as signup from '../src/features/auth/signup.ts';

const account = { name: '테스트', email: 'signup@example.test', age: '35', password: 'valid!12', confirmPassword: 'valid!12' };
const business = { status: '사업 준비 중', industry: '카페/베이커리', region: '서울', revenue: '', employees: '' };

test('signup uses the server password alphabet and 6–20 character boundaries', () => {
  for (const [password, valid] of [['abc12', false], ['abcdef', true], ['!@$*12', true], ['a'.repeat(20), true], ['a'.repeat(21), false], ['abcdef#', false]] as const) {
    assert.equal(isValidPassword(password), valid);
    assert.equal(isSignupAccountValid({ ...account, password, confirmPassword: password }), valid);
  }
});

test('signup rejects fractional, out-of-range, and nonnumeric age before sending', () => {
  for (const age of ['1.5', '0', '121', 'text', ' ']) {
    assert.equal(isSignupAccountValid({ ...account, age }), false);
    assert.notEqual(getSignupErrors({ ...account, age }).age, '');
  }
  for (const age of ['', '1', '120']) assert.equal(isSignupAccountValid({ ...account, age }), true);
});

test('atomic signup sends one request with the caller-held verification receipt', async () => {
  const api = axios.create();
  const requests: { url: string | undefined; data: unknown }[] = [];
  api.defaults.adapter = async config => {
    requests.push({ url: config.url, data: JSON.parse(config.data) });
    return { config, data: { data: { access_token: 'new-session', user: { name: '테스트' } } }, status: 200, statusText: 'OK', headers: {} };
  };
  const submit = createSignupSubmitter(api);
  const result = await submit(account, business, 'verification-receipt');
  assert.equal(result.data.data.access_token, 'new-session');
  assert.deepEqual(requests, [{
    url: '/api/v1/auth/signup/complete',
    data: {
      account: { name: '테스트', email: 'signup@example.test', age: 35, password: 'valid!12', passwordConfirm: 'valid!12', verificationToken: 'verification-receipt' },
      business: { businessStatus: false, jobCategory: '카페/베이커리', region: '서울', annualRevenue: null, employeeCount: null },
    },
  }]);
});

test('duplicate submissions share one pending request and failures allow receipt-preserving retry', async () => {
  const api = axios.create();
  let requests = 0;
  let fail: ((reason: Error) => void) | undefined;
  const receipts: string[] = [];
  api.defaults.adapter = config => {
    requests++;
    receipts.push(JSON.parse(config.data).account.verificationToken);
    if (requests === 1) return new Promise((_resolve, reject) => { fail = reject; });
    return Promise.resolve({ config, data: { data: { access_token: 'new-session', user: {} } }, status: 200, statusText: 'OK', headers: {} });
  };
  const submit = createSignupSubmitter(api);
  const first = submit(account, business, 'same-receipt');
  const duplicate = submit(account, business, 'same-receipt');
  assert.strictEqual(duplicate, first);
  assert.equal(requests, 1);
  fail!(new Error('server rolled back'));
  await assert.rejects(first, /server rolled back/);
  await submit(account, business, 'same-receipt');
  assert.equal(requests, 2);
  assert.deepEqual(receipts, ['same-receipt', 'same-receipt']);
});

test('missing proof and invalid account/business never reach registration', async () => {
  const api = axios.create();
  let requests = 0;
  api.defaults.adapter = async () => { requests++; throw new Error('unexpected network'); };
  const submit = createSignupSubmitter(api);
  await assert.rejects(submit(account, business, ''), /이메일 인증/);
  await assert.rejects(submit({ ...account, age: '2.5' }, business, 'receipt'), /입력/);
  await assert.rejects(submit(account, { ...business, region: '' }, 'receipt'), /입력/);
  assert.equal(requests, 0);
});

test('only a rejected email receipt requires restarting verification; rollback failures retain it', async () => {
  const api = axios.create();
  const submit = createSignupSubmitter(api);
  for (const [code, restart] of [['EMAIL_NOT_VERIFIED', true], ['INTERNAL_ERROR', false], ['VERIFICATION_FAILED', false]]) {
    api.defaults.adapter = async config => {
      throw new axios.AxiosError('Request rejected', 'ERR_BAD_REQUEST', config, undefined, {
        config, data: { success: false, message: 'Request rejected', error: { code, status: 400 } },
        status: 400, statusText: 'Bad Request', headers: {},
      });
    };
    await assert.rejects(submit(account, business, 'receipt'), error => {
      assert.equal(signup.needsNewEmailVerification(error), restart);
      return true;
    });
  }
});
