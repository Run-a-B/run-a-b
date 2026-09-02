export const REGION_OPTIONS = [
  "서울특별시",
  "부산광역시",
  "대구광역시",
  "인천광역시",
  "광주광역시",
  "대전광역시",
  "울산광역시",
  "세종특별자치시",
  "경기도",
  "강원특별자치도",
  "충청북도",
  "충청남도",
  "전북특별자치도",
  "전라남도",
  "경상북도",
  "경상남도",
  "제주특별자치도",
] as const;
export const INDUSTRY_OPTIONS = [
  "음식점업",
  "카페/음료",
  "의류/패션",
  "뷰티/미용",
  "교육/학원",
  "IT/소프트웨어",
  "제조업",
  "도소매업",
  "기타",
] as const;
export const REVENUE_OPTIONS = [
  "1,000만원 미만",
  "1,000만원~5,000만원",
  "5,000만원~1억원",
  "1억원~3억원",
  "3억원~5억원",
  "5억원 이상",
] as const;
export const EMPLOYEE_OPTIONS = [
  "없음 (나 혼자)",
  "1~4명",
  "5~9명",
  "10~29명",
  "30명 이상",
] as const;
export interface DiagnosisForm {
  businessStatus: boolean;
  industry: string;
  region: string;
  annualRevenueBand: string;
  employeeCountBand: string;
}
export interface DiagnosisRequest {
  businessStatus: boolean;
  industry: string;
  region: string;
  annualRevenueBand: string | null;
  employeeCountBand: string | null;
}
export interface SavedBusiness {
  businessStatus: boolean;
  jobCategory?: string | null;
  region?: string | null;
  annualRevenue?: string | null;
  employeeCount?: string | null;
}
export type EvidenceStatus =
  "PASS" | "PARTIAL" | "UNKNOWN" | "FAIL" | "NOT_REQUIRED";
export type Verdict =
  "NOT_ELIGIBLE" | "ON_HOLD" | "NEEDS_CONFIRMATION" | "LIKELY_ELIGIBLE";
export type Confidence = "HIGH" | "MEDIUM" | "LOW";
export interface Evidence {
  key: string;
  label: string;
  userValue: string | null;
  policyValue: string | null;
  status: EvidenceStatus;
  reason: string | null;
  source: string | null;
}
export interface DataQuality {
  policyCardAvailable: boolean;
  extractionStatus: string | null;
  sourceSystem: string | null;
  verifiedFactCount: number;
  totalFactCount: number;
}
export interface DiagnosisResponse {
  policy: {
    id: number;
    title: string;
    category: string | null;
    agency: string | null;
    applicationPeriod: string | null;
    detailUrl: string | null;
  };
  verdict: Verdict;
  verdictLabel: string;
  score: number | null;
  confidence: Confidence;
  summary: string;
  evidence: Evidence[];
  unknownConditions: string[];
  hardFailReasons: string[];
  hardFail: boolean;
  dataQuality: DataQuality;
  disclaimer: string;
}
const regionAliases: Record<string, string> = {
  서울: "서울특별시",
  부산: "부산광역시",
  대구: "대구광역시",
  인천: "인천광역시",
  광주: "광주광역시",
  대전: "대전광역시",
  울산: "울산광역시",
  세종: "세종특별자치시",
  경기: "경기도",
  강원: "강원특별자치도",
  강원도: "강원특별자치도",
  충북: "충청북도",
  충남: "충청남도",
  전북: "전북특별자치도",
  전라북도: "전북특별자치도",
  전남: "전라남도",
  경북: "경상북도",
  경남: "경상남도",
  제주: "제주특별자치도",
  제주도: "제주특별자치도",
};
const industryAliases: Record<string, string> = {
  음식점: "음식점업",
  "음식점업 (한식·양식·중식 등)": "음식점업",
  "카페/베이커리": "카페/음료",
  교육: "교육/학원",
  제조: "제조업",
  도소매: "도소매업",
};
function selectValue(
  value: string | null | undefined,
  options: readonly string[],
  aliases: Record<string, string> = {},
): string {
  const candidate = value ? (aliases[value] ?? value) : "";
  return options.includes(candidate) ? candidate : "";
}
export function buildDiagnosisPrefill(saved: SavedBusiness): DiagnosisForm {
  const operating = Boolean(saved.businessStatus);
  return {
    businessStatus: operating,
    industry: selectValue(saved.jobCategory, INDUSTRY_OPTIONS, industryAliases),
    region: selectValue(saved.region, REGION_OPTIONS, regionAliases),
    annualRevenueBand: operating
      ? selectValue(saved.annualRevenue, REVENUE_OPTIONS, {
          "5억 이상": "5억원 이상",
        })
      : "",
    employeeCountBand: operating
      ? selectValue(saved.employeeCount, EMPLOYEE_OPTIONS)
      : "",
  };
}
export function validateDiagnosisForm(
  form: DiagnosisForm,
): Partial<Record<keyof DiagnosisForm, string>> {
  const errors: Partial<Record<keyof DiagnosisForm, string>> = {};
  if (!form.industry) errors.industry = "업종을 선택해 주세요.";
  if (!form.region) errors.region = "지역을 선택해 주세요.";
  if (form.businessStatus && !form.annualRevenueBand)
    errors.annualRevenueBand = "매출 구간을 선택해 주세요.";
  if (form.businessStatus && !form.employeeCountBand)
    errors.employeeCountBand = "직원 수를 선택해 주세요.";
  return errors;
}
export function toDiagnosisRequest(form: DiagnosisForm): DiagnosisRequest {
  return {
    businessStatus: form.businessStatus,
    industry: form.industry,
    region: form.region,
    annualRevenueBand: form.businessStatus ? form.annualRevenueBand : null,
    employeeCountBand: form.businessStatus ? form.employeeCountBand : null,
  };
}
