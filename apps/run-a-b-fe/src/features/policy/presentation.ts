export type EvidenceStatus = 'PASS' | 'PARTIAL' | 'UNKNOWN' | 'FAIL' | 'NOT_REQUIRED';
export type Verdict = 'NOT_ELIGIBLE' | 'ON_HOLD' | 'NEEDS_CONFIRMATION' | 'LIKELY_ELIGIBLE';
export interface PolicyDetailData { id:number; title:string; description:string|null; date:string|null; announcementNo:string|null; department:string|null; applicationPeriod:string|null; supportScale:string|null; targetGroup:string|null; purposeText:string|null; applicationMethod:string|null; applicationUrl:string|null; detailUrl:string|null; category:string|null; region:string|null; industry:string|null; agency:string|null; sourceSystem:string|null; tags?:string[]; targetConditions?:string[]; supportItems?:{category:string;amount:string;method:string}[]; exclusions?:string[]; aiSummaryText?:string|null; aiHighlights?:{icon:string;label:string;content:string}[]; businessImpact?:unknown[]; reportData?:unknown; applicationChecklist?:unknown[] }
export const statusPresentation: Record<EvidenceStatus|Verdict,{label:string;className:string}> = {
 PASS:{label:'일치',className:'bg-green-100 text-green-700 border-green-200'}, PARTIAL:{label:'부분 일치',className:'bg-yellow-100 text-yellow-700 border-yellow-200'}, UNKNOWN:{label:'확인 필요',className:'bg-gray-100 text-gray-600 border-gray-200'}, FAIL:{label:'불일치',className:'bg-red-100 text-red-700 border-red-200'}, NOT_REQUIRED:{label:'조건 없음',className:'bg-gray-100 text-gray-600 border-gray-200'}, NOT_ELIGIBLE:{label:'현재 대상 아님',className:'bg-red-100 text-red-700 border-red-200'}, ON_HOLD:{label:'판정 보류',className:'bg-gray-100 text-gray-600 border-gray-200'}, NEEDS_CONFIRMATION:{label:'조건 확인 필요',className:'bg-yellow-100 text-yellow-700 border-yellow-200'}, LIKELY_ELIGIBLE:{label:'신청 가능성 높음',className:'bg-green-100 text-green-700 border-green-200'}
};
export interface BriefItem { label:string; value:string; available:boolean }
export function buildPolicyBrief(policy:PolicyDetailData):BriefItem[] { return [{label:'지원 대상',value:policy.targetGroup||'원문에서 지원 대상을 확인해 주세요.',available:Boolean(policy.targetGroup)},{label:'지원 내용',value:policy.supportScale||'지원 금액·방식 정보가 아직 수집되지 않았어요.',available:Boolean(policy.supportScale)},{label:'신청 기간',value:policy.applicationPeriod||'기간 정보 없음 — 원문 공고 확인',available:Boolean(policy.applicationPeriod)}]; }
export function buildPolicyQuality(detail:PolicyDetailData) {
  const facts = [detail.targetGroup, detail.supportScale, detail.applicationPeriod, detail.purposeText || detail.description, detail.applicationMethod, detail.announcementNo, detail.department];
  const labels = ['지원 대상', '지원 내용', '신청 기간', '사업 내용', '신청 방법', '공고 번호', '담당 부서'];
  const missingLabels = labels.filter((_, index) => !facts[index]?.trim());
  const availableCount = labels.length - missingLabels.length;
  const level = availableCount === 7 ? 'rich' : availableCount >= 3 ? 'partial' : 'basic';
  return { availableCount, totalCount: 7, missingLabels, level };
}
