import {
  INDUSTRY_OPTIONS, REGION_OPTIONS, REVENUE_OPTIONS,
  normalizeBusinessIndustry, normalizeBusinessRegion,
} from "../business/options.ts";

export { INDUSTRY_OPTIONS, REGION_OPTIONS, REVENUE_OPTIONS } from "../business/options.ts";

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
    industry: selectValue(normalizeBusinessIndustry(saved.jobCategory), INDUSTRY_OPTIONS),
    region: selectValue(normalizeBusinessRegion(saved.region), REGION_OPTIONS),
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
