import test from 'node:test';
import assert from 'node:assert/strict';
import { buildDiagnosisPrefill, toDiagnosisRequest, validateDiagnosisForm, REGION_OPTIONS, INDUSTRY_OPTIONS, REVENUE_OPTIONS, EMPLOYEE_OPTIONS, type DiagnosisResponse } from '../src/features/diagnosis/model.ts';

test('preparing businesses omit operating-only values', () => {
  assert.deepEqual(toDiagnosisRequest({ businessStatus: false, industry: '카페/음료', region: '서울특별시', annualRevenueBand: '5,000만원~1억원', employeeCountBand: '1~4명' }), { businessStatus: false, industry: '카페/음료', region: '서울특별시', annualRevenueBand: null, employeeCountBand: null });
});
test('validation requires bands only while operating', () => {
  assert.deepEqual(validateDiagnosisForm({ businessStatus: true, industry: '카페/음료', region: '서울특별시', annualRevenueBand: '', employeeCountBand: '' }), { annualRevenueBand: '매출 구간을 선택해 주세요.', employeeCountBand: '직원 수를 선택해 주세요.' });
  assert.deepEqual(validateDiagnosisForm({ businessStatus: false, industry: '카페/음료', region: '서울특별시', annualRevenueBand: '', employeeCountBand: '' }), {});
});
test('prefill canonicalizes every region alias', () => {
  const aliases = ['서울','부산','대구','인천','광주','대전','울산','세종','경기','강원','강원도','충북','충남','전북','전라북도','전남','경북','경남','제주','제주도'];
  const expected = ['서울특별시','부산광역시','대구광역시','인천광역시','광주광역시','대전광역시','울산광역시','세종특별자치시','경기도','강원특별자치도','강원특별자치도','충청북도','충청남도','전북특별자치도','전북특별자치도','전라남도','경상북도','경상남도','제주특별자치도','제주특별자치도'];
  aliases.forEach((region, index) => assert.equal(buildDiagnosisPrefill({ businessStatus: true, region, jobCategory: '음식점', annualRevenue: '5억 이상', employeeCount: '1~4명' }).region, expected[index]));
});
test('prefill canonicalizes every industry alias and operating bands', () => {
  const aliases = ['음식점','음식점업 (한식·양식·중식 등)','카페/베이커리','카페/음료','의류/패션','뷰티/미용','교육','교육/학원','IT/소프트웨어','제조','제조업','도소매','도소매업','기타'];
  aliases.forEach(industry => assert.notEqual(buildDiagnosisPrefill({ businessStatus: true, region: '서울', jobCategory: industry, annualRevenue: '5억 이상', employeeCount: '1~4명' }).industry, ''));
  assert.equal(buildDiagnosisPrefill({ businessStatus: true, region: '서울', jobCategory: '음식점', annualRevenue: '5억 이상', employeeCount: '1~4명' }).annualRevenueBand, '5억원 이상');
});
test('preparing prefill clears operating values and unknowns', () => {
  assert.deepEqual(buildDiagnosisPrefill({ businessStatus: false, jobCategory: '카페/베이커리', region: '강원도', annualRevenue: '5억 이상', employeeCount: 'bogus' }), { businessStatus: false, industry: '카페/음료', region: '강원특별자치도', annualRevenueBand: '', employeeCountBand: '' });
});
test('exports canonical diagnosis options', () => { assert.ok(REGION_OPTIONS.includes('강원특별자치도') && REGION_OPTIONS.includes('전북특별자치도')); assert.ok(INDUSTRY_OPTIONS.includes('기타')); assert.deepEqual(REVENUE_OPTIONS, ['1,000만원 미만','1,000만원~5,000만원','5,000만원~1억원','1억원~3억원','3억원~5억원','5억원 이상']); assert.deepEqual(EMPLOYEE_OPTIONS, ['없음 (나 혼자)','1~4명','5~9명','10~29명','30명 이상']); });
test('validation reports missing industry independently', () => { assert.deepEqual(validateDiagnosisForm({ businessStatus: false, industry: '', region: '서울특별시', annualRevenueBand: '', employeeCountBand: '' }), { industry: '업종을 선택해 주세요.' }); });
test('validation reports missing region independently', () => { assert.deepEqual(validateDiagnosisForm({ businessStatus: false, industry: '카페/음료', region: '', annualRevenueBand: '', employeeCountBand: '' }), { region: '지역을 선택해 주세요.' }); });
test('DiagnosisResponse types every Task 2 response field', () => {
  const response: DiagnosisResponse = { policy: { id: 123, title: '정책명', category: '경영', agency: '기관명', applicationPeriod: '2026.09.01 ~ 2026.09.30', detailUrl: 'https://example.com' }, verdict: 'NEEDS_CONFIRMATION', verdictLabel: '조건 확인 필요', score: 68, confidence: 'LOW', summary: '요약', evidence: [{ key: 'region', label: '지역', userValue: '서울특별시', policyValue: '서울특별시', status: 'PASS', reason: '같은 지역', source: '정책 목록' }], unknownConditions: ['사업 업력'], hardFailReasons: [], hardFail: false, dataQuality: { policyCardAvailable: false, extractionStatus: 'not_available', sourceSystem: 'bizinfo', verifiedFactCount: 2, totalFactCount: 6 }, disclaimer: '고지' };
  assert.equal(response.hardFail, false);
});
