import test from 'node:test';
import assert from 'node:assert/strict';
import { buildDiagnosisPrefill, toDiagnosisRequest, validateDiagnosisForm, REGION_OPTIONS, INDUSTRY_OPTIONS, REVENUE_OPTIONS, EMPLOYEE_OPTIONS } from '../src/features/diagnosis/model.ts';

test('preparing businesses omit operating-only values', () => {
  assert.deepEqual(toDiagnosisRequest({ businessStatus: false, industry: '카페/음료', region: '서울특별시', annualRevenueBand: '5,000만원~1억원', employeeCountBand: '1~4명' }), { businessStatus: false, industry: '카페/음료', region: '서울특별시', annualRevenueBand: null, employeeCountBand: null });
});
test('validation requires bands only while operating', () => {
  assert.deepEqual(validateDiagnosisForm({ businessStatus: true, industry: '카페/음료', region: '서울특별시', annualRevenueBand: '', employeeCountBand: '' }), { annualRevenueBand: '매출 구간을 선택해 주세요.', employeeCountBand: '직원 수를 선택해 주세요.' });
  assert.deepEqual(validateDiagnosisForm({ businessStatus: false, industry: '카페/음료', region: '서울특별시', annualRevenueBand: '', employeeCountBand: '' }), {});
});
test('prefill canonicalizes saved business aliases and clears unknowns', () => {
  assert.deepEqual(buildDiagnosisPrefill({ businessStatus: false, jobCategory: '카페/베이커리', region: '강원도', annualRevenue: '5억 이상', employeeCount: 'bogus' }), { businessStatus: false, industry: '카페/음료', region: '강원특별자치도', annualRevenueBand: '', employeeCountBand: '' });
});
test('exports canonical diagnosis options', () => { assert.ok(REGION_OPTIONS.includes('강원특별자치도') && REGION_OPTIONS.includes('전북특별자치도')); assert.ok(INDUSTRY_OPTIONS.includes('기타')); assert.deepEqual(REVENUE_OPTIONS, ['1,000만원 미만','1,000만원~5,000만원','5,000만원~1억원','1억원~3억원','3억원~5억원','5억원 이상']); assert.deepEqual(EMPLOYEE_OPTIONS, ['없음 (나 혼자)','1~4명','5~9명','10~29명','30명 이상']); });
